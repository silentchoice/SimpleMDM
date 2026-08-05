<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { approveApprovalTask, rejectApprovalTask, type ApprovalStatus } from '../../api/approval'
import type { ApiError } from '../../types'

const props = defineProps<{ taskId: number, status: ApprovalStatus }>()
const { t } = useI18n()
const emit = defineEmits<{ approved: [], rejected: [], conflict: [error: ApiError] }>()
const comment = ref('')
const reason = ref('')
const pending = ref(false)
const error = ref('')
const canAct = computed(() => props.status === 'PENDING' && !pending.value)
function message(value: ApiError): string { return value.requestId ? t('common.apiError', { message: value.message, requestId: t('common.requestId', { id: value.requestId }) }) : value.message }
async function run(action: () => Promise<void>, event: 'approved' | 'rejected'): Promise<void> {
  pending.value = true; error.value = ''
  try { await action(); if (event === 'approved') emit('approved'); else emit('rejected') } catch (cause) {
    const apiError = cause as ApiError
    error.value = message(apiError)
    if (apiError.status === 409) emit('conflict', apiError)
  } finally { pending.value = false }
}
function approve(): Promise<void> { return run(() => approveApprovalTask(props.taskId, comment.value.trim() || undefined), 'approved') }
function reject(): Promise<void> {
  if (!reason.value.trim()) { error.value = t('approval.actions.rejectionRequired'); return Promise.resolve() }
  return run(() => rejectApprovalTask(props.taskId, reason.value.trim()), 'rejected')
}
</script>

<template>
  <section v-if="status === 'PENDING'" class="approval-actions" :aria-label="t('approval.actions.ariaLabel')">
    <label>{{ t('approval.actions.approvalComment') }}<textarea v-model="comment" name="approveComment" maxlength="1000" /></label>
    <el-button data-testid="approve-button" type="success" :disabled="!canAct" :loading="pending" @click="approve">{{ t('approval.actions.approve') }}</el-button>
    <label>{{ t('approval.actions.rejectionReason') }}<textarea v-model="reason" name="rejectReason" maxlength="1000" required /></label>
    <el-button data-testid="reject-button" type="danger" :disabled="!canAct || !reason.trim()" :loading="pending" @click="reject">{{ t('approval.actions.reject') }}</el-button>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.approval-actions { display: grid; gap: 10px; margin-top: 20px; }
label { display: grid; gap: 4px; }
textarea { min-height: 70px; padding: 8px; font: inherit; }
</style>
