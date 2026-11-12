<template>
  <div class="page">
    <p class="demo-tip gw-sm gw-muted">
      部署关联：服务实例（ip:port / hostname）↔ 主机资源水位 ↔ 调用记录（源 IP → 目标 IP）。不做机柜 / 交换机等物理网络拓扑
    </p>

    <!-- 部署清单 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>部署清单</span>
        <div class="hd-actions">
          <el-select v-model="minutes" size="small" style="width: 120px" @change="loadAll">
            <el-option label="近 15 分钟" :value="15" />
            <el-option label="近 1 小时" :value="60" />
            <el-option label="近 6 小时" :value="360" />
          </el-select>
          <el-button size="small" plain :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="deployments" v-loading="loading" size="small" row-key="node" style="width: 100%">
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-table :data="row.instances" size="small" class="inner-table">
                <el-table-column prop="serviceName" label="服务" min-width="180" />
                <el-table-column prop="serviceId" label="服务 ID" min-width="180" />
                <el-table-column prop="appName" label="应用" width="140" />
                <el-table-column label="实例地址" width="170">
                  <template #default="{ row: inst }">{{ inst.ip }}:{{ inst.port }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="90" />
                <el-table-column prop="lastHeartbeatTime" label="最近心跳" width="170" />
              </el-table>
            </template>
          </el-table-column>
          <el-table-column prop="node" label="主机 / pod" min-width="220" />
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column label="实例数" width="100">
            <template #default="{ row }">{{ row.instances.length }}</template>
          </el-table-column>
          <el-table-column label="CPU" width="100">
            <template #default="{ row }">{{ row.cpuUsage ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="内存" width="100">
            <template #default="{ row }">{{ row.memUsedPct ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="磁盘" width="100">
            <template #default="{ row }">{{ row.diskUsedPct ?? '-' }}%</template>
          </el-table-column>
          <el-table-column prop="lastReportTime" label="最近指标上报" width="180" />
        </el-table>
        <div v-if="!loading && deployments.length === 0" class="empty-tip gw-sm gw-muted">
          暂无部署数据：服务需通过 SDK 注册（hostname 随注册上报）或已有 tb_gw_service 记录
        </div>
      </div>
    </section>

    <!-- 跨主机调用 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>跨主机调用</span>
        <span class="gw-sm gw-muted">按「源 IP → 目标 IP」聚合调用量 / 平均耗时 / 错误率（近 {{ minutes }} 分钟）</span>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="crossHost" v-loading="loading" size="small" style="width: 100%" max-height="380">
          <el-table-column label="源" min-width="200">
            <template #default="{ row }">
              <span class="gw-mono gw-sm">{{ row.srcHost || row.srcIp }}</span>
              <span class="gw-sm gw-muted">（{{ row.srcIp }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="目标" min-width="200">
            <template #default="{ row }">
              <span class="gw-mono gw-sm">{{ row.dstHost || row.dstIp }}</span>
              <span class="gw-sm gw-muted">（{{ row.dstIp }}）</span>
            </template>
          </el-table-column>
          <el-table-column prop="calls" label="调用量" width="100" />
          <el-table-column label="平均耗时" width="110">
            <template #default="{ row }">{{ row.avgCost ?? '-' }} ms</template>
          </el-table-column>
          <el-table-column label="错误率" width="100">
            <template #default="{ row }">
              <span :class="{ 'gw-danger': Number(row.errorRate) > 5 }">{{ row.errorRate ?? 0 }}%</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastTime" label="最近调用" width="180" />
        </el-table>
      </div>
    </section>

    <!-- 影响面分析 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>单点影响面</span>
        <div class="hd-actions">
          <el-select v-model="impactHost" size="small" style="width: 240px" placeholder="选择主机 / IP" filterable @change="loadImpact">
            <el-option v-for="node in deployments" :key="node.node" :label="node.node" :value="node.node" />
          </el-select>
        </div>
      </div>
      <div class="gw-card-bd">
        <div v-if="!impact.hostname" class="gw-sm gw-muted">选择一台主机，查看故障时受影响的实例与服务调用占比</div>
        <template v-else>
          <div class="impact-summary">
            <div class="metric">
              <div class="gw-sm gw-muted">实例数</div>
              <div class="value">{{ impact.instanceCount }}</div>
            </div>
            <div class="metric">
              <div class="gw-sm gw-muted">涉及服务</div>
              <div class="value">{{ impact.serviceCount }}</div>
            </div>
            <div class="metric">
              <div class="gw-sm gw-muted">受影响调用量</div>
              <div class="value">{{ impact.affectedCalls }}</div>
            </div>
            <div class="metric">
              <div class="gw-sm gw-muted">全站调用占比</div>
              <div class="value warn">{{ impact.callsShare }}%</div>
            </div>
          </div>
          <el-table :data="impact.instances || []" size="small" style="width: 100%">
            <el-table-column prop="serviceName" label="服务" min-width="180" />
            <el-table-column prop="serviceId" label="服务 ID" min-width="170" />
            <el-table-column label="实例地址" width="170">
              <template #default="{ row }">{{ row.ip }}:{{ row.port }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="calls" label="窗口内调用量" width="130" />
            <el-table-column label="调用占比" width="110">
              <template #default="{ row }">{{ row.callsShare }}%</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchCrossHost, fetchDeployments, fetchImpact } from '../api.js'

const minutes = ref(60)
const loading = ref(false)
const deployments = ref([])
const crossHost = ref([])
const impactHost = ref('')
const impact = ref({})

const loadImpact = async () => {
  if (!impactHost.value) return
  impact.value = (await fetchImpact(impactHost.value, minutes.value)) || {}
}

const loadAll = async () => {
  loading.value = true
  try {
    const [nodes, cross] = await Promise.all([
      fetchDeployments(minutes.value).catch(() => []),
      fetchCrossHost(minutes.value).catch(() => [])
    ])
    deployments.value = nodes || []
    crossHost.value = cross || []
    if (!impactHost.value && deployments.value.length) {
      impactHost.value = deployments.value[0].node
    }
    await loadImpact()
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.demo-tip {
  margin: 0 0 10px;
}

.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.inner-table {
  padding: 4px 0 4px 48px;
}

.empty-tip {
  padding: 18px 16px;
}

.impact-summary {
  display: flex;
  gap: 28px;
  margin: 4px 0 16px;
}

.metric .value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 2px;
}

.metric .value.warn {
  color: var(--gw-warning, #e6a23c);
}
</style>
