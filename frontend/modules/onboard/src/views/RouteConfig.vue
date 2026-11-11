<template>
  <div class="page">
    <div class="gw-card">
      <div class="gw-card-hd">
        路由配置
        <span class="hd-hint">规则保存后 30 秒内自动生效，无需重启网关</span>
      </div>
      <div class="gw-card-bd">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="业务方把调用地址从「上游服务」改成「网关地址」即可接入，代码零改动；每次经过网关的请求都会记录到日志追踪。"
          style="margin-bottom: 14px"
        />

        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索路径 / 应用 / 上游地址"
            clearable
            style="width: 280px"
            @keyup.enter="load"
            @clear="load"
          />
          <el-button @click="load">查询</el-button>
          <div class="spacer" />
          <el-button type="primary" @click="openDialog()">新增路由</el-button>
        </div>

        <el-table v-loading="loading" :data="pagedRows" size="small" border stripe>
          <el-table-column prop="pathPrefix" label="匹配路径" width="180" />
          <el-table-column prop="upstreamUrl" label="转发目标" min-width="210" show-overflow-tooltip />
          <el-table-column label="路径处理" width="190">
            <template #default="{ row }">
              <span class="path-tip">
                {{ row.stripPrefix === 1 ? '去首段' : '保留' }}{{ row.prefixPath ? ' + 加 ' + row.prefixPath : '' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="appName" label="归属应用" width="130">
            <template #default="{ row }">{{ row.appName || row.appId || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button link @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="rows.length" class="gw-card-ft route-pager">
          <span class="gw-sm gw-muted">共 {{ rows.length }} 条路由规则</span>
          <GwPager
            :total="rows.length"
            :current="page"
            :size="size"
            @page-change="onPage"
            @size-change="onSizeChange"
          />
        </div>

        <el-empty v-if="!loading && rows.length === 0" description="还没有配置转发规则" :image-size="80" />

        <el-divider content="接入示例" />
        <pre class="code-hint">{{ sample }}</pre>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑路由' : '新增路由'" width="560px">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="匹配路径" required>
          <el-input v-model="form.pathPrefix" placeholder="/order/**" />
          <div class="form-tip">支持通配符，如 <code>/order/**</code> 匹配 /order 开头的所有请求</div>
        </el-form-item>
        <el-form-item label="转发目标" required>
          <el-input v-model="form.upstreamUrl" placeholder="http://10.20.3.11:8080" />
          <div class="form-tip">只填协议 + 主机 + 端口，不要带路径</div>
        </el-form-item>
        <el-form-item label="追加前缀">
          <el-input v-model="form.prefixPath" placeholder="/api（可留空）" />
          <div class="form-tip">上游有 context-path 时填，如 /api</div>
        </el-form-item>
        <el-form-item label="去掉首段">
          <el-switch v-model="form.stripPrefix" :active-value="1" :inactive-value="0" />
          <span class="form-tip" style="margin-left: 8px">
            {{ form.stripPrefix === 1 ? '/order/create → /create' : '保持原路径转发' }}
          </span>
        </el-form-item>
        <el-form-item label="归属应用">
          <el-input v-model="form.appId" placeholder="app-order" />
        </el-form-item>
        <el-form-item label="应用名称">
          <el-input v-model="form.appName" placeholder="订单中心" />
        </el-form-item>
        <el-form-item label="服务名">
          <el-input v-model="form.serviceName" placeholder="order-service" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GwPager } from '@zimo/ui'
import { createRoute, deleteRoute, fetchRoutes, toggleRoute, updateRoute } from '../api'

const rows = ref([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)

const emptyForm = () => ({
  id: null,
  pathPrefix: '',
  upstreamUrl: '',
  prefixPath: '',
  stripPrefix: 1,
  status: 1,
  appId: '',
  appName: '',
  serviceName: '',
  remark: ''
})
const form = reactive(emptyForm())

/** 根据当前第一条规则生成调用示例 */
const sample = computed(() => {
  const first = rows.value[0]
  if (!first) {
    return `# 先在平台配置一条转发规则，再按下面的方式调用\ncurl "http://网关地址:28090/你的路径"`
  }
  const prefix = first.stripPrefix === 1
    ? first.pathPrefix.replace('/**', '').replace('/*', '')
    : ''
  const example = `${prefix || first.pathPrefix.replace('/**', '')}/your-path`
  return `# 配好规则后，业务方把地址指向网关即可（原上游：${first.upstreamUrl}）\ncurl "http://网关地址:28090${example}"\n\n# 每次调用都会出现在「日志追踪」里，含请求/响应报文和转发目标`
})

/** 前端分页：路由列表一次全量拉取，按页切片 */
const page = ref(1)
const size = ref(10)
const pagedRows = computed(() =>
  rows.value.slice((page.value - 1) * size.value, page.value * size.value)
)
const onPage = (p) => (page.value = p)
const onSizeChange = (s) => {
  size.value = s
  page.value = 1
}

async function load() {
  loading.value = true
  try {
    rows.value = (await fetchRoutes({ keyword: keyword.value || undefined })) || []
    page.value = 1
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, emptyForm(), row || {})
  dialogVisible.value = true
}

async function save() {
  if (!form.pathPrefix || !form.upstreamUrl) {
    ElMessage.warning('匹配路径和转发目标必填')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (payload.id) {
      await updateRoute(payload.id, payload)
    } else {
      await createRoute(payload)
    }
    ElMessage.success('已保存，30 秒内生效')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await toggleRoute(row.id)
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除规则 ${row.pathPrefix}？`, '删除路由', { type: 'warning' })
  await deleteRoute(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 分页条位于 gw-card-bd 内，抵消 16px 内边距以与卡片左右对齐 */
.route-pager {
  margin: 12px -16px -12px;
  border-top: 1px solid var(--gw-border-light);
}
.hd-hint {
  margin-left: 10px;
  font-size: 11.5px;
  font-weight: 400;
  color: var(--gw-text-tertiary);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.spacer {
  flex: 1;
}

.path-tip {
  font-size: 12px;
  color: var(--gw-text-secondary);
}

.form-tip {
  font-size: 11.5px;
  color: var(--gw-text-tertiary);
  line-height: 1.6;
}

.code-hint {
  margin: 0;
  padding: 12px 14px;
  background: var(--gw-bg);
  border: 1px solid var(--gw-border-light);
  border-radius: var(--gw-radius);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.75;
  color: var(--gw-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
