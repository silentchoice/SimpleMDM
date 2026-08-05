<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MetadataEntityKind, SnapshotEnvelope } from '../../api/approval'

type Definition = Record<string, unknown> & { code: string }
type DiffState = 'added' | 'removed' | 'modified' | 'unchanged'
type SnapshotSide = 'before' | 'after'
interface DiffRow {
  code: string
  state: DiffState
  before?: Definition
  after?: Definition
  beforeIndex?: number
  afterIndex?: number
}
type SupportedEnvelope = Omit<SnapshotEnvelope, 'orderedDefinitions'> & { orderedDefinitions: Definition[] }
type ParsedSnapshot =
  | { kind: 'supported', envelope: SupportedEnvelope }
  | { kind: 'unsupported', value: unknown }
  | { kind: 'error' }

const props = defineProps<{
  beforeSnapshot: string
  afterSnapshot: string
  entityKind: MetadataEntityKind
  entityId: number
}>()
const { t } = useI18n()
const entityKinds = new Set(['MASTER_FIELDS', 'SUB_TYPES', 'SUB_FIELDS'])
const fingerprintPattern = /^[0-9a-f]{64}$/
const codePattern = /^[A-Za-z][A-Za-z0-9_]{0,63}$/
const fieldTypes = new Set(['TEXT', 'NUMBER', 'DATE', 'DATETIME', 'SELECT', 'RADIO', 'MULTISELECT', 'SWITCH'])
const selectionFieldTypes = new Set(['SELECT', 'RADIO', 'MULTISELECT'])
const envelopeKeys = ['schemaVersion', 'departmentId', 'templateId', 'entityKind', 'baseFingerprint', 'orderedDefinitions']
const fieldKeys = ['id', 'ownerTypeId', 'code', 'displayName', 'fieldType', 'required', 'options', 'shared', 'sortOrder', 'status']
const subtypeKeys = ['id', 'masterTypeId', 'code', 'name', 'status']

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function hasExactKeys(value: Record<string, unknown>, keys: string[]): boolean {
  const actual = Object.keys(value)
  return actual.length === keys.length
    && keys.every((key) => Object.prototype.hasOwnProperty.call(value, key))
}

function validId(value: unknown, side: SnapshotSide): boolean {
  return Number.isSafeInteger(value) && (side === 'before' ? (value as number) > 0 : value === 0)
}

function validLabel(value: unknown): value is string {
  return typeof value === 'string' && !!value.trim() && value.length <= 128
}

function validField(value: Record<string, unknown>, kind: MetadataEntityKind,
  side: SnapshotSide, templateId: number, entityId: number): value is Definition {
  if (!hasExactKeys(value, fieldKeys) || !validId(value.id, side)
    || !Number.isSafeInteger(value.ownerTypeId) || (value.ownerTypeId as number) <= 0
    || (kind === 'MASTER_FIELDS' && value.ownerTypeId !== templateId)
    || (kind === 'SUB_FIELDS' && value.ownerTypeId !== entityId)
    || typeof value.code !== 'string' || !codePattern.test(value.code)
    || !validLabel(value.displayName) || typeof value.fieldType !== 'string'
    || !fieldTypes.has(value.fieldType) || typeof value.required !== 'boolean'
    || !Array.isArray(value.options) || typeof value.shared !== 'boolean'
    || (kind === 'MASTER_FIELDS' && value.shared)
    || !Number.isSafeInteger(value.sortOrder) || (value.sortOrder as number) < 0
    || value.status !== 'ACTIVE') return false
  const options = value.options as unknown[]
  if (options.some((option) => typeof option !== 'string' || !option.trim())
    || new Set(options).size !== options.length) return false
  return selectionFieldTypes.has(value.fieldType) ? options.length > 0 : options.length === 0
}

function validSubtype(value: Record<string, unknown>, side: SnapshotSide,
  templateId: number): value is Definition {
  return hasExactKeys(value, subtypeKeys) && validId(value.id, side)
    && value.masterTypeId === templateId && typeof value.code === 'string'
    && codePattern.test(value.code) && validLabel(value.name) && value.status === 'ACTIVE'
}

function definitions(value: unknown, kind: MetadataEntityKind, side: SnapshotSide,
  templateId: number, entityId: number): Definition[] | null {
  if (!Array.isArray(value) || (side === 'after' && value.length === 0)) return null
  const seen = new Set<string>()
  const sortOrders = new Set<number>()
  const result: Definition[] = []
  for (const item of value) {
    if (!isRecord(item)) return null
    if (kind === 'SUB_TYPES') {
      if (!validSubtype(item, side, templateId)) return null
    } else if (!validField(item, kind, side, templateId, entityId)) return null
    const definition = item as Definition
    const normalizedCode = definition.code.toLowerCase()
    if (seen.has(normalizedCode)) return null
    seen.add(normalizedCode)
    if (kind !== 'SUB_TYPES') {
      const sortOrder = definition.sortOrder as number
      if (sortOrders.has(sortOrder)) return null
      sortOrders.add(sortOrder)
    }
    result.push(definition)
  }
  return result
}

function parseSnapshot(raw: string, side: SnapshotSide): ParsedSnapshot {
  try {
    const value: unknown = JSON.parse(raw)
    if (!Number.isSafeInteger(props.entityId) || props.entityId <= 0
      || !isRecord(value) || typeof value.schemaVersion !== 'number') return { kind: 'error' }
    if (value.schemaVersion !== 1) {
      return value.entityKind === props.entityKind ? { kind: 'unsupported', value } : { kind: 'error' }
    }
    if (!hasExactKeys(value, envelopeKeys)
      || !Number.isSafeInteger(value.departmentId) || (value.departmentId as number) <= 0
      || !Number.isSafeInteger(value.templateId) || (value.templateId as number) <= 0
      || typeof value.entityKind !== 'string' || !entityKinds.has(value.entityKind)
      || value.entityKind !== props.entityKind
      || typeof value.baseFingerprint !== 'string' || !fingerprintPattern.test(value.baseFingerprint)
      || !Array.isArray(value.orderedDefinitions)) return { kind: 'error' }
    const entityKind = value.entityKind as MetadataEntityKind
    if (entityKind !== 'SUB_FIELDS' && value.templateId !== props.entityId) return { kind: 'error' }
    const orderedDefinitions = definitions(value.orderedDefinitions, entityKind, side,
      value.templateId as number, props.entityId)
    if (!orderedDefinitions) return { kind: 'error' }
    return {
      kind: 'supported',
      envelope: {
        schemaVersion: 1,
        departmentId: value.departmentId as number,
        templateId: value.templateId as number,
        entityKind,
        baseFingerprint: value.baseFingerprint,
        orderedDefinitions
      }
    }
  } catch {
    return { kind: 'error' }
  }
}

function normalize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalize)
  if (!isRecord(value)) return value
  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, normalize(value[key])]))
}

function businessProjection(kind: MetadataEntityKind, value: Definition): Record<string, unknown> {
  if (kind === 'SUB_TYPES') return { code: value.code, name: value.name }
  return {
    code: value.code,
    displayName: value.displayName,
    fieldType: value.fieldType,
    required: value.required,
    options: value.options,
    shared: value.shared,
    sortOrder: value.sortOrder
  }
}

function sameDefinition(kind: MetadataEntityKind, before: Definition, after: Definition): boolean {
  return JSON.stringify(normalize(businessProjection(kind, before)))
    === JSON.stringify(normalize(businessProjection(kind, after)))
}

function formatted(value: unknown): string {
  return JSON.stringify(value, null, 2)
}

const parsed = computed(() => ({
  before: parseSnapshot(props.beforeSnapshot, 'before'),
  after: parseSnapshot(props.afterSnapshot, 'after')
}))
function metadataMatches(before: SupportedEnvelope, after: SupportedEnvelope): boolean {
  return before.departmentId === after.departmentId && before.templateId === after.templateId
    && before.entityKind === after.entityKind && before.baseFingerprint === after.baseFingerprint
}
const hasError = computed(() => parsed.value.before.kind === 'error' || parsed.value.after.kind === 'error'
  || (parsed.value.before.kind === 'supported' && parsed.value.after.kind === 'supported'
    && !metadataMatches(parsed.value.before.envelope, parsed.value.after.envelope)))
const useRawFallback = computed(() => !hasError.value
  && (parsed.value.before.kind === 'unsupported' || parsed.value.after.kind === 'unsupported'))
const rawBefore = computed(() => parsed.value.before.kind === 'unsupported'
  ? formatted(parsed.value.before.value)
  : props.beforeSnapshot)
const rawAfter = computed(() => parsed.value.after.kind === 'unsupported'
  ? formatted(parsed.value.after.value)
  : props.afterSnapshot)

const rows = computed<DiffRow[]>(() => {
  if (parsed.value.before.kind !== 'supported' || parsed.value.after.kind !== 'supported') return []
  const beforeDefinitions = parsed.value.before.envelope.orderedDefinitions
  const afterDefinitions = parsed.value.after.envelope.orderedDefinitions
  const kind = parsed.value.after.envelope.entityKind
  const beforeByCode = new Map(beforeDefinitions.map((item, index) =>
    [item.code.toLowerCase(), { item, index }] as const))
  const afterCodes = new Set(afterDefinitions.map((item) => item.code.toLowerCase()))
  const current = afterDefinitions.map((after, afterIndex): DiffRow => {
    const matched = beforeByCode.get(after.code.toLowerCase())
    if (!matched) return { code: after.code, state: 'added', after, afterIndex }
    const unchanged = matched.index === afterIndex && sameDefinition(kind, matched.item, after)
    return {
      code: after.code,
      state: unchanged ? 'unchanged' : 'modified',
      before: matched.item,
      after,
      beforeIndex: matched.index,
      afterIndex
    }
  })
  const removed = beforeDefinitions
    .map((before, beforeIndex) => ({ before, beforeIndex }))
    .filter(({ before }) => !afterCodes.has(before.code.toLowerCase()))
    .map(({ before, beforeIndex }): DiffRow => ({
      code: before.code, state: 'removed', before, beforeIndex
    }))
  return [...current, ...removed]
})
</script>

<template>
  <section class="snapshot-diff" :aria-label="t('approval.diff.ariaLabel')">
    <p v-if="hasError" role="alert" class="form-error">{{ t('approval.diff.malformed') }}</p>
    <div v-else-if="useRawFallback" data-testid="raw-json-fallback" class="raw-json-fallback">
      <p>{{ t('approval.diff.unsupported') }}</p>
      <div class="snapshot-columns">
        <section><h3>{{ t('approval.diff.before') }}</h3><pre>{{ rawBefore }}</pre></section>
        <section><h3>{{ t('approval.diff.after') }}</h3><pre>{{ rawAfter }}</pre></section>
      </div>
    </div>
    <div v-else class="diff-list">
      <article
        v-for="row in rows"
        :key="row.code"
        data-testid="diff-row"
        :data-code="row.code"
        :data-state="row.state"
        :class="['diff-row', `diff-row--${row.state}`]"
      >
        <header><strong>{{ row.code }}</strong><span>{{ t(`approval.diff.states.${row.state}`) }}</span></header>
        <div class="snapshot-columns">
          <section v-if="row.before"><h4>{{ t('approval.diff.beforePosition', { position: (row.beforeIndex ?? 0) + 1 }) }}</h4><pre>{{ formatted(row.before) }}</pre></section>
          <section v-if="row.after"><h4>{{ t('approval.diff.afterPosition', { position: (row.afterIndex ?? 0) + 1 }) }}</h4><pre>{{ formatted(row.after) }}</pre></section>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.diff-list { display: grid; gap: 12px; }
.diff-row { padding: 12px; border: 1px solid #d1d5db; border-left-width: 6px; border-radius: 6px; background: white; }
.diff-row header { display: flex; justify-content: space-between; text-transform: capitalize; }
.diff-row--added { border-left-color: #2e7d32; background: #edf7ed; }
.diff-row--removed { border-left-color: #c62828; background: #fdecec; }
.diff-row--modified { border-left-color: #b7791f; background: #fff8df; }
.diff-row--unchanged { border-left-color: #94a3b8; }
.snapshot-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
pre { overflow: auto; margin: 6px 0 0; padding: 8px; border-radius: 4px; background: rgb(255 255 255 / 65%); white-space: pre-wrap; word-break: break-word; }
@media (max-width: 700px) { .snapshot-columns { grid-template-columns: 1fr; } }
</style>
