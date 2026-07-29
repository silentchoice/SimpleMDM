<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
      <h2>人员管理</h2>
      <el-button type="primary" @click="$router.push('/personnel/create')" v-if="userStore.hasEditPermission">
        <el-icon><Plus /></el-icon> 新增人员
      </el-button>
    </div>

    <!-- Search & Filter -->
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form :inline="true" :model="query" style="display: flex; flex-wrap: wrap;">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="姓名 / 工号 / 职位" clearable style="width: 240px;" @keyup.enter="fetchData">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.department" placeholder="全部" clearable style="width: 180px;" @change="fetchData">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="hover">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="employee_code" label="工号" width="100" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="gender" label="性别" width="60" />
        <el-table-column prop="department" label="部门" width="130" />
        <el-table-column prop="position" label="职位" width="140" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="170" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updated_at" label="更新时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$router.push(`/personnel/${row.id}`)">查看</el-button>
            <el-button
              v-if="userStore.hasEditPermission && row.status === 'active'"
              size="small" text type="warning"
              @click="$router.push(`/personnel/${row.id}/edit`)"
            >编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.page_size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { listPersonnel, getDepartments } from '../../api/personnel'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const departments = ref([])
const query = reactive({ keyword: '', department: '', page: 1, page_size: 10 })

function statusType(s) {
  const map = { active: 'success', inactive: 'info', pending_approval: 'warning' }
  return map[s] || 'info'
}

function statusLabel(s) {
  const map = { active: '正常', inactive: '禁用', pending_approval: '待审批' }
  return map[s] || s
}

function resetQuery() {
  query.keyword = ''
  query.department = ''
  query.page = 1
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listPersonnel({
      keyword: query.keyword,
      department: query.department,
      page: query.page,
      page_size: query.page_size,
    })
    tableData.value = res.data.items
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchData()
  try {
    const res = await getDepartments()
    departments.value = res.data || []
  } catch { /* ignore */ }
})
</script>
