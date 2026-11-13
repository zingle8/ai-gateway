package com.zimo.gateway.user.service;

import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录因子服务（v0.2 第 4 项）：图片验证码 / 弹框动态码 / 短信·邮件动态码 / 扫码登录。
 *
 * <p>状态全部落 Redis（key 前缀沿用 {@code gw:}）：
 * <ul>
 *   <li>{@code gw:login:captcha:{ticket}} → {@code IMAGE|code} / {@code DYNAMIC|code}</li>
 *   <li>{@code gw:login:otp:{ticket}} → {@code SMS|code|userId}（短信 / 邮件共用，一次性）</li>
 *   <li>{@code gw:login:qr:{ticket}:mode} / {@code :code} / {@code :user}（扫码三态）</li>
 * </ul>
 *
 * <p><b>通道边界</b>：短信、邮件均为「模拟通道」——验证码生成后写入服务日志（{@code [SMS-MOCK]} /
 * {@code [MAIL-MOCK]}），不发真实短信、不连 SMTP；接入真实通道只需替换 {@link #deliver} 实现，
 * 校验与界面逻辑不用改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginFactorService {

    private static final String CAPTCHA_PREFIX = "gw:login:captcha:";
    private static final String OTP_PREFIX = "gw:login:otp:";
    private static final String QR_PREFIX = "gw:login:qr:";

    /** 图片验证码字符集：去掉 0/O/1/I/L 等易混淆字符 */
    private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final Random RANDOM = new Random();

    private static final int CAPTCHA_IMAGE_TTL = 120;
    private static final int CAPTCHA_DYNAMIC_TTL = 30;
    private static final int OTP_TTL = 300;
    private static final int QR_TTL = 180;
    private static final int QR_CODE_TTL = 30;

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;
    private final LoginPolicyService policy;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ 验证码

    /** 图片验证码：返回 base64 PNG，120 秒有效，一次性 */
    public Map<String, Object> createImageCaptcha() {
        String code = random(CHARS, 4);
        String ticket = ticket();
        redis.opsForValue().set(CAPTCHA_PREFIX + ticket, "IMAGE|" + code,
                CAPTCHA_IMAGE_TTL, TimeUnit.SECONDS);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", ticket);
        data.put("mode", "image");
        data.put("image", "data:image/png;base64," + render(code));
        data.put("expiresIn", CAPTCHA_IMAGE_TTL);
        return data;
    }

    /** 弹框动态码：6 位数字，30 秒有效（前端到点自动换），一次性 */
    public Map<String, Object> createDynamicCode() {
        String code = randomDigits(6);
        String ticket = ticket();
        redis.opsForValue().set(CAPTCHA_PREFIX + ticket, "DYNAMIC|" + code,
                CAPTCHA_DYNAMIC_TTL, TimeUnit.SECONDS);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", ticket);
        data.put("mode", "dynamic");
        data.put("code", code);
        data.put("expiresIn", CAPTCHA_DYNAMIC_TTL);
        return data;
    }

    /** 校验密码登录验证码（不区分形态），校验通过即失效 */
    public void verifyCaptcha(String ticket, String code) {
        if (ticket == null || ticket.isBlank() || code == null || code.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请输入验证码");
        }
        String key = CAPTCHA_PREFIX + ticket.trim();
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "验证码已过期，请刷新后重试");
        }
        String expected = stored.contains("|") ? stored.substring(stored.indexOf('|') + 1) : stored;
        if (!expected.equalsIgnoreCase(code.trim())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "验证码不正确");
        }
        redis.delete(key);
    }

    // ------------------------------------------------------------------ 短信 / 邮件动态码

    /**
     * 发送动态码（模拟通道）：验证码写入服务日志，返回 ticket 供登录时配合校验。
     *
     * @param channel SMS / EMAIL
     */
    public Map<String, Object> sendOtp(String username, String channel) {
        String ch = "EMAIL".equalsIgnoreCase(channel) ? "EMAIL" : "SMS";
        String factorKey = "EMAIL".equals(ch) ? "emailOtp" : "smsOtp";
        if (!policy.factorEnabled(factorKey)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    ("EMAIL".equals(ch) ? "邮件验证码" : "短信验证码") + "登录方式已关闭");
        }
        UserEntity user = username == null || username.isBlank() ? null : userMapper.selectByUsername(username.trim());
        if (user == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "账号不存在");
        }
        String target = "EMAIL".equals(ch) ? user.getEmail() : user.getPhone();
        if (target == null || target.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "该账号未绑定" + ("EMAIL".equals(ch) ? "邮箱" : "手机号") + "，请联系管理员");
        }
        String code = randomDigits(6);
        String ticket = ticket();
        redis.opsForValue().set(OTP_PREFIX + ticket, ch + "|" + code + "|" + user.getId(),
                OTP_TTL, TimeUnit.SECONDS);
        deliver(ch, target, code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", ticket);
        data.put("channel", ch);
        data.put("target", mask(target));
        data.put("expiresIn", OTP_TTL);
        // 模拟通道：明确告知前端「验证码在服务日志里」
        data.put("mock", true);
        return data;
    }

    /** 校验短信 / 邮件动态码，返回 ticket 绑定的用户 */
    public UserEntity verifyOtp(String ticket, String code) {
        if (ticket == null || ticket.isBlank() || code == null || code.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请输入验证码");
        }
        String key = OTP_PREFIX + ticket.trim();
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "验证码已过期，请重新获取");
        }
        String[] parts = stored.split("\\|", -1);
        if (parts.length < 3 || !parts[1].equals(code.trim())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "验证码不正确");
        }
        redis.delete(key);
        UserEntity user = userMapper.selectById(Long.valueOf(parts[2]));
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户不存在");
        }
        return user;
    }

    /**
     * 模拟发送：验证码落日志。
     * 接真实通道时只替换本方法（短信网关 / SMTP），其余逻辑不变。
     */
    private void deliver(String channel, String target, String code) {
        if ("EMAIL".equals(channel)) {
            log.warn("[MAIL-MOCK] 邮件验证码已生成（未接真实 SMTP）：{} → {}，5 分钟内有效",
                    mask(target), code);
        } else {
            log.warn("[SMS-MOCK] 短信验证码已生成（未接真实短信网关）：{} → {}，5 分钟内有效",
                    mask(target), code);
        }
    }

    // ------------------------------------------------------------------ 扫码登录

    /**
     * 创建扫码会话。
     *
     * @param mode confirm：演示确认流（二维码内容为确认链接，手机端登录确认）
     *             code：扫码取码流（二维码内容就是 6 位动态码，30 秒自动更换）
     */
    public Map<String, Object> createQr(String mode, String baseUrl) {
        if (!policy.factorEnabled("qrcode")) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "扫码登录方式已关闭");
        }
        String finalMode = "code".equalsIgnoreCase(mode) ? "code" : "confirm";
        String ticket = ticket();
        redis.opsForValue().set(QR_PREFIX + ticket + ":mode", finalMode, QR_TTL, TimeUnit.SECONDS);
        redis.opsForValue().set(QR_PREFIX + ticket + ":status", "WAITING", QR_TTL, TimeUnit.SECONDS);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", ticket);
        data.put("mode", finalMode);
        data.put("expiresIn", QR_TTL);
        if ("code".equals(finalMode)) {
            data.put("code", rollQrCode(ticket));
            data.put("codeExpiresIn", QR_CODE_TTL);
        } else {
            // 二维码内容：控制台登录页 + ?qr=<ticket>，手机端打开即为确认页
            String host = baseUrl == null || baseUrl.isBlank() ? "http://localhost:28080" : baseUrl;
            data.put("content", host.replaceAll("/+$", "") + "/login?qr=" + ticket);
        }
        return data;
    }

    /** 扫码取码流：换一个动态码（30 秒有效） */
    public Map<String, Object> refreshQrCode(String ticket) {
        assertQr(ticket);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", rollQrCode(ticket));
        data.put("expiresIn", QR_CODE_TTL);
        return data;
    }

    /** 轮询扫码状态：WAITING / CONFIRMED（含一次性登录票据）/ EXPIRED */
    public Map<String, Object> qrStatus(String ticket) {
        Map<String, Object> data = new LinkedHashMap<>();
        String mode = redis.opsForValue().get(QR_PREFIX + ticket + ":mode");
        if (mode == null) {
            data.put("status", "EXPIRED");
            return data;
        }
        String status = redis.opsForValue().get(QR_PREFIX + ticket + ":status");
        data.put("status", status == null ? "EXPIRED" : status);
        data.put("mode", mode);
        return data;
    }

    /** 手机端确认（演示确认流）：校验账号密码后把 userId 绑定到 ticket */
    public Map<String, Object> confirmQr(String ticket, String username, String password) {
        assertQr(ticket);
        String mode = redis.opsForValue().get(QR_PREFIX + ticket + ":mode");
        if (!"confirm".equals(mode)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "当前扫码会话不是确认流，无需确认");
        }
        String status = redis.opsForValue().get(QR_PREFIX + ticket + ":status");
        if ("CONFIRMED".equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该二维码已被确认，请刷新后重试");
        }
        UserEntity user = username == null || username.isBlank() ? null : userMapper.selectByUsername(username.trim());
        if (user == null || !passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "账号已禁用，请联系管理员");
        }
        redis.opsForValue().set(QR_PREFIX + ticket + ":user", String.valueOf(user.getId()),
                QR_TTL, TimeUnit.SECONDS);
        redis.opsForValue().set(QR_PREFIX + ticket + ":status", "CONFIRMED",
                QR_TTL, TimeUnit.SECONDS);
        log.info("[扫码登录] 已确认：ticket={}，账号={}", ticket, username);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("username", user.getUsername());
        return data;
    }

    /** 演示确认流：取确认用户并一次性消费该扫码会话 */
    public UserEntity consumeQrConfirmation(String ticket) {
        assertQr(ticket);
        String status = redis.opsForValue().get(QR_PREFIX + ticket + ":status");
        String userId = redis.opsForValue().get(QR_PREFIX + ticket + ":user");
        if (!"CONFIRMED".equals(status) || userId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "二维码尚未确认，请先扫码确认");
        }
        clearQr(ticket);
        UserEntity user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户不存在");
        }
        return user;
    }

    /** 扫码取码流：校验手动输入的动态码（30 秒有效，用后不销毁，便于误输重试） */
    public void verifyQrCode(String ticket, String code) {
        assertQr(ticket);
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请输入二维码动态码");
        }
        String expected = redis.opsForValue().get(QR_PREFIX + ticket + ":code");
        if (expected == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "动态码已失效，请刷新二维码");
        }
        if (!expected.equals(code.trim())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "动态码不正确");
        }
    }

    private void assertQr(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "缺少扫码会话标识");
        }
        if (Boolean.FALSE.equals(redis.hasKey(QR_PREFIX + ticket + ":mode"))) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "二维码已过期，请刷新");
        }
    }

    private String rollQrCode(String ticket) {
        String code = randomDigits(6);
        redis.opsForValue().set(QR_PREFIX + ticket + ":code", code, QR_CODE_TTL, TimeUnit.SECONDS);
        return code;
    }

    private void clearQr(String ticket) {
        redis.delete(QP(ticket));
    }

    private java.util.List<String> QP(String ticket) {
        return java.util.List.of(
                QR_PREFIX + ticket + ":mode",
                QR_PREFIX + ticket + ":status",
                QR_PREFIX + ticket + ":code",
                QR_PREFIX + ticket + ":user");
    }

    /** 按账号取用户（扫码取码流登录时用，因子已校验） */
    public UserEntity findByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请输入账号");
        }
        return userMapper.selectByUsername(username.trim());
    }

    /**
     * 登录页可匿名读取的「登录方式」公开视图（登录前渲染因子入口用）。
     * 只暴露因子开关与验证码 / 扫码形态，不暴露 SMTP / 短信通道凭据。
     */
    public Map<String, Object> publicLoginConfig() {
        Map<String, Object> methods = policy.methods();
        List<Map<String, Object>> factors = new ArrayList<>();
        if (methods.get("factors") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> f) {
                    Object key = f.get("key");
                    if (key != null) {
                        factors.add(Map.of("key", key, "enabled", !Boolean.FALSE.equals(f.get("enabled"))));
                    }
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("factors", factors);
        data.put("captcha", Map.of(
                "enabled", policy.captchaEnabled(),
                "mode", policy.captchaMode()));
        data.put("qrcode", Map.of("mode", policy.qrMode()));
        return data;
    }

    // ------------------------------------------------------------------ 工具

    private static String ticket() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String random(String chars, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** 目标脱敏：138****8000 / a***@qq.com */
    private static String mask(String target) {
        if (target == null || target.length() <= 4) {
            return target;
        }
        if (target.contains("@")) {
            int at = target.indexOf('@');
            String name = target.substring(0, at);
            return (name.length() <= 1 ? name : name.charAt(0) + "***") + target.substring(at);
        }
        return target.substring(0, 3) + "****" + target.substring(target.length() - 2);
    }

    /** 生成图形验证码 PNG（自绘，不引第三方库） */
    private static String render(String code) {
        int width = 132;
        int height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(246, 248, 252));
        g.fillRect(0, 0, width, height);
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(randomColor(160, 220));
            g.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height),
                    RANDOM.nextInt(width), RANDOM.nextInt(height));
        }
        // 字符（随机角度与颜色）
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        int step = width / (code.length() + 1);
        for (int i = 0; i < code.length(); i++) {
            AffineTransform old = g.getTransform();
            double angle = (RANDOM.nextDouble() - 0.5) * 0.45;
            g.rotate(angle, step * (i + 1), height / 2.0);
            g.setColor(randomColor(20, 120));
            g.drawString(String.valueOf(code.charAt(i)), step * (i + 1) - 9, height / 2 + 10);
            g.setTransform(old);
        }
        // 噪点
        for (int i = 0; i < 90; i++) {
            g.setColor(randomColor(150, 220));
            g.fillRect(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new BizException(ResultCode.ERROR.getCode(), "验证码生成失败");
        }
    }

    private static Color randomColor(int min, int max) {
        int range = Math.max(1, max - min);
        return new Color(min + RANDOM.nextInt(range), min + RANDOM.nextInt(range), min + RANDOM.nextInt(range));
    }
}
