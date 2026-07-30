<template>
  <div>
    <div class="page-header header-row">
      <h2>部门主数据</h2>
      <el-button v-if="userStore.hasEditPermission" type="primary"
        @click="$router.push('/personnel/create')">
        <el-icon><Plus /></el-icon> 新增
      </el-button>
    </div>
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form :inline="true" :model="query">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="搜索动态字段值" clearable
            style="width: 240px;" @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.department" placeholder="全部" clearable
            style="width: 180px;" @change="fetchData">
            <el-option v-for="department in departments" :key="department"
              :label="department" :value="department" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="hover">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="owner_dept" label="所属部门" width="130" />
        <el-table-column v-for="definition in visibleDefinitions" :key="definition.field_key"
          :label="definition.field_name" min-width="120">
          <template #default="{ row }">
            <DynamicFieldValue :definition="definition"
              :value="row.data?.[definition.field_key]" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updated_at" label="更新时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary"
              @click="$router.push(`/personnel/${row.id}`)">查看</el-button>
            <el-button v-if="canEdit(row) && row.status === 'active'"
              size="small" text type="warning"
              @click="$router.push(`/personnel/${row.id}/edit`)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.page_size"
          :total="total" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" @change="fetchData" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listPersonnel, getDepartments } from '../../api/personnel'
import { listFieldDefs } from '../../api/deptFields'
import { useUserStore } from '../../stores/user'
import { sortedDefinitions } from '../../utils/dynamicFields'
import DynamicFieldValue from '../../components/DynamicFieldValue.vue'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref([])
const definitions = ref([])
const total = ref(0)
const departments = ref([])
const query = reactive({ keyword: '', department: '', page: 1, page_size: 10 })
const visibleDefinitions = computed(() =>
  sortedDefinitions(definitions.value.filter(field => !field.system_field))
)

function canEdit(row) {
  return (userStore.permissions || []).some(permission =>
    permission.perm_type === 'EDIT' &&
    (permission.scope_type === 'ALL' || permission.scope_value === row.owner_dept)
  )
}
function statusType(status) {
  return ({ active: 'success', inactive: 'info', pending_approval: 'warning' })[status] || 'info'
}
function statusLabel(status) {
  return ({ active: '正常', inactive: '禁用', pending_approval: '待审批' })[status] || status
}
function resetQuery() {
  Object.assign(query, { keyword: '', department: '', page: 1 })
  fetchData()
}
async function fetchData() {
  loading.value = true
  try {
    const response = await listPersonnel(query)
    tableData.value = response.data.items || []
    total.value = response.data.total || 0
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const [definitionResponse, departmentResponse] = await Promise.all([
    listFieldDefs('', 'master'),
    getDepartments(),
  ])
  definitions.value = definitionResponse.data || []
  departments.value = departmentResponse.data || []
  await fetchData()
})
</script>

<style scoped>
.header-row { display: flex; justify-content: space-between; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
