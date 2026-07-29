<template>
  <div>
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
      <h2>{{ pageTitle }}</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card shadow="hover" v-loading="loading">
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
import { ElMessage } from 'element-plus'
import ChangeDiff from '../../components/ChangeDiff.vue'

const route = useRoute()
const router = useRouter()

const mode = computed(() => route.meta.mode || 'view')
const isView = computed(() => mode.value === 'view')
const isEdit = computed(() => mode.value === 'edit')
const isCreate = computed(() => mode.value === 'create')

const pageTitle = computed(() => {
  if (isCreate.value) return '新增人员'
  if (isEdit.value) return '编辑人员'
  return '人员详情'
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

onMounted(async () => {
  try {
    const res = await getDepartments()
    departments.value = res.data || []
  } catch { /* ignore */ }

  if (!isCreate.value) {
    loading.value = true
    try {
      const id = route.params.id
      const res = await getPersonnel(id)
      Object.assign(form, res.data)
    } finally {
      loading.value = false
    }
  }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (isEdit.value) {
    // Compute diff and show confirmation dialog
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
        name: form.name,
        gender: form.gender,
        department: form.department,
        position: form.position,
        phone: form.phone,
        email: form.email,
      })
      ElMessage.success('变更已提交，请等待审批')
    } else {
      await createPersonnel({ ...form })
      ElMessage.success('人员已创建，请等待审批')
    }
    diffDialogVisible.value = false
    router.push('/personnel')
  } finally {
    submitting.value = false
  }
}
</script>
