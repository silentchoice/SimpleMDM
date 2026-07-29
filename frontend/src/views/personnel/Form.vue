<template>
  <div>
    <div class="page-header page-header-row">
      <h2>{{ pageTitle }}</h2>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card shadow="hover" v-loading="loading" style="margin-bottom: 20px;">
      <template #header><strong>部门主数据（动态主表）</strong></template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        :disabled="isView"
        style="max-width: 680px;"
      >
        <el-form-item label="所属部门" prop="owner_dept" required>
          <el-select v-model="form.owner_dept" placeholder="请选择部门" style="width: 100%;">
            <el-option v-for="department in departments" :key="department"
              :label="department" :value="department" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-for="definition in editableMasterDefs"
          :key="definition.field_key"
          :label="definition.field_name"
          :prop="`data.${definition.field_key}`"
          :required="definition.required"
        >
          <DynamicFieldInput
            :definition="definition"
            v-model="form.data[definition.field_key]"
          />
        </el-form-item>
        <el-form-item v-if="!isView">
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '提交审批' : '提交（需审批）' }}
          </el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="!isCreate" v-loading="subLoading">
      <el-card v-for="group in allSubGroups" :key="group.sub_type"
        shadow="hover" style="margin-bottom: 20px;">
        <template #header>
          <div class="page-header-row">
            <strong>{{ group.sub_type }}（{{ group.fields.length }} 个字段 · {{ group.records.length }} 条记录）</strong>
            <div>
              <el-tag v-if="!isOwnerDept" type="info" size="small">只读</el-tag>
              <el-button v-if="!isView && isOwnerDept" type="primary" size="small"
                @click="showSubDialog(group.sub_type)">添加记录</el-button>
            </div>
          </div>
        </template>
        <el-table v-if="group.records.length" :data="group.records" border stripe size="small">
          <el-table-column v-for="definition in group.fields" :key="definition.field_key"
            :label="definition.field_name" min-width="120">
            <template #default="{ row }">
              <DynamicFieldValue :definition="definition"
                :value="row.data?.[definition.field_key]" />
            </template>
          </el-table-column>
          <el-table-column prop="owner_dept" label="所属部门" width="120" />
          <el-table-column label="可见性" width="110">
            <template #default="{ row }">
              <el-tag :type="visibilityType(row.visibility)" size="small">
                {{ visibilityLabel(row.visibility) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="!isView && isOwnerDept" label="操作" width="80">
            <template #default="{ row }">
              <el-button type="primary" link size="small"
                @click="showSubDialog(group.sub_type, row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无记录" :image-size="60" />
      </el-card>
      <el-empty v-if="allSubGroups.length === 0"
        description="暂无子表字段定义，请先在「字段定义」中配置" :image-size="80" />
    </div>

    <el-dialog v-model="subDialogVisible"
      :title="subEditing ? '编辑记录' : `添加 ${subForm.sub_type} 记录`" width="540px">
      <el-form :model="subForm" label-width="110px">
        <el-form-item v-for="definition in currentSubDefs" :key="definition.field_key"
          :label="definition.field_name" :required="definition.required">
          <DynamicFieldInput :definition="definition"
            v-model="subForm.data[definition.field_key]" />
        </el-form-item>
        <el-form-item v-if="subEditing" label="可见性">
          <el-select v-model="subForm.visibility">
            <el-option label="仅本部门" value="private" />
            <el-option label="审批共享中" value="pending_share" />
            <el-option label="已共享" value="shared" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSubRecord" :loading="subSaving">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="diffDialogVisible" title="变更确认" width="620px">
      <p style="margin-bottom: 16px; color: #909399;">请确认以下变更，提交后将进入审批流程：</p>
      <ChangeDiff :change-data="diffData" :definitions="masterFieldDefs" />
      <template #footer>
        <el-button @click="diffDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSubmit" :loading="submitting">确认提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPersonnel, getDepartments, getPersonnel, updatePersonnel } from '../../api/personnel'
import { createSub, listSub, updateSub } from '../../api/personnelSub'
import { getFieldDefsByType, listFieldDefs, listSubTypes } from '../../api/deptFields'
import { useUserStore } from '../../stores/user'
import { buildInitialData, normalizePayload, sortedDefinitions } from '../../utils/dynamicFields'
import DynamicFieldInput from '../../components/DynamicFieldInput.vue'
import DynamicFieldValue from '../../components/DynamicFieldValue.vue'
import ChangeDiff from '../../components/ChangeDiff.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mode = computed(() => route.meta.mode || 'view')
const isView = computed(() => mode.value === 'view')
const isEdit = computed(() => mode.value === 'edit')
const isCreate = computed(() => mode.value === 'create')
const pageTitle = computed(() => isCreate.value ? '新增主数据' : isEdit.value ? '编辑主数据' : '主数据详情')

const formRef = ref()
const form = reactive({ owner_dept: '', data: {}, version: null })
const original = ref({ owner_dept: '', data: {} })
const departments = ref([])
const masterFieldDefs = ref([])
const allSubFieldDefs = ref([])
const subTypes = ref([])
const subRecords = ref([])
const loading = ref(false)
const subLoading = ref(false)
const submitting = ref(false)
const diffDialogVisible = ref(false)
const diffData = ref({})

const editableMasterDefs = computed(() =>
  sortedDefinitions(masterFieldDefs.value.filter(definition => !definition.system_field))
)
const rules = computed(() => {
  const result = {
    owner_dept: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  }
  for (const definition of editableMasterDefs.value.filter(field => field.required)) {
    const choice = ['select', 'radio'].includes(definition.field_type)
    result[`data.${definition.field_key}`] = [{
      required: true,
      message: `${choice ? '请选择' : '请输入'}${definition.field_name}`,
      trigger: choice ? 'change' : 'blur',
    }]
  }
  return result
})
const isOwnerDept = computed(() => (userStore.permissions || []).some(permission =>
  permission.perm_type === 'EDIT' &&
  (permission.scope_type === 'ALL' || permission.scope_value === form.owner_dept)
))

const allSubGroups = computed(() => {
  const recordsByType = Object.groupBy
    ? Object.groupBy(subRecords.value, record => record.sub_type)
    : subRecords.value.reduce((groups, record) => {
        ;(groups[record.sub_type] ||= []).push(record)
        return groups
      }, {})
  return [...new Set([...subTypes.value, ...Object.keys(recordsByType)])].map(subType => ({
    sub_type: subType,
    records: recordsByType[subType] || [],
    fields: sortedDefinitions(allSubFieldDefs.value.filter(field => field.sub_type === subType)),
  }))
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!isEdit.value) return confirmSubmit()
  const nextData = normalizePayload(editableMasterDefs.value, form.data)
  const diff = {}
  if (original.value.owner_dept !== form.owner_dept) {
    diff.owner_dept = { old: original.value.owner_dept, new: form.owner_dept }
  }
  const keys = new Set([...Object.keys(original.value.data || {}), ...Object.keys(nextData)])
  for (const key of keys) {
    if ((original.value.data || {})[key] !== nextData[key]) {
      diff[key] = { old: (original.value.data || {})[key], new: nextData[key] }
    }
  }
  if (!Object.keys(diff).length) return ElMessage.info('没有变更需要提交')
  diffData.value = diff
  diffDialogVisible.value = true
}

async function confirmSubmit() {
  submitting.value = true
  try {
    const payload = {
      owner_dept: form.owner_dept,
      data: normalizePayload(editableMasterDefs.value, form.data),
      version: form.version,
    }
    if (isEdit.value) await updatePersonnel(route.params.id, payload)
    else await createPersonnel(payload)
    ElMessage.success(isEdit.value ? '变更已提交，请等待审批' : '已提交，请等待审批')
    router.push('/personnel')
  } finally {
    submitting.value = false
  }
}

const subDialogVisible = ref(false)
const subEditing = ref(null)
const subSaving = ref(false)
const subForm = reactive({ sub_type: '', data: {}, visibility: 'private' })
const currentSubDefs = computed(() =>
  sortedDefinitions(allSubFieldDefs.value.filter(field => field.sub_type === subForm.sub_type))
)

function showSubDialog(subType, row = null) {
  subEditing.value = row
  subForm.sub_type = subType
  subForm.data = buildInitialData(currentDefinitions(subType), row?.data || {})
  subForm.visibility = row?.visibility || 'private'
  subDialogVisible.value = true
}

function currentDefinitions(subType) {
  return allSubFieldDefs.value.filter(field => field.sub_type === subType)
}

async function saveSubRecord() {
  for (const definition of currentSubDefs.value) {
    const value = subForm.data[definition.field_key]
    if (definition.required && (value === '' || value === null || value === undefined)) {
      return ElMessage.warning(`请填写${definition.field_name}`)
    }
  }
  subSaving.value = true
  try {
    const payload = {
      subType: subForm.sub_type,
      data: normalizePayload(currentSubDefs.value, subForm.data),
      visibility: subForm.visibility,
      version: subEditing.value?.version,
    }
    if (subEditing.value) await updateSub(route.params.id, subEditing.value.id, payload)
    else await createSub(route.params.id, payload)
    ElMessage.success(subEditing.value ? '子表数据已更新' : '子表数据已创建')
    subDialogVisible.value = false
    await loadSubRecords()
  } finally {
    subSaving.value = false
  }
}

function visibilityLabel(value) {
  return ({ private: '仅本部门', pending_share: '审批共享中', shared: '已共享' })[value] || value
}
function visibilityType(value) {
  return ({ private: 'info', pending_share: 'warning', shared: 'success' })[value] || 'info'
}

async function loadSubRecords() {
  subLoading.value = true
  try {
    const response = await listSub(route.params.id)
    subRecords.value = response.data || []
  } finally {
    subLoading.value = false
  }
}

async function loadDefinitions() {
  const [masterResponse, subResponse, typeResponse] = await Promise.all([
    listFieldDefs('', 'master'),
    listFieldDefs('', 'sub'),
    listSubTypes('sub'),
  ])
  masterFieldDefs.value = masterResponse.data || []
  allSubFieldDefs.value = subResponse.data || []
  subTypes.value = typeResponse.data || []
}

async function loadCrossDepartmentDefinitions() {
  if (!form.owner_dept || form.owner_dept === userStore.user?.department) return
  const definitions = []
  for (const subType of new Set(subRecords.value.map(record => record.sub_type))) {
    const response = await getFieldDefsByType(subType, form.owner_dept)
    definitions.push(...(response.data || []))
  }
  const known = new Set(allSubFieldDefs.value.map(field => `${field.department}:${field.field_key}`))
  for (const definition of definitions) {
    const key = `${definition.department}:${definition.field_key}`
    if (!known.has(key)) allSubFieldDefs.value.push(definition)
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const departmentResponse = await getDepartments()
    departments.value = departmentResponse.data || []
    await loadDefinitions()
    if (isCreate.value) {
      form.data = buildInitialData(editableMasterDefs.value)
      form.owner_dept = userStore.user?.department || ''
      return
    }
    const response = await getPersonnel(route.params.id)
    form.owner_dept = response.data.owner_dept
    form.data = buildInitialData(editableMasterDefs.value, response.data.data || {})
    form.version = response.data.version
    original.value = {
      owner_dept: response.data.owner_dept,
      data: { ...(response.data.data || {}) },
    }
    await loadSubRecords()
    await loadCrossDepartmentDefinitions()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
