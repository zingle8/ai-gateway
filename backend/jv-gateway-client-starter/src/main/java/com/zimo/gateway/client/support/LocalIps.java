package com.zimo.gateway.client.support;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * 本机 IP 探测。
 *
 * <p><b>不要用 {@link InetAddress#getLocalHost()}</b>：它会按主机名做正向/反向 DNS 解析，
 * 在 hostname 未写入 /etc/hosts 的机器上会阻塞到 DNS 超时（实测 5 秒）。
 * 放在请求链路上就意味着「应用的首个请求慢 5 秒」，放在启动链路上就是启动慢 5 秒。</p>
 *
 * <p>这里直接枚举网卡取 IPv4 地址，不做任何名称解析，结果静态缓存。
 * 装了 Docker / VPN / 虚拟机的机器上会自动跳过这些虚拟网卡；若仍然选错，
 * 用 {@code zimo.gateway.client.ip} 显式指定即可。</p>
 */
public final class LocalIps {

    /** 虚拟网卡命名：utun/tun/tap/bridge/ap/awdl/vmnet/veth/docker/br-/vboxnet 等 */
    private static final Pattern VIRTUAL = Pattern.compile(
            "^(utun|tun|tap|bridge|ap\\d|llw|awdl|vmnet|veth|docker|br-|gif|stf|p2p|vboxnet|virbr).*",
            Pattern.CASE_INSENSITIVE);

    private static volatile String cached;

    private LocalIps() {
    }

    public static String get() {
        String ip = cached;
        if (ip == null) {
            synchronized (LocalIps.class) {
                ip = cached;
                if (ip == null) {
                    ip = resolve();
                    cached = ip;
                }
            }
        }
        return ip;
    }

    private static String resolve() {
        String physical = null;
        String any = null;
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                String name = nic.getName() == null ? "" : nic.getName();
                if (VIRTUAL.matcher(name).matches()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address)
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    // en0 / eth0 / wlan0 这类物理网卡优先
                    if (name.startsWith("en") || name.startsWith("eth") || name.startsWith("wlan")) {
                        if (physical == null) {
                            physical = ip;
                        }
                    } else if (any == null) {
                        any = ip;
                    }
                }
            }
        } catch (Exception ignored) {
            // 拿不到就降级，不影响接入
        }
        if (physical != null) {
            return physical;
        }
        return any != null ? any : "127.0.0.1";
    }
}
