<template>
  <el-card v-loading="loading">
    <template #header>集成管理</template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <h3>分发端点</h3>
    <el-form v-if="canEdit" inline>
      <el-input v-model="endpoint.code" :disabled="endpoint.id != null" placeholder="请输入端点编码" />
      <el-input v-model="endpoint.name" placeholder="请输入端点名称" />
      <el-input v-model="endpoint.endpoint_url" placeholder="请输入端点地址" />
      <el-select v-model="endpoint.authentication_type" data-test="authentication-type" placeholder="请选择认证方式">
        <el-option label="无认证" value="NONE" />
        <el-option label="基础认证" value="BASIC" />
        <el-option label="Bearer Token" value="BEARER" />
        <el-option label="API Key" value="API_KEY" />
      </el-select>
      <el-input v-if="endpoint.authentication_type === 'BASIC'" v-model="endpoint.username" data-test="basic-username" placeholder="请输入用户名" autocomplete="off" />
      <el-input v-if="endpoint.authentication_type === 'BASIC'" v-model="endpoint.password" data-test="basic-password" type="password" show-password placeholder="请输入密码" autocomplete="new-password" />
      <el-input v-if="endpoint.authentication_type === 'BEARER'" v-model="endpoint.token" data-test="bearer-token" type="password" show-password placeholder="请输入 Bearer Token" autocomplete="new-password" />
      <el-input v-if="endpoint.authentication_type === 'API_KEY'" v-model="endpoint.header_name" data-test="api-key-header" placeholder="请输入请求头名称" />
      <el-input v-if="endpoint.authentication_type === 'API_KEY'" v-model="endpoint.value" data-test="api-key-value" type="password" show-password placeholder="请输入 API Key" autocomplete="new-password" />
      <el-button data-test="create-endpoint" :loading="savingEndpoint" @click="addEndpoint">{{ endpoint.id == null ? '新增端点' : '保存端点' }}</el-button>
      <el-button v-if="endpoint.id != null" @click="resetEndpoint">取消</el-button>
    </el-form>
    <el-table :data="endpoints" empty-text="暂无端点">
      <el-table-column prop="code" label="端点编码" />
      <el-table-column prop="name" label="端点名称" />
      <el-table-column label="端点地址"><template #default="{ row }">{{ row.endpoint_url === '[redacted]' ? '已隐藏' : row.endpoint_url }}</template></el-table-column>
      <el-table-column label="认证方式"><template #default="{ row }">{{ authenticationLabel(row.authentication_type) }}</template></el-table-column>
      <el-table-column label="认证凭据"><template #default="{ row }">{{ row.credentials_configured ? '已配置（已隐藏）' : '无需配置' }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="定时策略"><template #default="{ row }">{{ row.schedule_enabled ? `${row.schedule_cron} · ${row.schedule_timezone}` : '未启用' }}</template></el-table-column>
      <el-table-column v-if="canEdit || canSchedule" label="操作"><template #default="{ row }"><el-button v-if="canEdit" :data-test="`edit-endpoint-${row.id}`" link type="primary" @click="editEndpoint(row)">编辑</el-button><el-button v-if="canSchedule" :data-test="`schedule-endpoint-${row.id}`" link type="primary" @click="editSchedule(row)">定时策略</el-button></template></el-table-column>
    </el-table>
    <el-form v-if="schedule.endpoint_id != null && canSchedule" class="schedule-form" inline>
      <b>定时策略 · {{ endpointName(schedule.endpoint_id) }}</b>
      <el-switch v-model="schedule.enabled" active-text="启用" inactive-text="停用" />
      <el-input v-if="schedule.enabled" v-model="schedule.cron" placeholder="六字段 Cron，例如 0 30 9 * * *" />
      <el-select v-if="schedule.enabled" v-model="schedule.timezone" placeholder="请选择时区">
        <el-option label="中国标准时间（Asia/Shanghai）" value="Asia/Shanghai" />
        <el-option label="协调世界时（UTC）" value="UTC" />
      </el-select>
      <el-button data-test="save-schedule" :loading="savingSchedule" @click="saveSchedule">保存定时策略</el-button>
      <el-button @click="resetSchedule">取消</el-button>
    </el-form>

    <h3>分发订阅</h3>
    <el-form v-if="canEdit" inline>
      <el-select data-test="subscription-endpoint" v-model="subscription.endpoint_id" placeholder="请选择端点"><el-option v-for="item in endpoints" :key="item.id" :label="item.name || item.code" :value="item.id" /></el-select>
      <el-select v-model="subscription.object_type_id" placeholder="请选择主数据对象"><el-option v-for="item in objectTypes" :key="item.id" :label="item.name || item.code" :value="item.id" /></el-select>
      <el-select v-model="subscription.event_type" placeholder="请选择事件"><el-option v-for="code in events" :key="code" :label="eventLabel(code)" :value="code" /></el-select>
      <el-button data-test="create-subscription" :loading="savingSubscription" @click="addSubscription">新增订阅</el-button>
    </el-form>
    <el-table :data="subscriptions" empty-text="暂无订阅">
      <el-table-column label="端点"><template #default="{ row }">{{ endpointName(row.endpoint_id) }}</template></el-table-column>
      <el-table-column label="主数据对象"><template #default="{ row }">{{ objectName(row.object_type_id) }}</template></el-table-column>
      <el-table-column label="事件"><template #default="{ row }">{{ eventLabel(row.event_type) }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
    </el-table>
  </el-card>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createEndpoint, createSubscription, listEndpoints, listSubscriptions, updateEndpoint, updateEndpointSchedule } from '../../api/integration'
import { listObjectTypes } from '../../api/mdm'
import { chineseError, eventLabel, permissionCapability, statusLabel } from '../../utils/labels'
const endpoints = ref([]), subscriptions = ref([]), objectTypes = ref([]), loading = ref(false), savingEndpoint = ref(false), savingSubscription = ref(false), savingSchedule = ref(false), error = ref('')
const blankEndpoint = () => ({ id: null, code: '', name: '', endpoint_url: '', authentication_type: 'NONE', username: '', password: '', token: '', header_name: '', value: '' })
const endpoint = reactive(blankEndpoint())
const subscription = reactive({ endpoint_id: null, object_type_id: null, event_type: 'RECORD_CHANGED' })
const schedule = reactive({ endpoint_id: null, enabled: false, cron: '0 0 9 * * *', timezone: 'Asia/Shanghai' })
const events = ['RECORD_CHANGED', 'RECORD_CREATED', 'RECORD_UPDATED', 'RECORD_DELETED']
const user = JSON.parse(localStorage.getItem('user') || '{}')
const canEdit = computed(() => user.is_admin === true
  || permissionCapability('MDM_FIELD_MANAGE', 'can_edit'))
const canSchedule = computed(() => user.is_admin === true
  || permissionCapability('INTEGRATION_MANUAL_PUSH', 'can_edit'))
const authenticationLabel = value => ({ NONE: '无认证', BASIC: '基础认证', BEARER: 'Bearer Token', API_KEY: 'API Key' })[value] || '—'
const endpointName = id => endpoints.value.find(item => Number(item.id) === Number(id))?.name || id
const objectName = id => objectTypes.value.find(item => Number(item.id) === Number(id))?.name || id
async function load() {
  loading.value = true; error.value = ''
  try {
    const [endpointResponse, subscriptionResponse, objectResponse] = await Promise.all([listEndpoints(), listSubscriptions(), listObjectTypes()])
    endpoints.value = endpointResponse.data || []; subscriptions.value = subscriptionResponse.data || []; objectTypes.value = objectResponse.data || []
  } catch (exception) { error.value = chineseError(exception, '加载集成设置失败') }
  finally { loading.value = false }
}
async function addEndpoint() {
  if (!canEdit.value || savingEndpoint.value) return
  savingEndpoint.value = true; error.value = ''
  try {
    const credentials = endpoint.authentication_type === 'BASIC'
      ? { username: endpoint.username, password: endpoint.password }
      : endpoint.authentication_type === 'BEARER'
        ? { token: endpoint.token }
        : endpoint.authentication_type === 'API_KEY'
          ? { header_name: endpoint.header_name, value: endpoint.value }
          : undefined
    const hasCredentialValues = credentials && Object.values(credentials).some(value => String(value || '').length > 0)
    const payload = { name: endpoint.name, endpoint_url: endpoint.endpoint_url,
      authentication_type: endpoint.authentication_type, ...(hasCredentialValues ? { credentials } : {}) }
    if (endpoint.id == null) await createEndpoint({ code: endpoint.code, ...payload,
      ...(credentials && !hasCredentialValues ? { credentials } : {}) })
    else await updateEndpoint(endpoint.id, payload)
    resetEndpoint()
    await load(); ElMessage.success('端点创建成功')
  }
  catch (exception) { error.value = chineseError(exception, '创建端点失败') }
  finally { savingEndpoint.value = false }
}
function editEndpoint(value) { Object.assign(endpoint, blankEndpoint(), value, { username: '', password: '', token: '', header_name: '', value: '' }) }
function resetEndpoint() { Object.assign(endpoint, blankEndpoint()) }
function editSchedule(value) { Object.assign(schedule, { endpoint_id: value.id, enabled: value.schedule_enabled === true, cron: value.schedule_cron || '0 0 9 * * *', timezone: value.schedule_timezone || 'Asia/Shanghai' }) }
function resetSchedule() { Object.assign(schedule, { endpoint_id: null, enabled: false, cron: '0 0 9 * * *', timezone: 'Asia/Shanghai' }) }
async function saveSchedule() {
  if (!canSchedule.value || schedule.endpoint_id == null || savingSchedule.value) return
  savingSchedule.value = true; error.value = ''
  try {
    await updateEndpointSchedule(schedule.endpoint_id, { enabled: schedule.enabled, cron: schedule.enabled ? schedule.cron : null, timezone: schedule.enabled ? schedule.timezone : null })
    resetSchedule(); await load(); ElMessage.success('定时策略保存成功')
  } catch (exception) { error.value = chineseError(exception, '保存定时策略失败') }
  finally { savingSchedule.value = false }
}
async function addSubscription() {
  if (!canEdit.value || savingSubscription.value) return
  savingSubscription.value = true; error.value = ''
  try { await createSubscription({ ...subscription }); subscriptions.value = (await listSubscriptions()).data || []; ElMessage.success('订阅创建成功') }
  catch (exception) { error.value = chineseError(exception, '创建订阅失败') }
  finally { savingSubscription.value = false }
}
onMounted(load)
</script>
<style scoped>h3{margin:22px 0 12px}.schedule-form{margin:16px 0;padding:14px;background:#f5f7fa;border-radius:6px}</style>
