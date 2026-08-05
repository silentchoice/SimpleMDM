<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MasterTypeInput } from '../../api/metadata'

const props = withDefaults(defineProps<{
  open: boolean
  saving: boolean
  error?: string
  onSaved?: (value: MasterTypeInput) => Promise<void> | void
}>(), { error: '', onSaved: undefined })
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

const code = ref('')
const name = ref('')
const validationError = ref('')
const submitting = ref(false)

function reset(): void {
  code.value = ''
  name.value = ''
  validationError.value = ''
}

watch(() => props.open, () => reset(), { immediate: true })

async function submit(): Promise<void> {
  validationError.value = ''
  if (!code.value.trim() || !name.value.trim()) {
    validationError.value = t('metadata.masterTypeDrawer.validation')
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
  <aside v-if="open" class="system-drawer" role="dialog" :aria-label="t('metadata.masterTypeDrawer.ariaLabel')">
    <h2>{{ t('metadata.masterTypeDrawer.title') }}</h2>
    <form @submit.prevent="submit">
      <label for="master-type-code">{{ t('metadata.templates.code') }}</label>
      <input id="master-type-code" v-model="code" name="code" :disabled="saving || submitting" />
      <label for="master-type-name">{{ t('metadata.templates.name') }}</label>
      <input id="master-type-name" v-model="name" name="name" :disabled="saving || submitting" />
      <p v-if="validationError || error" class="form-error" role="alert">{{ validationError || error }}</p>
      <div class="drawer-actions">
        <el-button data-testid="master-type-cancel" native-type="button" @click="emit('close')">{{ t('common.cancel') }}</el-button>
        <el-button native-type="submit" type="primary" :loading="saving || submitting">{{ saving || submitting ? t('common.saving') : t('common.save') }}</el-button>
      </div>
    </form>
  </aside>
</template>
