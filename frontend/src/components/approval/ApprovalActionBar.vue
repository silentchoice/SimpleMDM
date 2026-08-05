<script setup lang="ts">
import { computed, ref } from 'vue'
import { approveApprovalTask, rejectApprovalTask, type ApprovalStatus } from '../../api/approval'
import type { ApiError } from '../../types'

const props = defineProps<{ taskId: number, status: ApprovalStatus }>()
const emit = defineEmits<{ approved: [], rejected: [], conflict: [error: ApiError] }>()
const comment = ref('')
const reason = ref('')
const pending = ref(false)
const error = ref('')
const canAct = computed(() => props.status === 'PENDING' && !pending.value)
function message(value: ApiError): string { return value.requestId ? `${value.message} (Request ID: ${value.requestId})` : value.message }
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
  if (!reason.value.trim()) { error.value = 'Rejection reason is required'; return Promise.resolve() }
  return run(() => rejectApprovalTask(props.taskId, reason.value.trim()), 'rejected')
}
</script>

<template>
  <section v-if="status === 'PENDING'" class="approval-actions" aria-label="Approval actions">
    <label>Approval comment (optional)<textarea v-model="comment" name="approveComment" maxlength="1000" /></label>
    <el-button data-testid="approve-button" type="success" :disabled="!canAct" :loading="pending" @click="approve">Approve</el-button>
    <label>Rejection reason<textarea v-model="reason" name="rejectReason" maxlength="1000" required /></label>
    <el-button data-testid="reject-button" type="danger" :disabled="!canAct || !reason.trim()" :loading="pending" @click="reject">Reject</el-button>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.approval-actions { display: grid; gap: 10px; margin-top: 20px; }
label { display: grid; gap: 4px; }
textarea { min-height: 70px; padding: 8px; font: inherit; }
</style>
