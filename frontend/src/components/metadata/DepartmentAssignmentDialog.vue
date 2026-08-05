<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Department } from '../../api/system'
import type { MasterType } from '../../api/metadata'

const props = withDefaults(defineProps<{
  open: boolean
  masterType: MasterType | null
  departments: Department[]
  saving: boolean
  error?: string
  onAssigned?: (departmentId: number) => Promise<void> | void
}>(), { error: '', onAssigned: undefined })
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

const departmentId = ref('')
const validationError = ref('')
const submitting = ref(false)
const activeDepartments = computed(() => props.departments.filter((department) => department.status === 'ACTIVE'))

function reset(): void {
  departmentId.value = ''
  validationError.value = ''
}

watch(() => props.open, () => reset(), { immediate: true })
watch(() => props.masterType, () => { if (props.open) reset() })

async function submit(): Promise<void> {
  validationError.value = ''
  if (!departmentId.value) {
    validationError.value = t('metadata.assignment.validation')
    return
  }
  if (submitting.value || props.saving) return
  submitting.value = true
  try {
    await props.onAssigned?.(Number(departmentId.value))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <aside v-if="open" class="system-drawer" role="dialog" :aria-label="t('metadata.assignment.ariaLabel', { name: masterType?.name ?? t('metadata.assignment.templateFallback') })">
    <h2>{{ t('metadata.assignment.title', { name: masterType?.name ?? t('metadata.assignment.templateFallback') }) }}</h2>
    <form @submit.prevent="submit">
      <label for="assignment-department">{{ t('metadata.assignment.department') }}</label>
      <select id="assignment-department" v-model="departmentId" name="departmentId" :disabled="saving || submitting">
        <option value="">{{ t('metadata.assignment.selectDepartment') }}</option>
        <option v-for="department in activeDepartments" :key="department.id" :value="department.id.toString()">{{ department.name }}</option>
      </select>
      <p v-if="validationError || error" class="form-error" role="alert">{{ validationError || error }}</p>
      <div class="drawer-actions">
        <el-button data-testid="assignment-cancel" native-type="button" @click="emit('close')">{{ t('common.cancel') }}</el-button>
        <el-button native-type="submit" type="primary" :loading="saving || submitting">{{ saving || submitting ? t('metadata.assignment.assigning') : t('metadata.assignment.assign') }}</el-button>
      </div>
    </form>
  </aside>
</template>
