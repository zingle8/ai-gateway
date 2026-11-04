<template>
  <div class="page">
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>服务发现</span>
        <span class="gw-muted gw-sm">共 {{ total }} 个注册服务</span>
      </div>

      <!-- 过滤栏 -->
      <div class="gw-card-bd">
        <div class="filter-bar">
          <el-input v-model="query.keyword" placeholder="应用ID / 应用名 / 服务名" clearable style="width: 220px;" />
          <el-select v-model="query.appId" placeholder="全部应用" clearable style="width: 180px;">
            <el-option v-for="app in appOptions" :key="app" :label="app" :value="app" />
          </el-select>
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px;">
            <el-option label="健康" value="健康" />
            <el-option label="亚健康" value="亚健康" />
            <el-option label="异常" value="异常" />
          </el-select>
          <el-select v-model="query.language" placeholder="全部语言" clearable style="width: 150px;">
            <el-option v-for="lang in langOptions" :key="lang" :label="lang" :value="lang" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <!-- 列表 -->
      <div class="gw-card-bd no-pad">
        <el-table :data="rows" size="small" style="width: 100%" @row-click="openDetail">
          <el-table-column prop="appId" label="应用ID" width="120" />
          <el-table-column prop="appName" label="应用名" width="110" />
          <el-table-column prop="serviceName" label="服务名" min-width="160" show-overflow-tooltip />
          <el-table-column prop="port" label="端口" width="80" />
          <el-table-column prop="language" label="语言类型" width="110" />
          <el-table-column prop="version" label="版本号" width="100" />
          <el-table-column prop="qps" label="QPS" width="90" />
          <el-table-column prop="avgRt" label="平均 RT" width="100" />
          <el-table-column label="健康状态" width="110">
            <template #default="{ row }">
              <span class="status-dot" :class="dotClass(row.status)"></span> {{ row.status }}
            </template>
          </el-table-column>
          <el-table-column label="入站 / 出网流量" width="170">
            <template #default="{ row }">{{ row.inFlow }} / {{ row.outFlow }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
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

    <!-- 服务详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="'服务详情 · ' + (detail.serviceName || '')" size="560px">
      <div v-if="detail.serviceName" class="drawer-sec">
        <div class="drawer-sec-title">基本信息</div>
        <div class="kv-grid">
          <div v-for="f in detailFields" :key="f.label" class="kv-item">
            <span class="gw-sm gw-muted">{{ f.label }}</span>
            <b>{{ f.value }}</b>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { fetchServices } from '../api'
import { fmtTime, dotClass } from '@zimo/shared'

const rows = ref([])
const total = ref(0)
const pages = ref(1)
const detailVisible = ref(false)
const detail = ref({})

const query = reactive({
  keyword: '',
  appId: '',
  status: '',
  language: '',
  current: 1,
  size: 10
})

const appOptions = [
  'app-order', 'app-user', 'app-search', 'app-file', 'app-pay', 'app-auth',
  'app-msg', 'app-inventory', 'app-coupon', 'app-settle', 'app-report', 'app-open'
]
const langOptions = ['Java 17', 'Go 1.22', 'Node.js 20']

const load = async () => {
  try {
    const page = await fetchServices(query)
    rows.value = page.records || []
    total.value = page.total || 0
    pages.value = page.pages || 1
  } catch (e) {
    rows.value = []
    total.value = 0
  }
}

// 首屏加载：原先漏了这一步，进入页面时不会查询，只有手动点「查询」才有数据
onMounted(load)

const reset = () => {
  query.keyword = ''
  query.appId = ''
  query.status = ''
  query.language = ''
  query.current = 1
  load()
}

const onPage = (p) => {
  query.current = p
  load()
}

const openDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}


const detailFields = computed(() => {
  const d = detail.value
  return [
    { label: '应用ID', value: d.appId },
    { label: '应用名', value: d.appName },
    { label: '服务名', value: d.serviceName },
    { label: '端口', value: d.port },
    { label: '语言类型', value: d.language },
    { label: '版本号', value: d.version },
    { label: 'QPS', value: d.qps },
    { label: '平均 RT', value: d.avgRt + ' ms' },
    { label: '健康状态', value: d.status },
    { label: '入站 / 出网流量', value: (d.inFlow || '-') + ' / ' + (d.outFlow || '-') },
    { label: '实例数', value: d.instances },
    { label: '注册时间', value: fmtTime(d.registerTime) }
  ]
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  background: var(--gw-text-tertiary);
}

.status-dot.success { background: var(--gw-success); }
.status-dot.warning { background: var(--gw-warning); }
.status-dot.danger { background: var(--gw-danger); }

.drawer-sec { margin-bottom: 18px; }

.drawer-sec-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid var(--gw-primary);
}

.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
}

.kv-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
</style>
