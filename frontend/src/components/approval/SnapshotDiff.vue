<script setup lang="ts">
import { computed } from 'vue'
import type { SnapshotEnvelope } from '../../api/approval'

type Definition = Record<string, unknown> & { code: string }
type DiffState = 'added' | 'removed' | 'modified' | 'unchanged'
interface DiffRow { code: string, state: DiffState, before?: Definition, after?: Definition }
type SupportedEnvelope = Omit<SnapshotEnvelope, 'orderedDefinitions'> & { orderedDefinitions: Definition[] }
type ParsedSnapshot =
  | { kind: 'supported', envelope: SupportedEnvelope }
  | { kind: 'unsupported', value: unknown }
  | { kind: 'error' }

const props = defineProps<{ beforeSnapshot: string, afterSnapshot: string }>()

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function definitions(value: unknown): Definition[] | null {
  if (!Array.isArray(value)) return null
  const seen = new Set<string>()
  const result: Definition[] = []
  for (const item of value) {
    if (!isRecord(item) || typeof item.code !== 'string' || !item.code.trim() || seen.has(item.code)) return null
    seen.add(item.code)
    result.push(item as Definition)
  }
  return result
}

function parseSnapshot(raw: string): ParsedSnapshot {
  try {
    const value: unknown = JSON.parse(raw)
    if (!isRecord(value) || typeof value.schemaVersion !== 'number') return { kind: 'error' }
    if (value.schemaVersion !== 1) return { kind: 'unsupported', value }
    const orderedDefinitions = definitions(value.orderedDefinitions)
    if (typeof value.departmentId !== 'number' || typeof value.templateId !== 'number'
      || typeof value.entityKind !== 'string' || typeof value.baseFingerprint !== 'string'
      || !orderedDefinitions) return { kind: 'error' }
    return {
      kind: 'supported',
      envelope: { ...value, orderedDefinitions } as SupportedEnvelope
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

function sameDefinition(before: Definition, after: Definition): boolean {
  return JSON.stringify(normalize(before)) === JSON.stringify(normalize(after))
}

function formatted(value: unknown): string {
  return JSON.stringify(value, null, 2)
}

const parsed = computed(() => ({
  before: parseSnapshot(props.beforeSnapshot),
  after: parseSnapshot(props.afterSnapshot)
}))
const hasError = computed(() => parsed.value.before.kind === 'error' || parsed.value.after.kind === 'error')
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
  const beforeByCode = new Map(beforeDefinitions.map((item) => [item.code, item]))
  const afterCodes = new Set(afterDefinitions.map((item) => item.code))
  const current = afterDefinitions.map((after): DiffRow => {
    const before = beforeByCode.get(after.code)
    if (!before) return { code: after.code, state: 'added', after }
    return { code: after.code, state: sameDefinition(before, after) ? 'unchanged' : 'modified', before, after }
  })
  const removed = beforeDefinitions
    .filter((before) => !afterCodes.has(before.code))
    .map((before): DiffRow => ({ code: before.code, state: 'removed', before }))
  return [...current, ...removed]
})
</script>

<template>
  <section class="snapshot-diff" aria-label="Snapshot differences">
    <p v-if="hasError" role="alert" class="form-error">Unable to display snapshot diff: malformed snapshot data.</p>
    <div v-else-if="useRawFallback" data-testid="raw-json-fallback" class="raw-json-fallback">
      <p>Unsupported snapshot schema version. Review the raw JSON below.</p>
      <div class="snapshot-columns">
        <section><h3>Before</h3><pre>{{ rawBefore }}</pre></section>
        <section><h3>After</h3><pre>{{ rawAfter }}</pre></section>
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
        <header><strong>{{ row.code }}</strong><span>{{ row.state }}</span></header>
        <div class="snapshot-columns">
          <section v-if="row.before"><h4>Before</h4><pre>{{ formatted(row.before) }}</pre></section>
          <section v-if="row.after"><h4>After</h4><pre>{{ formatted(row.after) }}</pre></section>
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
