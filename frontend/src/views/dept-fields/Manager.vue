<template>
  <div>
    <div class="page-header">
      <h2>字段定义管理 — {{ userStore.user?.department }}</h2>
      <p style="color: #909399; font-size: 14px;">定义本部门子表的字段结构，定义后将用于人员详情页的扩展数据录入</p>
    </div>

    <!-- 按 sub_type 分组 -->
    <el-card shadow="hover" v-for="group in groupedFields" :key="group.subType" style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>
            <strong>{{ group.subType }}</strong>
            <span style="color: #909399; margin-left: 8px;">{{ group.fields.length }} 个字段</span>
          </span>
          <el-button type="primary" size="small" @click="showDialog(group.subType, null)">添加字段</el-button>
        </div>
      </template>
      <el-table :data="group.fields" border stripe size="small">
        <el-table-column prop="field_name" label="字段名" width="180" />
        <el-table-column prop="field_type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.field_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="必填" width="80">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'danger' : 'info'" size="small">
              {{ row.required ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort_order" label="排序" width="70" />
        <el-table-column prop="created_by_name" label="创建人" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDialog(group.subType, row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空提示 -->
      <el-empty v-if="group.fields.length === 0" description="暂无字段，点击「添加字段」开始定义" :image-size="60" />
    </el-card>

    <!-- 新建 sub_type 按钮 -->
    <div style="margin-top: 16px;" v-if="Object.keys(groupedFields).length > 0 || true">
      <el-button type="success" @click="showNewSubTypeDialog">新建字段组</el-button>
    </div>

    <!-- 新增/编辑字段弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingField ? '编辑字段' : '添加字段'" width="460px">
      <el-form label-width="100px">
        <el-form-item label="字段组" v-if="!editingField && !newSubTypeMode">
          <el-select v-model="dialogForm.sub_type" style="width: 100%;">
            <el-option v-for="st in subTypes" :key="st" :label="st" :value="st" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段组名" v-if="newSubTypeMode">
          <el-input v-model="newSubTypeName" placeholder="如: salary, contract" />
        </el-form-item>
        <el-form-item label="字段名" required>
          <el-input v-model="dialogForm.field_name" placeholder="如: 基本工资" />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="dialogForm.field_type">
            <el-option label="文本 (string)" value="string" />
            <el-option label="数字 (number)" value="number" />
            <el-option label="日期 (date)" value="date" />
            <el-option label="下拉 (select)" value="select" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="dialogForm.required" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialogForm.sort_order" :min="0" :max="99" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveField" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { listFieldDefs, listSubTypes, createFieldDef, updateFieldDef } from '../../api/deptFields'
import { useUserStore } from '../../stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()

const allFields = ref([])
const subTypes = ref([])
const saving = ref(false)
const dialogVisible = ref(false)
const editingField = ref(null)
const newSubTypeMode = ref(false)
const newSubTypeName = ref('')

const dialogForm = reactive({
  sub_type: '',
  field_name: '',
  field_type: 'string',
  required: false,
  sort_order: 0,
})

const groupedFields = computed(() => {
  const groups = {}
  for (const f of allFields.value) {
    if (!groups[f.sub_type]) groups[f.sub_type] = []
    groups[f.sub_type].push(f)
  }
  return Object.entries(groups).map(([subType, fields]) => ({ subType, fields }))
})

function showNewSubTypeDialog() {
  newSubTypeMode.value = true
  editingField.value = null
  dialogForm.sub_type = ''
  dialogForm.field_name = ''
  dialogForm.field_type = 'string'
  dialogForm.required = false
  dialogForm.sort_order = 0
  newSubTypeName.value = ''
  dialogVisible.value = true
}

function showDialog(subType, field) {
  newSubTypeMode.value = false
  if (field) {
    editingField.value = field
    dialogForm.sub_type = field.sub_type
    dialogForm.field_name = field.field_name
    dialogForm.field_type = field.field_type
    dialogForm.required = field.required
    dialogForm.sort_order = field.sort_order
  } else {
    editingField.value = null
    dialogForm.sub_type = subType
    dialogForm.field_name = ''
    dialogForm.field_type = 'string'
    dialogForm.required = false
    dialogForm.sort_order = 0
  }
  dialogVisible.value = true
}

async function saveField() {
  if (!dialogForm.field_name) { ElMessage.warning('请输入字段名'); return }
  saving.value = true
  try {
    const subType = newSubTypeMode.value ? newSubTypeName.value : dialogForm.sub_type
    if (!subType) { ElMessage.warning('请选择或输入字段组名'); return }

    if (editingField.value) {
      await updateFieldDef(editingField.value.id, { ...dialogForm })
      ElMessage.success('字段已更新')
    } else {
      await createFieldDef({ ...dialogForm, sub_type: subType })
      ElMessage.success('字段已创建')
    }
    dialogVisible.value = false
    await loadFields()
  } finally {
    saving.value = false
  }
}

async function loadFields() {
  try {
    const [defRes, typesRes] = await Promise.all([listFieldDefs(), listSubTypes()])
    allFields.value = defRes.data || []
    subTypes.value = typesRes.data || []
  } catch { /* ignore */ }
}

onMounted(loadFields)
</script>
