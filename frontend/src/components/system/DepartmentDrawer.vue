<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Department, DepartmentInput } from '../../api/system'

const props = withDefaults(defineProps<{
  open: boolean
  department: Department | null
  saving: boolean
  error?: string
  onSaved?: (value: DepartmentInput) => Promise<void> | void
}>(), { error: '', onSaved: undefined })
const emit = defineEmits<{ close: [] }>()

const code = ref('')
const name = ref('')
const validationError = ref('')
const submitting = ref(false)

function reset(): void {
  code.value = props.department?.code ?? ''
  name.value = props.department?.name ?? ''
  validationError.value = ''
}

watch(() => props.open, (open) => { if (open) reset(); else reset() }, { immediate: true })
watch(() => props.department, () => { if (props.open) reset() })

async function submit(): Promise<void> {
  validationError.value = ''
  if (!code.value.trim() || !name.value.trim()) {
    validationError.value = 'Code and name are required'
    return
  }
  if (submitting.value || props.saving) return
  submitting.value = true
  try {
    await props.onSaved?.({ code: code.value.trim(), name: name.value.trim() })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <aside v-if="open" class="system-drawer" role="dialog" :aria-label="department ? 'Edit department' : 'Create department'">
    <h2>{{ department ? 'Edit department' : 'Create department' }}</h2>
    <form @submit.prevent="submit">
      <label for="department-code">Code</label>
      <input id="department-code" v-model="code" name="code" :disabled="saving || submitting" />
      <label for="department-name">Name</label>
      <input id="department-name" v-model="name" name="name" :disabled="saving || submitting" />
      <p v-if="validationError || error" class="form-error" role="alert">{{ validationError || error }}</p>
      <div class="drawer-actions">
        <el-button data-testid="department-cancel" native-type="button" @click="emit('close')">Cancel</el-button>
        <el-button native-type="submit" type="primary" :loading="saving || submitting">{{ saving || submitting ? 'Saving…' : 'Save' }}</el-button>
      </div>
    </form>
  </aside>
</template>
