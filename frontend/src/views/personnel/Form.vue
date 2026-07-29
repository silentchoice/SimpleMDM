<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
      <h2>{{ pageTitle }}</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <!-- 主表 -->
    <el-card shadow="hover" v-loading="loading" style="margin-bottom: 20px;">
      <template #header><strong>部门主数据（主表 — 跨部门共享）</strong></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" :disabled="isView" style="max-width: 640px;">
        <el-form-item label="工号" prop="employee_code">
          <el-input v-model="form.employee_code" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-select v-model="form.department" placeholder="请选择部门" style="width: 100%;">
            <el-option v-for="d in departments" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="form.position" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>

        <el-form-item v-if="!isView">
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '提交审批' : '提交（需审批）' }}
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 子表（按可用 sub_type 展示，含已有记录 + 空字段组） -->
    <div v-if="!isCreate" v-loading="subLoading">
      <el-card shadow="hover" v-for="group in allSubGroups" :key="group.sub_type" style="margin-bottom: 20px;">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <strong>{{ group.sub_type }}（{{ group.fields.length }} 个字段 · {{ group.records.length }} 条记录）</strong>
            <el-tag v-if="!isOwnerDept" type="info" size="small">只读</el-tag>
            <el-button v-if="!isView && isOwnerDept" type="primary" size="small"
              @click="showSubDialog(group.sub_type, null)">添加记录</el-button>
          </div>
        </template>
        <el-table v-if="group.records.length > 0" :data="group.records" border stripe size="small">
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
        <el-empty v-else description="暂无记录" :image-size="60" />
      </el-card>
      <el-empty v-if="allSubGroups.length === 0" description="暂无子表字段定义，请先在「字段定义」中配置" :image-size="80" />
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

const form = reactive({
  employee_code: '',
  name: '',
  gender: '男',
  department: '',
  position: '',
  phone: '',
  email: '',
})

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
    const diff = {}
    for (const key of ['employee_code', 'name', 'gender', 'department', 'position', 'phone', 'email']) {
      if (form[key] !== original.data[key]) {
        const labelMap = {
          employee_code: '工号', name: '姓名', gender: '性别',
          department: '部门', position: '职位', phone: '手机号', email: '邮箱',
        }
        diff[labelMap[key]] = { old: original.data[key] || '(空)', new: form[key] || '(空)' }
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
    if (isEdit.value) {
      const id = route.params.id
      await updatePersonnel(id, {
        name: form.name, gender: form.gender, department: form.department,
        position: form.position, phone: form.phone, email: form.email,
      })
      ElMessage.success('变更已提交，请等待审批')
    } else {
      await createPersonnel({ ...form })
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

// 当前用户是否有该人员所在部门的编辑权限
const isOwnerDept = computed(() => {
  const perms = userStore.permissions || []
  return perms.some(p =>
    p.perm_type === 'EDIT' &&
    (p.scope_type === 'ALL' || p.scope_value === form.department)
  )
})

// 所有可用 sub_type（有记录 + 有字段定义但无记录的）
const allSubGroups = computed(() => {
  const recordGroups = {}
  for (const rec of subRecords.value) {
    const st = rec.sub_type
    if (!recordGroups[st]) recordGroups[st] = []
    recordGroups[st].push(rec)
  }
  // Merge with sub_types from field definitions
  const result = []
  const seen = new Set()
  for (const st of [...Object.keys(recordGroups), ...subTypes.value]) {
    if (seen.has(st)) continue
    seen.add(st)
    const records = recordGroups[st] || []
    const ownerDept = records[0]?.owner_dept || form.department
    let fields = allFieldDefs.value.filter(f =>
      f.sub_type === st && f.table_type === 'sub' &&
      (f.department === ownerDept || f.department === userStore.user?.department)
    )
    if (fields.length === 0) {
      const keys = Object.keys(parseJson(records[0]?.data_json || '{}'))
      fields = keys.map((k, i) => ({ id: 'fb-' + i, field_name: k, field_type: 'string', required: false }))
    }
    result.push({ sub_type: st, records, fields })
  }
  return result
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
  if (!form.department || form.department === userStore.user?.department) return
  const seenSubTypes = [...new Set(subRecords.value.map(r => r.sub_type))]
  if (seenSubTypes.length === 0) return
  try {
    const crossDefs = []
    for (const st of seenSubTypes) {
      try {
        const r = await getFieldDefsByType(st, form.department)
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

  await loadFieldDefs()

  if (!isCreate.value) {
    loading.value = true
    try {
      const id = route.params.id
      const res = await getPersonnel(id)
      Object.assign(form, res.data)
      await loadSubRecords()
      await loadCrossDeptFieldDefs()
    } finally {
      loading.value = false
    }
  }
})
</script>
