<template>
  <div class="scheduler-page">
    <!-- 任务列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>调度任务</span>
          <el-button type="primary" size="small" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </template>
      <el-table :data="pagedJobs" v-loading="loading" highlight-current-row @row-click="showDetail" style="cursor: pointer">
        <el-table-column prop="jobName" label="任务名称" min-width="280" show-overflow-tooltip />
        <el-table-column prop="jobKey" label="任务键" width="150" />
        <el-table-column prop="runTime" label="调度时间" width="100" align="center">
          <template #default="{ row }">每日 {{ row.runTime }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunStatus" label="上次执行" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lastRunStatus" :type="row.lastRunStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.lastRunStatus === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
            <span v-else class="muted">未执行</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunTime" label="上次执行时间" width="170">
          <template #default="{ row }">{{ fmt(row.lastRunTime) || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="runJob(row.jobKey)">手动执行</el-button>
            <el-button size="small" link @click.stop="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="sched-pager">
        <span class="muted sched-total">共 {{ jobs.length }} 个调度任务</span>
        <GwPager
          :total="jobs.length"
          :current="page"
          :size="size"
          @page-change="onPage"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 任务详情弹框 -->
    <el-dialog v-model="dialogVisible" :title="`任务详情：${current?.jobName || ''}`" width="880px" top="6vh">
      <template v-if="current">
        <!-- 任务状态详情 -->
        <el-descriptions :column="3" border size="small" class="mb16">
          <el-descriptions-item label="任务键">{{ current.jobKey }}</el-descriptions-item>
          <el-descriptions-item label="调度时间">每日 {{ current.runTime }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">
            <el-tag :type="current.status === 1 ? 'success' : 'info'" size="small">{{ current.status === 1 ? '启用' : '停用' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上次执行状态">
            <el-tag v-if="current.lastRunStatus" :type="current.lastRunStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ current.lastRunStatus === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
            <span v-else>未执行</span>
          </el-descriptions-item>
          <el-descriptions-item label="上次执行时间">{{ fmt(current.lastRunTime) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="任务说明" :span="3">{{ current.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 执行计划列表 -->
        <h4 class="sec-title">执行计划（分区预建等前瞻性计划）</h4>
        <el-table :data="plans" v-loading="detailLoading" size="small" max-height="240" border>
          <el-table-column prop="planDate" label="计划日期" width="110" />
          <el-table-column prop="planType" label="类型" width="130" />
          <el-table-column prop="target" label="作用表" min-width="160" />
          <el-table-column prop="partitionName" label="分区名" width="110" />
          <el-table-column prop="partitionValue" label="分区边界" width="160" />
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="planTag(row.status)" size="small">{{ planText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="doneTime" label="完成时间" width="160">
            <template #default="{ row }">{{ fmt(row.doneTime) || '—' }}</template>
          </el-table-column>
        </el-table>

        <!-- 执行记录列表 -->
        <h4 class="sec-title">执行记录</h4>
        <el-table :data="logs" v-loading="detailLoading" size="small" max-height="240" border>
          <el-table-column prop="startTime" label="开始时间" width="160">
            <template #default="{ row }">{{ fmt(row.startTime) }}</template>
          </el-table-column>
          <el-table-column prop="runType" label="触发方式" width="90" align="center">
            <template #default="{ row }">{{ row.runType === 'MANUAL' ? '手动' : '定时' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalRows" label="处理行数" width="100" align="right" />
          <el-table-column prop="detail" label="结果摘要" min-width="260" show-overflow-tooltip />
          <el-table-column prop="error" label="失败原因" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.error || '—' }}</template>
          </el-table-column>
          <el-table-column prop="host" label="执行实例" width="140" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { GwPager } from '@zimo/ui'
import { ElMessage } from 'element-plus'
import { request } from '@zimo/shared'

const loading = ref(false)
const detailLoading = ref(false)
const jobs = ref([])
const current = ref(null)
const plans = ref([])
const logs = ref([])
const dialogVisible = ref(false)

/** 前端分页：调度任务列表按页切片 */
const page = ref(1)
const size = ref(10)
const pagedJobs = computed(() =>
  jobs.value.slice((page.value - 1) * size.value, page.value * size.value)
)
const onPage = (p) => (page.value = p)
const onSizeChange = (s) => {
  size.value = s
  page.value = 1
}

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '')
const planTag = (s) => ({ DONE: 'success', FAILED: 'danger', PENDING: 'warning' }[s] || 'info')
const planText = (s) => ({ DONE: '已完成', FAILED: '失败', PENDING: '待执行' }[s] || s)

const loadJobs = async () => {
  loading.value = true
  try {
    // request 拦截器已解包 { code, message, data }，直接返回 data
    jobs.value = (await request.get('/scheduler/jobs')) || []
  } finally {
    loading.value = false
  }
}

const loadDetail = async (jobKey) => {
  detailLoading.value = true
  try {
    const [planList, logList] = await Promise.all([
      request.get(`/scheduler/jobs/${jobKey}/plans?limit=50`),
      request.get(`/scheduler/jobs/${jobKey}/logs?limit=50`)
    ])
    plans.value = planList || []
    logs.value = logList || []
  } finally {
    detailLoading.value = false
  }
}

const showDetail = async (row) => {
  current.value = row
  dialogVisible.value = true
  await loadDetail(row.jobKey)
}

const runJob = async (jobKey) => {
  await request.post(`/scheduler/jobs/${jobKey}/run`)
  ElMessage.success('任务已触发')
  await loadJobs()
  if (current.value?.jobKey === jobKey) await loadDetail(jobKey)
}

const loadAll = loadJobs
onMounted(loadJobs)
</script>

<style scoped>
.scheduler-page { display: flex; flex-direction: column; gap: 16px; }
.sched-pager { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding-top: 10px; }
.sched-total { font-size: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.muted { color: var(--el-text-color-secondary); }
.mb16 { margin-bottom: 16px; }
.sec-title { margin: 8px 0 8px; font-size: 14px; color: var(--el-text-color-primary); }
</style>
