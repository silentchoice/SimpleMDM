<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
      <h2>审批详情 #{{ detail.id }}</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="16">
        <!-- Info card -->
        <el-card shadow="hover" style="margin-bottom: 16px;">
          <template #header><span style="font-weight: 600;">审批信息</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="审批类型">
              <el-tag :type="detail.workflow_type === 'create' ? 'success' : 'warning'" size="small">
                {{ detail.workflow_type === 'create' ? '新增人员' : '人员变更' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交人">{{ detail.submitter_name }}</el-descriptions-item>
            <el-descriptions-item label="审批人">{{ detail.approver_name || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="相关人员">{{ detail.personnel_name }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ detail.submit_time }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.approve_time" label="审批时间">{{ detail.approve_time }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.approve_comment" label="审批意见" :span="2">
              {{ detail.approve_comment }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.withdrawn_time" label="撤回时间">{{ detail.withdrawn_time }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- Change Diff -->
        <el-card shadow="hover" v-if="parsedDiff && Object.keys(parsedDiff).length > 0">
          <template #header><span style="font-weight: 600;">变更对比</span></template>
          <ChangeDiff :change-data="parsedDiff" />
        </el-card>

        <!-- Approval Actions -->
        <el-card shadow="hover" v-if="detail.status === 'pending'" style="margin-top: 16px;">
          <template #header><span style="font-weight: 600;">审批操作</span></template>
          <template v-if="userStore.isApprover()">
            <el-form label-position="top">
              <el-form-item label="审批意见">
                <el-input v-model="comment" type="textarea" :rows="3" placeholder="请输入审批意见（驳回时必填）" />
              </el-form-item>
              <el-form-item>
                <el-button type="success" @click="handleApprove" :loading="acting">通过</el-button>
                <el-button type="danger" @click="handleReject" :loading="acting">驳回</el-button>
              </el-form-item>
            </el-form>
          </template>
          <template v-else-if="userStore.isOperator() && detail.submitter_id === userStore.user?.id">
            <el-button type="warning" @click="handleWithdraw" :loading="acting">撤回申请</el-button>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getApproval, approve, reject, withdraw } from '../../api/approval'
import { useUserStore } from '../../stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChangeDiff from '../../components/ChangeDiff.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref({})
const comment = ref('')
const acting = ref(false)

const parsedDiff = computed(() => {
  if (!detail.value.change_data) return null
  try {
    if (typeof detail.value.change_data === 'string') {
      return JSON.parse(detail.value.change_data)
    }
    return detail.value.change_data
  } catch {
    return null
  }
})

function statusType(s) {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger', withdrawn: 'info' }
  return map[s] || 'info'
}

function statusLabel(s) {
  const map = { pending: '待审批', approved: '已通过', rejected: '已驳回', withdrawn: '已撤回' }
  return map[s] || s
}

async function fetchDetail() {
  const res = await getApproval(route.params.id)
  detail.value = res.data
}

async function handleApprove() {
  acting.value = true
  try {
    await approve(detail.value.id, comment.value || '同意')
    ElMessage.success('审批已通过，数据已生效并推送至下游系统')
    fetchDetail()
  } finally {
    acting.value = false
  }
}

async function handleReject() {
  if (!comment.value.trim()) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  try {
    await ElMessageBox.confirm('确定驳回该申请吗？', '确认驳回', { type: 'warning' })
  } catch {
    acting.value = false
    return
  }
  acting.value = true
  try {
    await reject(detail.value.id, comment.value)
    ElMessage.success('审批已驳回')
    fetchDetail()
  } finally {
    acting.value = false
  }
}

async function handleWithdraw() {
  try {
    await ElMessageBox.confirm('确定撤回该申请吗？', '确认撤回', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    await withdraw(detail.value.id)
    ElMessage.success('申请已撤回')
    fetchDetail()
  } finally {
    acting.value = false
  }
}

onMounted(() => fetchDetail())
</script>
