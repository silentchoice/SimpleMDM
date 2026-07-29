<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
      <h2>{{ pageTitle }}</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <!-- 主表（动态字段，从 master field definitions 渲染） -->
    <el-card shadow="hover" v-loading="loading" style="margin-bottom: 20px;">
      <template #header><strong>部门主数据（主表 — 跨部门共享）</strong></template>
      <el-form ref="formRef" :model="formData" label-width="120px" :disabled="isView" style="max-width: 640px;">
        <el-form-item v-for="fd in masterFieldDefs" :key="fd.id"
          :label="fd.field_name" :required="fd.required">
          <!-- 部门用下拉 -->
          <el-select v-if="fd.field_name === '部门'" v-model="formData[fd.field_name]"
            placeholder="请选择部门" style="width: 100%;">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
          <!-- 性别用单选 -->
          <el-radio-group v-else-if="fd.field_name === '性别'" v-model="formData[fd.field_name]">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
          <!-- 工号在编辑时禁用 -->
          <el-input v-else v-model="formData[fd.field_name]"
            :disabled="fd.field_name === '工号' && isEdit"
            :type="fd.field_type === 'number' ? 'number' : 'text'" />
        </el-form-item>

        <el-form-item v-if="!isView">
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '提交审批' : '提交（需审批）' }}
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 子表（按 sub_type 分组，每组建表） -->
    <div v-if="!isCreate" v-loading="subLoading">
      <el-card shadow="hover" v-for="group in groupedSubRecords" :key="group.sub_type" style="margin-bottom: 20px;">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <strong>{{ group.sub_type }}（{{ group.fields.length }} 个字段 · {{ group.records.length }} 条记录）</strong>
            <el-button v-if="!isView && isOwnerDept" type="primary" size="small"
              @click="showSubDialog(group.sub_type, null)">添加记录</el-button>
          </div>
        </template>
        <el-table :data="group.records" border stripe size="small">
          <!-- 动态列：按字段定义渲染 -->
          <el-table-column v-for="fd in group.fields" :key="fd.id"
            :prop="fd.field_name" :label="fd.field_name" min-width="120">
            <template #default="{ row }">
              {{ parseJson(row.data_json)[fd.field_name] || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="owner_dept" label="所属部门" width="100" />
          <el-table-column label="可见性" width="100">
            <template #default="{ row }">
              <el-tag :type="visibilityType(row.visibility)" size="small">
                {{ visibilityLabel(row.visibility) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" v-if="!isView && isOwnerDept">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="showSubDialog(group.sub_type, row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
      <el-empty v-if="groupedSubRecords.length === 0" description="暂无子表数据" :image-size="80" />
    </div>

    <!-- 子表编辑弹窗（动态表单） -->
    <el-dialog v-model="subDialogVisible"
      :title="subEditing ? '编辑记录' : '添加 ' + subForm.sub_type + ' 记录'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="数据类型" v-if="!subEditing">
          <el-select v-model="subForm.sub_type" placeholder="选择数据类型" @change="onSubTypeChange" style="width: 100%;">
            <el-option v-for="st in subTypes" :key="st" :label="st" :value="st" />
          </el-select>
        </el-form-item>
        <el-form-item v-for="fd in currentFieldDefs" :key="fd.id"
          :label="fd.field_name" :required="fd.required">
          <el-input v-model="subForm.fields[fd.field_name]"
            :placeholder="fd.required ? '必填' : '可选'"
            :type="fd.field_type === 'number' ? 'number' : 'text'" />
        </el-form-item>
        <el-form-item label="可见性" v-if="subEditing">
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

    <!-- Diff dialog for edit mode -->
    <el-dialog v-model="diffDialogVisible" title="变更确认" width="600px">
      <p style="margin-bottom: 16px; color: #909399;">请确认以下变更，提交后将进入审批流程：</p>
      <ChangeDiff :change-data="diffData" />
      <template #footer>
        <el-button @click="diffDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSubmit" :loading="submitting">确认提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPersonnel, createPersonnel, updatePersonnel, getDepartments } from '../../api/personnel'
import { listSub, createSub, updateSub } from '../../api/personnelSub'
import { listFieldDefs, listSubTypes, getFieldDefsByType } from '../../api/deptFields'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'
import ChangeDiff from '../../components/ChangeDiff.vue'

const route = useRoute()
const router = useRouter()

const mode = computed(() => route.meta.mode || 'view')
const isView = computed(() => mode.value === 'view')
const isEdit = computed(() => mode.value === 'edit')
const isCreate = computed(() => mode.value === 'create')

const pageTitle = computed(() => {
  if (isCreate.value) return '新增主数据'
  if (isEdit.value) return '编辑主数据'
  return '主数据详情'
})

const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const diffDialogVisible = ref(false)
const diffData = ref({})
const departments = ref([])

// Master field definitions → drives the main form
const masterFieldDefs = computed(() =>
  allFieldDefs.value.filter(f => f.table_type === 'master').sort((a, b) => a.sort_order - b.sort_order)
)

// Map Chinese field_name → English DTO key
const FIELD_MAP = {
  '工号': 'employee_code', '姓名': 'name', '性别': 'gender',
  '部门': 'department', '职位': 'position', '手机号': 'phone', '邮箱': 'email',
}

const formData = reactive({})

function initFormData() {
  for (const fd of masterFieldDefs.value) {
    if (!(fd.field_name in formData)) formData[fd.field_name] = ''
  }
  if (!formData['性别']) formData['性别'] = '男'
}

function formDataToDTO() {
  const dto = {}
  for (const [cn, en] of Object.entries(FIELD_MAP)) {
    if (formData[cn] !== undefined) dto[en] = formData[cn]
  }
  return dto
}

function fillFormDataFromDTO(dto) {
  for (const [cn, en] of Object.entries(FIELD_MAP)) {
    if (dto[en] !== undefined) formData[cn] = dto[en]
  }
}

const rules = {
  employee_code: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  department: [{ required: true, message: '请选择部门', trigger: 'change' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (isEdit.value) {
    const id = route.params.id
    const original = await getPersonnel(id)
    const origDto = original.data
    const newDto = formDataToDTO()
    const diff = {}
    for (const [cn, en] of Object.entries(FIELD_MAP)) {
      const oldVal = origDto[en]
      const newVal = newDto[en]
      if (oldVal !== newVal) {
        diff[cn] = { old: oldVal || '(空)', new: newVal || '(空)' }
      }
    }
    if (Object.keys(diff).length === 0) {
      ElMessage.info('没有变更需要提交')
      return
    }
    diffData.value = diff
    diffDialogVisible.value = true
  } else {
    await confirmSubmit()
  }
}

async function confirmSubmit() {
  submitting.value = true
  try {
    const dto = formDataToDTO()
    if (isEdit.value) {
      await updatePersonnel(route.params.id, dto)
      ElMessage.success('变更已提交，请等待审批')
    } else {
      await createPersonnel(dto)
      ElMessage.success('已提交，请等待审批')
    }
    diffDialogVisible.value = false
    router.push('/personnel')
  } finally {
    submitting.value = false
  }
}

// ── 子表逻辑 (动态字段) ──
const userStore = useUserStore()
const subRecords = ref([])
const subLoading = ref(false)
const subDialogVisible = ref(false)
const subSaving = ref(false)
const subEditing = ref(null)
const subTypes = ref([])
const allFieldDefs = ref([])          // 当前部门的所有字段定义
const currentFieldDefs = computed(() => {
  if (!subForm.sub_type) return []
  return allFieldDefs.value.filter(f => f.sub_type === subForm.sub_type)
})

const subForm = reactive({
  sub_type: '',
  fields: {},        // { field_name: value }
  visibility: 'private',
})

// 当前用户是否与人员同部门 → 可编辑子表
const isOwnerDept = computed(() => {
  return userStore.user?.department === formData['部门']
})

// 子记录按 sub_type 分组，每组带字段定义
const groupedSubRecords = computed(() => {
  const groups = {}
  for (const rec of subRecords.value) {
    const st = rec.sub_type
    if (!groups[st]) groups[st] = []
    groups[st].push(rec)
  }
  return Object.entries(groups).map(([subType, records]) => {
    const ownerDept = records[0]?.owner_dept || formData['部门']
    // 从已加载的字段定义中匹配（可能跨部门）
    let fields = allFieldDefs.value.filter(f =>
      f.sub_type === subType && (f.department === ownerDept || f.table_type === 'master')
    )
    // Fallback: 从数据 JSON keys 生成虚拟列
    if (fields.length === 0) {
      const keys = Object.keys(parseJson(records[0]?.data_json || '{}'))
      fields = keys.map((k, i) => ({ id: 'fb-' + i, field_name: k, field_type: 'string', required: false }))
    }
    return { sub_type: subType, records, fields }
  })
})

function parseJson(jsonStr) {
  try { return JSON.parse(jsonStr) || {} } catch { return {} }
}

function visibilityLabel(v) {
  const map = { private: '仅本部门', pending_share: '审批共享中', shared: '已共享' }
  return map[v] || v
}

function visibilityType(v) {
  const map = { private: 'info', pending_share: 'warning', shared: 'success' }
  return map[v] || 'info'
}

function onSubTypeChange(subType) {
  subForm.fields = {}
}

function showSubDialog(subType, row) {
  if (row) {
    subEditing.value = row
    subForm.sub_type = row.sub_type
    subForm.fields = { ...parseJson(row.data_json) }
    subForm.visibility = row.visibility
  } else {
    subEditing.value = null
    subForm.sub_type = subType || ''
    subForm.fields = {}
    subForm.visibility = 'private'
  }
  subDialogVisible.value = true
}

async function saveSubRecord() {
  subSaving.value = true
  try {
    const id = route.params.id
    const dataJson = JSON.stringify(subForm.fields)
    if (subEditing.value) {
      await updateSub(id, subEditing.value.id, {
        dataJson,
        subType: subForm.sub_type,
        visibility: subForm.visibility,
      })
      ElMessage.success('子表数据已更新')
    } else {
      await createSub(id, {
        subType: subForm.sub_type,
        dataJson,
      })
      ElMessage.success('子表数据已创建')
    }
    subDialogVisible.value = false
    await loadSubRecords()
  } finally {
    subSaving.value = false
  }
}

async function loadSubRecords() {
  subLoading.value = true
  try {
    const id = route.params.id
    const res = await listSub(id)
    subRecords.value = res.data || []
  } catch { /* ignore */ } finally {
    subLoading.value = false
  }
}

async function loadFieldDefs() {
  try {
    const [defRes, typesRes, masterRes] = await Promise.all([
      listFieldDefs('', 'sub'),
      listSubTypes('sub'),
      listFieldDefs('', 'master'),
    ])
    allFieldDefs.value = [...(defRes.data || []), ...(masterRes.data || [])]
    subTypes.value = typesRes.data || []
  } catch { /* ignore */ }
}

async function loadCrossDeptFieldDefs() {
  if (!formData['部门'] || formData['部门'] === userStore.user?.department) return
  const seenSubTypes = [...new Set(subRecords.value.map(r => r.sub_type))]
  if (seenSubTypes.length === 0) return
  try {
    const crossDefs = []
    for (const st of seenSubTypes) {
      try {
        const r = await getFieldDefsByType(st, formData['部门'])
        if (r.data) crossDefs.push(...r.data)
      } catch { /* skip */ }
    }
    // 合并去重
    const existingIds = new Set(allFieldDefs.value.map(f => f.id))
    for (const f of crossDefs) {
      if (!existingIds.has(f.id)) allFieldDefs.value.push(f)
    }
  } catch { /* ignore */ }
}

onMounted(async () => {
  try {
    const res = await getDepartments()
    departments.value = res.data || []
  } catch { /* ignore */ }

  initFormData()
  await loadFieldDefs()
  // Re-init after field defs loaded
  initFormData()

  if (!isCreate.value) {
    loading.value = true
    try {
      const id = route.params.id
      const res = await getPersonnel(id)
      fillFormDataFromDTO(res.data)
      await loadSubRecords()
      await loadCrossDeptFieldDefs()
    } finally {
      loading.value = false
    }
  }
})
</script>
