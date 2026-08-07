<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { FieldDefinition } from '../../api/metadata'
import type { HistorySnapshot } from '../../api/records'
import RecordStatusTag from './RecordStatusTag.vue'

const props = defineProps<{
  snapshots: HistorySnapshot[]
  fields: FieldDefinition[]
}>()
const { t } = useI18n()

function displayValue(snapshot: HistorySnapshot, code: string): string {
  const value = snapshot.masterValues[code]
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return value == null ? '—' : String(value)
}
</script>

<template>
  <div class="record-history">
    <article
      v-for="snapshot in snapshots.slice(0, 3)"
      :key="snapshot.version"
      class="record-history__version"
      :data-testid="`history-version-${snapshot.version}`"
    >
      <header class="record-history__header">
        <h3>{{ t('record.history.version', { version: snapshot.version }) }}</h3>
        <RecordStatusTag :status="snapshot.status" />
      </header>
      <dl class="record-history__grid">
        <template v-for="field in fields" :key="field.id">
          <dt>{{ field.displayName }}</dt>
          <dd>{{ displayValue(snapshot, field.code) }}</dd>
        </template>
      </dl>
    </article>
  </div>
</template>
