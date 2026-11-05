<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="info" effect="plain">设计稿</el-tag>
      <span class="gw-sm gw-muted">策略表单可编辑，暂不落库；后端 /auth/policy 接口待接入。</span>
    </p>

    <!-- 登录保护 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>登录保护</span>
        <span class="gw-sm gw-muted">防止暴力破解与撞库</span>
      </div>
      <div class="gw-card-bd">
        <el-form :model="policy" label-width="200px" size="small" class="policy-form">
          <el-form-item label="失败锁定阈值">
            <el-input-number v-model="policy.lockThreshold" :min="3" :max="20" controls-position="right" style="width: 120px" />
            <span class="unit">次错误后锁定</span>
          </el-form-item>
          <el-form-item label="锁定时长">
            <el-input-number v-model="policy.lockMinutes" :min="1" :max="1440" controls-position="right" style="width: 120px" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="图形验证码触发">
            <el-radio-group v-model="policy.captchaTrigger">
              <el-radio-button label="NONE">从不</el-radio-button>
              <el-radio-button label="FAIL2">失败 2 次后</el-radio-button>
              <el-radio-button label="ALWAYS">总是</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="同账号并发会话上限">
            <el-input-number v-model="policy.maxSessions" :min="1" :max="10" controls-position="right" style="width: 120px" />
            <span class="unit">个（超出时最旧的会话被顶下线）</span>
          </el-form-item>
        </el-form>
      </div>
    </section>

    <!-- 验证码 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>验证码策略</span>
        <span class="gw-sm gw-muted">邮箱 / 短信共用</span>
      </div>
      <div class="gw-card-bd">
        <el-form :model="policy" label-width="200px" size="small" class="policy-form">
          <el-form-item label="有效期">
            <el-input-number v-model="policy.otpTtl" :min="1" :max="30" controls-position="right" style="width: 120px" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="发送间隔">
            <el-input-number v-model="policy.otpInterval" :min="30" :max="600" :step="30" controls-position="right" style="width: 120px" />
            <span class="unit">秒（同目标地址）</span>
          </el-form-item>
          <el-form-item label="单日发送上限">
            <el-input-number v-model="policy.otpDailyLimit" :min="1" :max="100" controls-position="right" style="width: 120px" />
            <span class="unit">条（超出后当日不再下发）</span>
          </el-form-item>
          <el-form-item label="错误尝试上限">
            <el-input-number v-model="policy.otpMaxAttempts" :min="1" :max="10" controls-position="right" style="width: 120px" />
            <span class="unit">次（超出即作废该验证码）</span>
          </el-form-item>
        </el-form>
      </div>
    </section>

    <!-- 密码策略 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>密码策略</span>
        <span class="gw-sm gw-muted">仅作用于密码登录与修改密码</span>
      </div>
      <div class="gw-card-bd">
        <el-form :model="policy" label-width="200px" size="small" class="policy-form">
          <el-form-item label="最小长度">
            <el-input-number v-model="policy.pwdMinLength" :min="6" :max="32" controls-position="right" style="width: 120px" />
            <span class="unit">位</span>
          </el-form-item>
          <el-form-item label="复杂度要求">
            <el-checkbox v-model="policy.pwdUpper">大写字母</el-checkbox>
            <el-checkbox v-model="policy.pwdDigit">数字</el-checkbox>
            <el-checkbox v-model="policy.pwdSymbol">特殊字符</el-checkbox>
          </el-form-item>
          <el-form-item label="密码有效期">
            <el-input-number v-model="policy.pwdExpireDays" :min="0" :max="365" controls-position="right" style="width: 120px" />
            <span class="unit">天（0 表示不过期）</span>
          </el-form-item>
          <el-form-item label="历史密码不可重用">
            <el-switch v-model="policy.pwdHistory" />
            <span class="unit">最近 {{ policy.pwdHistoryCount }} 次</span>
            <el-input-number
              v-if="policy.pwdHistory"
              v-model="policy.pwdHistoryCount"
              :min="3" :max="10"
              controls-position="right"
              size="small"
              style="width: 90px; margin-left: 8px"
            />
          </el-form-item>
        </el-form>
      </div>
    </section>

    <!-- 双因子 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>双因子（2FA）</span>
        <span class="gw-sm gw-muted">TOTP 动态码 / 短信二次验证</span>
      </div>
      <div class="gw-card-bd">
        <el-form :model="policy" label-width="200px" size="small" class="policy-form">
          <el-form-item label="强制范围">
            <el-radio-group v-model="policy.mfaScope">
              <el-radio-button label="NONE">不强制</el-radio-button>
              <el-radio-button label="ADMIN">仅管理员</el-radio-button>
              <el-radio-button label="ALL">全部控制台账号</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="敏感操作二次验证">
            <el-switch v-model="policy.stepUp" active-text="改密码 / 绑手机 / 签发 API Key 时要求重新验证" inactive-text="" />
          </el-form-item>
          <el-form-item label="TOTP 允许时间偏移">
            <el-input-number v-model="policy.totpDrift" :min="0" :max="3" controls-position="right" style="width: 120px" />
            <span class="unit">个窗口（1 窗口 = 30 秒）</span>
          </el-form-item>
          <el-form-item label="备用验证码">
            <el-switch v-model="policy.backupCodes" active-text="生成 10 个一次性备用码" inactive-text="" />
          </el-form-item>
        </el-form>
      </div>
    </section>

    <!-- 令牌有效期 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>令牌有效期</span>
        <span class="gw-sm gw-muted">access 短期 + refresh 长期轮换</span>
      </div>
      <div class="gw-card-bd">
        <el-form :model="policy" label-width="200px" size="small" class="policy-form">
          <el-form-item label="Access Token（控制台）">
            <el-input-number v-model="policy.accessConsole" :min="5" :max="120" :step="5" controls-position="right" style="width: 120px" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="Access Token（开放 API）">
            <el-input-number v-model="policy.accessApi" :min="5" :max="240" :step="5" controls-position="right" style="width: 120px" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="Refresh Token（有效期）">
            <el-input-number v-model="policy.refreshDays" :min="1" :max="180" controls-position="right" style="width: 120px" />
            <span class="unit">天</span>
          </el-form-item>
          <el-form-item label="Refresh 闲置作废">
            <el-input-number v-model="policy.refreshIdleDays" :min="1" :max="90" controls-position="right" style="width: 120px" />
            <span class="unit">天未使用即失效</span>
          </el-form-item>
          <el-form-item label="Refresh 旋转">
            <el-switch v-model="policy.refreshRotate" active-text="每次刷新换新并作废旧 token（检测重放）" inactive-text="" />
          </el-form-item>
        </el-form>
      </div>
    </section>

    <div class="actions">
      <el-button type="primary" @click="save">保存策略</el-button>
      <el-button @click="resetAll">重置</el-button>
      <span class="gw-sm gw-muted" style="margin-left: 8px">策略变更对新建会话生效，存量会话在下次刷新时应用</span>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

const defaults = () => ({
  // 登录保护
  lockThreshold: 5,
  lockMinutes: 15,
  captchaTrigger: 'FAIL2',
  maxSessions: 3,
  // 验证码
  otpTtl: 5,
  otpInterval: 60,
  otpDailyLimit: 10,
  otpMaxAttempts: 5,
  // 密码
  pwdMinLength: 8,
  pwdUpper: true,
  pwdDigit: true,
  pwdSymbol: false,
  pwdExpireDays: 90,
  pwdHistory: true,
  pwdHistoryCount: 5,
  // 双因子
  mfaScope: 'ADMIN',
  stepUp: true,
  totpDrift: 1,
  backupCodes: true,
  // 令牌
  accessConsole: 20,
  accessApi: 60,
  refreshDays: 7,
  refreshIdleDays: 30,
  refreshRotate: true
})

const policy = reactive(defaults())
const snapshot = ref(JSON.stringify(policy))

const save = () => {
  snapshot.value = JSON.stringify(policy)
  ElMessage.success('策略已保存（设计稿，未落库）')
}

const resetAll = () => {
  Object.assign(policy, JSON.parse(snapshot.value))
  ElMessage.success('已还原为上次保存的策略')
}
</script>

<style scoped>
.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}

/* 表单不铺满整宽：label 定宽 + 控件自然宽度，长行折行后仍然对齐 */
.policy-form { max-width: 720px; }

.policy-form :deep(.el-form-item) { margin-bottom: 12px; }

.unit {
  margin-left: 8px;
  font-size: 12px;
  color: var(--gw-text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0 4px;
}
</style>
