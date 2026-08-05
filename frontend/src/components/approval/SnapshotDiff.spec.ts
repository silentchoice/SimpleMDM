import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SnapshotDiff from './SnapshotDiff.vue'

const fingerprint = 'a'.repeat(64)
function snapshot(orderedDefinitions: unknown[], schemaVersion = 1, overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    schemaVersion,
    departmentId: 3,
    templateId: 41,
    entityKind: 'MASTER_FIELDS',
    baseFingerprint: fingerprint,
    orderedDefinitions,
    ...overrides
  })
}

describe('snapshot diff', () => {
  it('matches by stable code and preserves after-order before appending removals', () => {
    const before = snapshot([
      { code: 'UNCHANGED', displayName: 'Same', required: false },
      { code: 'MODIFIED', displayName: 'Old', required: false },
      { code: 'REMOVED', displayName: 'Gone', required: false }
    ])
    const after = snapshot([
      { code: 'MODIFIED', displayName: 'New', required: true },
      { code: 'UNCHANGED', displayName: 'Same', required: false },
      { code: 'ADDED', displayName: 'Fresh', required: false }
    ])

    const wrapper = mount(SnapshotDiff, { props: { beforeSnapshot: before, afterSnapshot: after } })
    const rows = wrapper.findAll('[data-testid="diff-row"]')

    expect(rows.map((row) => row.attributes('data-code'))).toEqual(['MODIFIED', 'UNCHANGED', 'ADDED', 'REMOVED'])
    expect(rows.map((row) => row.attributes('data-state'))).toEqual(['modified', 'unchanged', 'added', 'removed'])
    expect(rows[0].text()).toContain('Old')
    expect(rows[0].text()).toContain('New')
  })

  it('treats object key order as unchanged while retaining array order semantics', () => {
    const before = snapshot([{ code: 'SERIAL', options: ['A', 'B'], required: true }])
    const after = snapshot([{ required: true, options: ['A', 'B'], code: 'SERIAL' }])

    const wrapper = mount(SnapshotDiff, { props: { beforeSnapshot: before, afterSnapshot: after } })

    expect(wrapper.get('[data-testid="diff-row"]').attributes('data-state')).toBe('unchanged')
  })

  it('renders malformed version-one envelopes as an error state', () => {
    const wrapper = mount(SnapshotDiff, {
      props: { beforeSnapshot: '{not-json', afterSnapshot: JSON.stringify({ schemaVersion: 1 }) }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
    expect(wrapper.find('[data-testid="diff-row"]').exists()).toBe(false)
  })

  it.each([
    ['unknown entity kind', { entityKind: 'RECORDS' }],
    ['zero department ID', { departmentId: 0 }],
    ['fractional template ID', { templateId: 41.5 }],
    ['non-SHA-256 fingerprint', { baseFingerprint: 'fp' }]
  ])('renders %s as a semantically malformed v1 envelope', (_case, overrides) => {
    const wrapper = mount(SnapshotDiff, {
      props: { beforeSnapshot: snapshot([], 1, overrides), afterSnapshot: snapshot([]) }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it.each([
    ['departmentId', { departmentId: 4 }],
    ['templateId', { templateId: 42 }],
    ['entityKind', { entityKind: 'SUB_TYPES' }],
    ['baseFingerprint', { baseFingerprint: 'b'.repeat(64) }]
  ])('rejects before/after metadata mismatch in %s', (_field, afterOverrides) => {
    const wrapper = mount(SnapshotDiff, {
      props: { beforeSnapshot: snapshot([]), afterSnapshot: snapshot([], 1, afterOverrides) }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it('uses a safe raw-JSON text fallback for unsupported schema versions', () => {
    const malicious = '<img src=x onerror="globalThis.pwned=true">'
    const wrapper = mount(SnapshotDiff, {
      props: {
        beforeSnapshot: snapshot([{ code: 'OLD', displayName: malicious }], 2),
        afterSnapshot: snapshot([{ code: 'NEW', displayName: malicious }], 2)
      }
    })

    expect(wrapper.get('[data-testid="raw-json-fallback"]').text()).toContain('<img src=x onerror=')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<img src=x')
  })

  it('escapes displayed definition values instead of creating executable HTML', () => {
    const malicious = '<script>globalThis.pwned=true</script>'
    const wrapper = mount(SnapshotDiff, {
      props: {
        beforeSnapshot: snapshot([]),
        afterSnapshot: snapshot([{ code: 'SAFE', displayName: malicious }])
      }
    })

    expect(wrapper.text()).toContain(malicious)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<script>globalThis.pwned=true</script>')
  })
})
