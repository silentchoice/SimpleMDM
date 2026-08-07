<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { assignDepartment, createMasterType, getCodeRule, listMasterTypes, updateCodeRule, type CodeRuleResponse, type MasterType, type MasterTypeInput } from '../../api/metadata'
import { listDepartments, type Department } from '../../api/system'
import DepartmentAssignmentDialog from '../../components/metadata/DepartmentAssignmentDialog.vue'
import MasterTypeDrawer from '../../components/metadata/MasterTypeDrawer.vue'
import type { ApiError } from '../../types'

const masterTypes = ref<MasterType[]>([])
const { t } = useI18n()
const departments = ref<Department[]>([])
const selected = ref<MasterType | null>(null)
const drawerOpen = ref(false)
const assignmentOpen = ref(false)
const codeRuleOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const codeRulePattern = ref('')
const codeRulePreview = ref('')

function errorMessage(reason: unknown): string {
  const apiError = reason as ApiError
  return apiError.requestId ? t('common.apiError', { message: apiError.message, requestId: t('common.requestId', { id: apiError.requestId }) }) : apiError.message
}

async function load(): Promise<void> {
  loading.value = true
  try { masterTypes.value = await listMasterTypes() } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}

function create(): void { error.value = ''; drawerOpen.value = true }

async function save(body: MasterTypeInput): Promise<void> {
  error.value = ''
  saving.value = true
  try {
    await createMasterType(body)
    drawerOpen.value = false
    await load()
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}

async function openAssignment(masterType: MasterType): Promise<void> {
  error.value = ''
  selected.value = masterType
  try {
    departments.value = await listDepartments()
    assignmentOpen.value = true
  } catch (reason) { error.value = errorMessage(reason) }
}

async function assign(departmentId: number): Promise<void> {
  if (!selected.value) return
  error.value = ''
  saving.value = true
  try {
    await assignDepartment(selected.value.id, departmentId)
    assignmentOpen.value = false
    await load()
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}

async function openCodeRule(masterType: MasterType): Promise<void> {
  error.value = ''
  selected.value = masterType
  try {
    const rule = await getCodeRule(masterType.id)
    codeRulePattern.value = rule.pattern
    codeRulePreview.value = rule.preview
    codeRuleOpen.value = true
  } catch (reason) {
    if ((reason as ApiError).status === 404) {
      codeRulePattern.value = ''
      codeRulePreview.value = ''
      codeRuleOpen.value = true
    } else error.value = errorMessage(reason)
  }
}

async function saveCodeRule(): Promise<void> {
  if (!selected.value) return
  error.value = ''
  saving.value = true
  try {
    const rule: CodeRuleResponse = await updateCodeRule(selected.value.id, { pattern: codeRulePattern.value })
    codeRulePattern.value = rule.pattern
    codeRulePreview.value = rule.preview
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="content-view">
    <div class="view-heading"><div><h1>{{ t('metadata.templates.title') }}</h1><p>{{ t('metadata.templates.description') }}</p></div><el-button data-testid="master-type-create" type="primary" @click="create">{{ t('metadata.templates.create') }}</el-button></div>
    <p v-if="error && !drawerOpen && !assignmentOpen && !codeRuleOpen" class="form-error" role="alert">{{ error }}</p>
    <el-table :data="masterTypes" v-loading="loading">
      <el-table-column prop="code" :label="t('metadata.templates.code')" />
      <el-table-column prop="name" :label="t('metadata.templates.name')" />
      <el-table-column prop="status" :label="t('common.status')"><template #default="scope">{{ scope.row?.status ? t(`status.${scope.row.status}`) : '' }}</template></el-table-column>
      <el-table-column :label="t('common.actions')" width="280">
        <template #default="scope"><el-button :data-testid="`assign-department-${scope.row.id}`" text type="primary" @click="openAssignment(scope.row)">{{ t('metadata.templates.assignDepartment') }}</el-button><el-button :data-testid="`edit-code-rule-${scope.row.id}`" text type="primary" @click="openCodeRule(scope.row)">{{ t('metadata.templates.codeRule') }}</el-button></template>
      </el-table-column>
    </el-table>
    <MasterTypeDrawer :open="drawerOpen" :saving="saving" :error="error" :on-saved="save" @close="drawerOpen = false" />
    <DepartmentAssignmentDialog :open="assignmentOpen" :master-type="selected" :departments="departments" :saving="saving" :error="error" :on-assigned="assign" @close="assignmentOpen = false" />
    <aside v-if="codeRuleOpen" class="system-drawer" role="dialog" :aria-label="t('metadata.templates.codeRule')">
      <h2>{{ t('metadata.templates.codeRule') }}</h2>
      <label>{{ t('metadata.templates.pattern') }} <input v-model="codeRulePattern" name="pattern" /></label>
      <p>{{ codeRulePreview }}</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="drawer-actions"><el-button native-type="button" @click="codeRuleOpen = false">{{ t('common.cancel') }}</el-button><el-button data-testid="code-rule-save" native-type="button" type="primary" :loading="saving" :disabled="saving" @click="saveCodeRule">{{ t('common.save') }}</el-button></div>
    </aside>
  </section>
</template>
