<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { FieldDefinition, SubType } from '../../api/metadata'
import type { RecordDetail } from '../../api/records'

const props = defineProps<{
  snapshot: RecordDetail
  masterFields: FieldDefinition[]
  subTypes: SubType[]
  subFields: Record<number, FieldDefinition[]>
  testidPrefix?: string
}>()
const { t } = useI18n()

function displayValue(value: unknown): string {
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return value == null ? '—' : String(value)
}

function masterLabel(code: string): string {
  return props.masterFields.find((field) => field.code === code)?.displayName ?? code
}

function subTypeName(subTypeId: number): string {
  return props.subTypes.find((type) => type.id === subTypeId)?.name
    ?? t('record.detail.subTypeFallback', { id: subTypeId })
}

function childCodes(subTypeId: number, rows: RecordDetail['children'][number]['rows']): string[] {
  const available = new Set(rows.flatMap((row) => Object.keys(row.values)))
  const ordered = (props.subFields[subTypeId] ?? []).map((field) => field.code)
    .filter((code) => available.has(code))
  for (const row of rows) {
    for (const code of Object.keys(row.values)) if (!ordered.includes(code)) ordered.push(code)
  }
  return ordered
}

function childLabel(subTypeId: number, code: string): string {
  return (props.subFields[subTypeId] ?? []).find((field) => field.code === code)?.displayName ?? code
}
</script>

<template>
  <div class="record-snapshot">
    <section class="record-snapshot__master">
      <h3>{{ t('record.detail.masterFields') }}</h3>
      <dl class="record-detail__grid">
        <template v-for="code in Object.keys(snapshot.masterValues)" :key="code">
          <dt>{{ masterLabel(code) }}</dt>
          <dd>{{ displayValue(snapshot.masterValues[code]) }}</dd>
        </template>
      </dl>
    </section>

    <section
      v-for="group in snapshot.children"
      :key="group.subTypeId"
      class="record-snapshot__children"
      :data-testid="`${testidPrefix ?? 'snapshot'}-children-${group.subTypeId}`"
    >
      <h3>{{ subTypeName(group.subTypeId) }}</h3>
      <table class="records-table">
        <thead>
          <tr>
            <th>{{ t('record.detail.row') }}</th>
            <th v-for="code in childCodes(group.subTypeId, group.rows)" :key="code">
              {{ childLabel(group.subTypeId, code) }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in group.rows" :key="row.id">
            <td>{{ index + 1 }}</td>
            <td v-for="code in childCodes(group.subTypeId, group.rows)" :key="code">
              {{ displayValue(row.values[code]) }}
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
