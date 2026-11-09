<template>
  <section class="gw-card" style="margin-top: 12px;">
    <div class="gw-card-hd">
      <span>WebSocket 会话</span>
      <span class="gw-muted gw-sm">共 {{ total }} 条会话</span>
    </div>

    <!-- 过滤栏 -->
    <div class="gw-card-bd">
      <div class="filter-bar">
        <el-input v-model="query.traceId" placeholder="输入 traceId 查询" clearable style="width: 200px;" />
        <el-input v-model="query.path" placeholder="握手路径模糊匹配" clearable style="width: 220px;" />
        <el-select v-model="query.closeStatus" placeholder="全部状态" clearable style="width: 140px;">
          <el-option label="正常关闭" value="NORMAL" />
          <el-option label="异常断开" value="ABNORMAL" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <!-- 会话列表 -->
    <div class="gw-card-bd no-pad">
      <el-table :data="rows" size="small" style="width: 100%" @row-click="openFrames">
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.connStart) }}</template>
        </el-table-column>
        <el-table-column prop="requestPath" label="握手路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="upstreamUrl" label="上游地址" width="200" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="客户端IP" width="130" />
        <el-table-column prop="traceId" label="traceId" width="150" />
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column label="入站（客户端→下游）" width="150" align="center">
          <template #default="{ row }">{{ row.inMsgCount }} 帧 / {{ fmtBytes(row.inBytes) }}</template>
        </el-table-column>
        <el-table-column label="出站（下游→客户端）" width="150" align="center">
          <template #default="{ row }">{{ row.outMsgCount }} 帧 / {{ fmtBytes(row.outBytes) }}</template>
        </el-table-column>
        <el-table-column label="关闭状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.closeStatus === 'NORMAL' ? 'success' : 'danger'">
              {{ row.closeStatus === 'NORMAL' ? '正常' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openFrames(row)">帧明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页条（吸底） -->
    <div class="gw-card-ft">
      <span class="gw-muted gw-sm">共 {{ total }} 条 · 每页 {{ query.size }} 条 · 第 {{ query.current }} / {{ pages }} 页</span>
      <el-pagination
        layout="prev, pager, next"
        :current-page="query.current"
        :page-size="query.size"
        :total="total"
        @current-change="onPage"
      />
    </div>
  </section>

  <!-- 帧明细抽屉 -->
  <el-drawer v-model="frameVisible" :title="'帧明细 · ' + (frameSession?.sessionKey || '')" size="min(680px, 94vw)">
    <div v-if="frameSession" v-loading="frameLoading">
      <div class="drawer-sec">
        <div class="drawer-sec-title">会话信息</div>
        <div class="kv-grid">
          <div class="kv-item"><span class="gw-sm gw-muted">握手路径</span><b>{{ frameSession.requestPath }}</b></div>
          <div class="kv-item"><span class="gw-sm gw-muted">上游地址</span><b>{{ frameSession.upstreamUrl }}</b></div>
          <div class="kv-item"><span class="gw-sm gw-muted">traceId</span><b>{{ frameSession.traceId || '—' }}</b></div>
          <div class="kv-item"><span class="gw-sm gw-muted">时长</span><b>{{ frameSession.durationMs }} ms</b></div>
        </div>
      </div>

      <div v-if="frameSession.inSample" class="drawer-sec">
        <div class="drawer-sec-title">入站首帧样本</div>
        <pre class="code-block">{{ frameSession.inSample }}</pre>
      </div>
      <div v-if="frameSession.outSample" class="drawer-sec">
        <div class="drawer-sec-title">出站首帧样本</div>
        <pre class="code-block">{{ frameSession.outSample }}</pre>
      </div>

      <div class="drawer-sec">
        <div class="drawer-sec-title">帧明细（{{ frames.length }} 条已记录）</div>
        <el-table :data="frames" size="small" style="width: 100%">
          <el-table-column label="方向" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.direction === 'IN' ? 'primary' : 'warning'">{{ row.direction }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="msgType" label="类型" width="80" />
          <el-table-column prop="length" label="字节数" width="90" />
          <el-table-column label="到达时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.recvTime) }}</template>
          </el-table-column>
          <el-table-column label="Payload" min-width="260">
            <template #default="{ row }">
              <span class="gw-mono frame-payload">{{ framePayload(row.payload) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!frames.length && !frameLoading" class="gw-sm gw-muted" style="padding: 12px;">
          该会话未记录帧明细（可能达到每会话帧数上限，或采集时帧记录功能未开启）
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { fetchWsSessions, fetchWsFrames } from '../api'
import { fmtTime, fmtBytes } from '@zimo/shared'

const rows = ref([])
const total = ref(0)
const pages = ref(1)

const query = reactive({
  traceId: '',
  path: '',
  closeStatus: '',
  current: 1,
  size: 10
})

const load = async () => {
  const params = {}
  Object.keys(query).forEach((k) => {
    const v = query[k]
    if (v !== '' && v !== null && v !== undefined) params[k] = v
  })
  try {
    const page = await fetchWsSessions(params)
    rows.value = page.records || []
    total.value = page.total || 0
    pages.value = page.pages || 1
  } catch (e) {
    rows.value = []
    total.value = 0
  }
}

const reset = () => {
  query.traceId = ''
  query.path = ''
  query.closeStatus = ''
  query.current = 1
  load()
}

const onPage = (p) => {
  query.current = p
  load()
}

/* ---- 帧明细抽屉 ---- */
const frameVisible = ref(false)
const frameLoading = ref(false)
const frameSession = ref(null)
const frames = ref([])

const openFrames = async (row) => {
  frameSession.value = row
  frameVisible.value = true
  frameLoading.value = true
  frames.value = []
  try {
    frames.value = await fetchWsFrames(row.sessionKey)
  } catch (e) {
    frames.value = []
  } finally {
    frameLoading.value = false
  }
}

/** 二进制帧 payload 是 hex，加空格分组便于阅读；文本帧原样 */
const framePayload = (p) => {
  if (!p) return '—'
  if (/^[0-9a-f]{4,}/.test(p) && p.includes('...')) return p
  if (/^[0-9a-f]+$/.test(p) && p.length >= 8) return p.replace(/(.{2})/g, '$1 ').trim()
  return p.length > 120 ? p.slice(0, 120) + '…' : p
}

onMounted(load)
</script>

<style scoped>
.frame-payload {
  word-break: break-all;
  font-size: 12px;
}
</style>
