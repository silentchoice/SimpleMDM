<template>
  <div>
    <div class="page-header"><h2>仪表盘</h2></div>

    <!-- Stat Cards -->
    <el-row :gutter="20" style="margin-bottom: 24px;">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-statistic title="人员总数" :value="stats.total_personnel">
              <template #prefix><el-icon color="#409EFF" :size="20"><UserFilled /></el-icon></template>
            </el-statistic>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-statistic title="待审批" :value="stats.pending_approvals">
              <template #prefix><el-icon :color="stats.pending_approvals > 0 ? '#e6a23c' : '#67c23a'" :size="20"><DocumentChecked /></el-icon></template>
            </el-statistic>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-statistic title="推送成功率" :value="stats.push_success_rate + '%'">
              <template #prefix><el-icon :color="stats.push_success_rate > 90 ? '#67c23a' : '#e6a23c'" :size="20"><Connection /></el-icon></template>
            </el-statistic>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <el-statistic title="数据一致性" value="100%">
              <template #prefix><el-icon color="#67c23a" :size="20"><CircleCheckFilled /></el-icon></template>
            </el-statistic>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Quick Actions -->
    <el-row :gutter="20" style="margin-bottom: 24px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header><span style="font-weight: 600;">快捷操作</span></template>
          <div style="display: flex; gap: 12px;">
            <el-button type="primary" @click="$router.push('/personnel/create')" v-if="userStore.isOperator()">
              <el-icon><Plus /></el-icon> 新增人员
            </el-button>
            <el-button type="warning" @click="$router.push('/approvals?type=pending_my')" v-if="userStore.isApprover()">
              <el-icon><DocumentChecked /></el-icon> 待我审批
            </el-button>
            <el-button @click="$router.push('/personnel')">
              <el-icon><UserFilled /></el-icon> 人员列表
            </el-button>
            <el-button @click="$router.push('/push-logs')">
              <el-icon><Connection /></el-icon> 推送日志
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Recent Approvals -->
    <el-card shadow="hover">
      <template #header><span style="font-weight: 600;">最近审批</span></template>
      <el-table :data="stats.recent_approvals || []" style="width: 100%">
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="personnel_name" label="人员" width="100" />
        <el-table-column prop="workflow_type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.workflow_type === 'create' ? 'success' : 'warning'" size="small">
              {{ row.workflow_type === 'create' ? '新增' : '变更' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitter_name" label="提交人" width="100" />
        <el-table-column prop="approver_name" label="审批人" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submit_time" label="时间" min-width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { getStats } from '../api/dashboard'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const stats = reactive({ total_personnel: 0, pending_approvals: 0, push_success_rate: 100, recent_approvals: [] })

function statusType(s) {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger', withdrawn: 'info' }
  return map[s] || 'info'
}

function statusLabel(s) {
  const map = { pending: '待审批', approved: '已通过', rejected: '已驳回', withdrawn: '已撤回' }
  return map[s] || s
}

onMounted(async () => {
  const res = await getStats()
  Object.assign(stats, res.data)
})
</script>
