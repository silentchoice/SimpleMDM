<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type RecordAction = 'CREATE' | 'UPDATE' | 'DELETE'
type DiffState = 'added' | 'removed' | 'modified' | 'reordered'

interface SnapshotChildRow {
  recordId: number | null
  rowOrder: number
  values: Record<string, unknown>
}

interface SnapshotChildRows {
  subTypeId: number
  rows: SnapshotChildRow[]
}

interface RecordSnapshot {
  schemaVersion: number
  departmentId: number
  masterTypeId: number
  recordId: number | null
  recordCode: string
  action: RecordAction
  baseVersion: number
  masterValues: Record<string, unknown>
  children: SnapshotChildRows[]
}

interface MasterDiffRow {
  field: string
  before: string
  after: string
}

interface ChildDiffRow {
  key: string
  state: DiffState
  subTypeId: number
  before?: SnapshotChildRow
  after?: SnapshotChildRow
}

const props = defineProps<{
  beforeSnapshot: string | null
  afterSnapshot: string
}>()

const { t } = useI18n()

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function hasExactKeys(value: Record<string, unknown>, keys: string[]): boolean {
  const actual = Object.keys(value)
  return actual.length === keys.length && keys.every((key) => Object.prototype.hasOwnProperty.call(value, key))
}

function validRow(value: unknown): value is SnapshotChildRow {
  return isRecord(value)
    && (value.recordId === null || (Number.isSafeInteger(value.recordId) && (value.recordId as number) > 0))
    && Number.isSafeInteger(value.rowOrder) && (value.rowOrder as number) >= 0
    && isRecord(value.values)
}

function validGroup(value: unknown): value is SnapshotChildRows {
  return isRecord(value)
    && Number.isSafeInteger(value.subTypeId) && (value.subTypeId as number) > 0
    && Array.isArray(value.rows) && value.rows.every(validRow)
}

function validSnapshot(value: unknown): value is RecordSnapshot {
  return isRecord(value)
    && hasExactKeys(value, ['schemaVersion', 'departmentId', 'masterTypeId', 'recordId', 'recordCode', 'action', 'baseVersion', 'masterValues', 'children'])
    && Number.isSafeInteger(value.departmentId) && (value.departmentId as number) > 0
    && Number.isSafeInteger(value.masterTypeId) && (value.masterTypeId as number) > 0
    && (value.recordId === null || (Number.isSafeInteger(value.recordId) && (value.recordId as number) > 0))
    && typeof value.recordCode === 'string' && !!value.recordCode.trim()
    && typeof value.action === 'string' && ['CREATE', 'UPDATE', 'DELETE'].includes(value.action)
    && Number.isSafeInteger(value.baseVersion) && (value.baseVersion as number) >= 0
    && isRecord(value.masterValues)
    && Array.isArray(value.children) && value.children.every(validGroup)
}

function parse(raw: string | null): { kind: 'missing' } | { kind: 'unsupported', raw: string } | { kind: 'error' } | { kind: 'supported', snapshot: RecordSnapshot } {
  if (raw === null) return { kind: 'missing' }
  try {
    const value = JSON.parse(raw) as unknown
    if (!isRecord(value) || typeof value.schemaVersion !== 'number') return { kind: 'error' }
    if (value.schemaVersion !== 1) return { kind: 'unsupported', raw }
    return validSnapshot(value) ? { kind: 'supported', snapshot: value } : { kind: 'error' }
  } catch {
    return { kind: 'error' }
  }
}

function normalize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalize)
  if (!isRecord(value)) return value
  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, normalize(value[key])]))
}

function stringify(value: unknown): string {
  if (Array.isArray(value) || isRecord(value)) return JSON.stringify(value)
  return value == null ? '—' : String(value)
}

function sameValues(before: Record<string, unknown>, after: Record<string, unknown>): boolean {
  return JSON.stringify(normalize(before)) === JSON.stringify(normalize(after))
}

const parsed = computed(() => ({
  before: parse(props.beforeSnapshot),
  after: parse(props.afterSnapshot)
}))

const useRawFallback = computed(() => parsed.value.before.kind === 'unsupported' || parsed.value.after.kind === 'unsupported')

function bindingMatches(before: RecordSnapshot | null, after: RecordSnapshot): boolean {
  if (after.action === 'CREATE') return before === null && after.recordId === null && after.baseVersion === 0
  if (!before) return false
  return before.departmentId === after.departmentId
    && before.masterTypeId === after.masterTypeId
    && before.recordId === after.recordId
    && before.recordCode === after.recordCode
    && before.action === after.action
    && before.baseVersion === after.baseVersion
}

const hasError = computed(() => {
  if (useRawFallback.value) return false
  if (parsed.value.after.kind !== 'supported') return true
  if (parsed.value.after.snapshot.action === 'CREATE') return parsed.value.before.kind !== 'missing'
  if (parsed.value.before.kind !== 'supported') return true
  return !bindingMatches(parsed.value.before.snapshot, parsed.value.after.snapshot)
})

const masterRows = computed<MasterDiffRow[]>(() => {
  if (hasError.value || parsed.value.after.kind !== 'supported') return []
  const beforeValues = parsed.value.before.kind === 'supported' ? parsed.value.before.snapshot.masterValues : {}
  const afterValues = parsed.value.after.snapshot.masterValues
  return [...new Set([...Object.keys(beforeValues), ...Object.keys(afterValues)])]
    .filter((field) => stringify(beforeValues[field]) !== stringify(afterValues[field]))
    .map((field) => ({ field, before: stringify(beforeValues[field]), after: stringify(afterValues[field]) }))
})

const childRows = computed<ChildDiffRow[]>(() => {
  if (hasError.value || parsed.value.after.kind !== 'supported') return []
  const beforeGroups = new Map<number, SnapshotChildRows>(
    (parsed.value.before.kind === 'supported' ? parsed.value.before.snapshot.children : []).map((group) => [group.subTypeId, group] as const)
  )
  const rows: ChildDiffRow[] = []
  for (const group of parsed.value.after.snapshot.children) {
    const beforeGroup = beforeGroups.get(group.subTypeId)
    const beforeById = new Map((beforeGroup?.rows ?? []).filter((row) => row.recordId !== null).map((row) => [row.recordId as number, row] as const))
    const matched = new Set<number>()
    for (const afterRow of group.rows) {
      if (afterRow.recordId !== null && beforeById.has(afterRow.recordId)) {
        const beforeRow = beforeById.get(afterRow.recordId)!
        matched.add(afterRow.recordId)
        const reordered = beforeRow.rowOrder !== afterRow.rowOrder
        const modified = !sameValues(beforeRow.values, afterRow.values)
        if (reordered) rows.push({ key: `${group.subTypeId}-${afterRow.recordId}-reordered`, state: 'reordered', subTypeId: group.subTypeId, before: beforeRow, after: afterRow })
        else if (modified) rows.push({ key: `${group.subTypeId}-${afterRow.recordId}-modified`, state: 'modified', subTypeId: group.subTypeId, before: beforeRow, after: afterRow })
      } else {
        rows.push({ key: `${group.subTypeId}-new-${afterRow.rowOrder}`, state: 'added', subTypeId: group.subTypeId, after: afterRow })
      }
    }
    for (const beforeRow of beforeGroup?.rows ?? []) {
      if (beforeRow.recordId !== null && !matched.has(beforeRow.recordId)) {
        rows.push({ key: `${group.subTypeId}-${beforeRow.recordId}-removed`, state: 'removed', subTypeId: group.subTypeId, before: beforeRow })
      }
    }
    beforeGroups.delete(group.subTypeId)
  }
  for (const [subTypeId, group] of beforeGroups) {
    for (const row of group.rows) rows.push({ key: `${subTypeId}-${row.recordId ?? row.rowOrder}-removed`, state: 'removed', subTypeId, before: row })
  }
  return rows
})
</script>

<template>
  <section data-testid="record-snapshot-diff" class="record-diff" :aria-label="t('approval.recordDiff.ariaLabel')">
    <p v-if="hasError" class="form-error" role="alert">{{ t('approval.recordDiff.malformed') }}</p>
    <div v-else-if="useRawFallback" data-testid="record-raw-json-fallback" class="raw-json-fallback">
      <p>{{ t('approval.recordDiff.unsupported') }}</p>
      <div class="snapshot-columns">
        <section>
          <h3>{{ t('approval.diff.before') }}</h3>
          <pre>{{ props.beforeSnapshot ?? 'null' }}</pre>
        </section>
        <section>
          <h3>{{ t('approval.diff.after') }}</h3>
          <pre>{{ props.afterSnapshot }}</pre>
        </section>
      </div>
    </div>
    <template v-else>
      <section class="record-diff-section">
        <h3>{{ t('approval.recordDiff.masterTitle') }}</h3>
        <p v-if="masterRows.length === 0">{{ t('approval.recordDiff.noMasterChanges') }}</p>
        <div v-else class="record-diff-list">
          <article v-for="row in masterRows" :key="row.field" class="record-diff-row">
            <strong>{{ row.field }}</strong>
            <span>{{ row.before }}</span>
            <span>{{ row.after }}</span>
          </article>
        </div>
      </section>

      <section class="record-diff-section">
        <h3>{{ t('approval.recordDiff.childTitle') }}</h3>
        <p v-if="childRows.length === 0">{{ t('approval.recordDiff.noChildChanges') }}</p>
        <div v-else class="record-diff-list">
          <article
            v-for="row in childRows"
            :key="row.key"
            data-testid="child-row-diff"
            class="record-diff-row"
            :data-state="row.state"
          >
            <strong>{{ t('approval.recordDiff.subType', { id: row.subTypeId }) }}</strong>
            <span>{{ t(`approval.recordDiff.states.${row.state}`) }}</span>
            <span>{{ row.before ? stringify(row.before.values) : '—' }}</span>
            <span>{{ row.after ? stringify(row.after.values) : '—' }}</span>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>
