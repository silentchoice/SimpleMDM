<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
      <h2>推送API管理</h2>
      <el-button type="primary" @click="openCreateDialog" v-if="canEdit">
        <el-icon><Plus /></el-icon> 新增API
      </el-button>
    </div>

    <!-- Filter -->
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form :inline="true">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="名称/标识符" clearable style="width: 220px;" @keyup.enter="fetchData">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;" @change="fetchData">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="hover">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="50" />
        <el-table-column prop="name" label="名称" width="120">
          <template #default="{ row }">
            <span style="font-weight: 600;">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="target_system" label="标识符" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.target_system }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="70">
          <template #default="{ row }">
            <el-tag :type="methodType(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="base_url" label="目标URL" min-width="260" show-overflow-tooltip />
        <el-table-column prop="auth_type" label="认证" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active'"
              @change="(val) => toggleStatus(row, val)"
              :disabled="!canEdit"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column prop="retry_max" label="重试" width="60" />
        <el-table-column prop="description" label="说明" width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="success" @click="handleTest(row)">测试</el-button>
            <el-button size="small" text type="primary" @click="openEditDialog(row)" v-if="canEdit">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)" v-if="canEdit">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.page_size"
          :total="total"
          :page-sizes="[10, 20]"
          layout="total, sizes, prev, pager, next"
          @change="fetchData"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增推送API' : '编辑推送API'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="显示名称" prop="name">
          <el-input v-model="form.name" placeholder="如: CRM系统" />
        </el-form-item>
        <el-form-item label="标识符" prop="target_system" v-if="dialogMode === 'create'">
          <el-input v-model="form.target_system" placeholder="如: CRM (字母大写，唯一)" />
        </el-form-item>
        <el-form-item label="HTTP方法">
          <el-select v-model="form.method" style="width: 100%;">
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标URL" prop="base_url">
          <el-input v-model="form.base_url" placeholder="http://host/api/endpoint" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-radio-group v-model="form.auth_type">
            <el-radio value="token">Token</el-radio>
            <el-radio value="basic">Basic Auth</el-radio>
            <el-radio value="none">无认证</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="认证配置" v-if="form.auth_type !== 'none'">
          <el-input
            v-model="form.auth_config"
            type="textarea"
            :rows="3"
            placeholder='JSON格式，如: {"header":"Authorization","prefix":"Bearer","token":"xxx"}'
          />
        </el-form-item>
        <el-form-item label="最大重试">
          <el-input-number v-model="form.retry_max" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="超时(秒)">
          <el-input-number v-model="form.timeout_sec" :min="5" :max="120" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="active" inactive-value="inactive" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">{{ form.status === 'active' ? '启用' : '停用' }}</span>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ dialogMode === 'create' ? '创建并同步推送' : '保存并同步推送' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Test Result Dialog -->
    <el-dialog v-model="testDialogVisible" title="连接测试结果" width="500px">
      <el-descriptions :column="1" border v-if="testResult">
        <el-descriptions-item label="目标URL">{{ testResult.url }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ testResult.method }}</el-descriptions-item>
        <el-descriptions-item label="认证方式">{{ testResult.auth_type }}</el-descriptions-item>
        <el-descriptions-item label="响应时间">{{ testResult.response_time_ms }}ms</el-descriptions-item>
        <el-descriptions-item label="状态码">
          <el-tag :type="testResult.status_code === 200 ? 'success' : 'danger'" size="small">
            {{ testResult.status_code }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="testDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { listPushApis, createPushApi, updatePushApi, deletePushApi, testPushApi } from '../../api/pushApi'
import { useUserStore } from '../../stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const canEdit = computed(() => userStore.isOperator() || userStore.isApprover())

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const testDialogVisible = ref(false)
const testResult = ref(null)

const query = reactive({ keyword: '', status: '', page: 1, page_size: 20 })

const form = reactive({
  name: '', target_system: '', method: 'POST', base_url: '',
  auth_type: 'token', auth_config: '', status: 'active',
  description: '', retry_max: 3, timeout_sec: 30,
})

const rules = {
  name: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  target_system: [{ required: true, message: '请输入标识符', trigger: 'blur' }],
  base_url: [{ required: true, message: '请输入目标URL', trigger: 'blur' }],
}

function methodType(m) {
  const map = { POST: 'warning', PUT: 'success', PATCH: 'info', DELETE: 'danger' }
  return map[m] || ''
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listPushApis({ keyword: query.keyword, status: query.status, page: query.page, page_size: query.page_size })
    tableData.value = res.data.items
    total.value = res.data.total
  } finally { loading.value = false }
}

function resetForm() {
  Object.assign(form, {
    name: '', target_system: '', method: 'POST', base_url: '',
    auth_type: 'token', auth_config: '', status: 'active',
    description: '', retry_max: 3, timeout_sec: 30,
  })
  editingId.value = null
}

function openCreateDialog() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEditDialog(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    name: row.name, method: row.method, base_url: row.base_url,
    auth_type: row.auth_type, auth_config: row.auth_config || '',
    status: row.status, description: row.description || '',
    retry_max: row.retry_max, timeout_sec: row.timeout_sec,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      const res = await createPushApi({ ...form })
      ElMessage.success(res.message || 'API配置已创建并同步推送')
    } else {
      const res = await updatePushApi(editingId.value, {
        name: form.name, method: form.method, base_url: form.base_url,
        auth_type: form.auth_type, auth_config: form.auth_config || null,
        status: form.status, description: form.description || null,
        retry_max: form.retry_max, timeout_sec: form.timeout_sec,
      })
      ElMessage.success(res.message || 'API配置已更新并同步推送')
    }
    dialogVisible.value = false
    fetchData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除 "${row.name}" (${row.target_system}) 吗？已有推送记录的API将被停用而非删除。`,
      '确认删除',
      { type: 'warning' }
    )
  } catch { return }

  try {
    const res = await deletePushApi(row.id)
    ElMessage.success(res.message)
    fetchData()
  } catch { /* handled */ }
}

async function handleTest(row) {
  try {
    const res = await testPushApi(row.id)
    testResult.value = res.data
    testDialogVisible.value = true
    ElMessage.success(res.message)
  } catch { /* handled */ }
}

async function toggleStatus(row, active) {
  try {
    const res = await updatePushApi(row.id, { status: active ? 'active' : 'inactive' })
    row.status = active ? 'active' : 'inactive'
    ElMessage.success(res.message || `API已${active ? '启用' : '停用'}`)
  } catch { /* handled */ }
}

onMounted(() => fetchData())
</script>
