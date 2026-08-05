import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SnapshotDiff from './SnapshotDiff.vue'

type EntityKind = 'MASTER_FIELDS' | 'SUB_TYPES' | 'SUB_FIELDS'

const fingerprint = 'a'.repeat(64)
function field(id: number, ownerTypeId: number, code: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    ownerTypeId,
    code,
    displayName: `${code} name`,
    fieldType: 'TEXT',
    required: false,
    options: [],
    shared: false,
    sortOrder: 0,
    status: 'ACTIVE',
    ...overrides
  }
}
function subType(id: number, code: string, overrides: Record<string, unknown> = {}) {
  return { id, masterTypeId: 41, code, name: `${code} name`, status: 'ACTIVE', ...overrides }
}
function snapshot(orderedDefinitions: unknown[], entityKind: EntityKind = 'MASTER_FIELDS',
  schemaVersion = 1, overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    schemaVersion,
    departmentId: 3,
    templateId: 41,
    entityKind,
    baseFingerprint: fingerprint,
    orderedDefinitions,
    ...overrides
  })
}
function without(value: Record<string, unknown>, key: string): Record<string, unknown> {
  const copy = { ...value }
  delete copy[key]
  return copy
}

describe('snapshot diff', () => {
  it.each([
    ['master fields', 'MASTER_FIELDS' as const, field(11, 41, 'SERIAL'), field(0, 41, 'SERIAL')],
    ['sub types', 'SUB_TYPES' as const, subType(55, 'DEVICE'), subType(0, 'DEVICE')],
    ['sub fields', 'SUB_FIELDS' as const, field(71, 55, 'MODEL'), field(0, 55, 'MODEL')]
  ])('accepts real backend %s persisted-before and controller-after shapes',
    (_label, kind, beforeDefinition, afterDefinition) => {
      const wrapper = mount(SnapshotDiff, { props: {
        beforeSnapshot: snapshot([beforeDefinition], kind),
        afterSnapshot: snapshot([afterDefinition], kind)
      } })

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="diff-row"]').attributes('data-state')).toBe('unchanged')
    })

  it('matches by stable code, ignores transport identity, and classifies business changes', () => {
    const before = snapshot([
      field(11, 41, 'UNCHANGED', { displayName: 'Same', sortOrder: 0 }),
      field(12, 41, 'MODIFIED', { displayName: 'Old', sortOrder: 1 }),
      field(13, 41, 'REMOVED', { displayName: 'Gone', sortOrder: 2 })
    ])
    const after = snapshot([
      field(0, 41, 'UNCHANGED', { displayName: 'Same', sortOrder: 0 }),
      field(0, 41, 'MODIFIED', { displayName: 'New', required: true, sortOrder: 1 }),
      field(0, 41, 'ADDED', { displayName: 'Fresh', sortOrder: 2 })
    ])

    const wrapper = mount(SnapshotDiff, { props: { beforeSnapshot: before, afterSnapshot: after } })
    const rows = wrapper.findAll('[data-testid="diff-row"]')

    expect(rows.map((row) => row.attributes('data-code'))).toEqual(['UNCHANGED', 'MODIFIED', 'ADDED', 'REMOVED'])
    expect(rows.map((row) => row.attributes('data-state'))).toEqual(['unchanged', 'modified', 'added', 'removed'])
    expect(rows[1].text()).toContain('Old')
    expect(rows[1].text()).toContain('New')
  })

  it('treats object key order as unchanged while retaining array order semantics', () => {
    const beforeDefinition = field(11, 41, 'SERIAL', { fieldType: 'SELECT', options: ['A', 'B'], required: true })
    const afterDefinition = {
      status: 'ACTIVE', sortOrder: 0, shared: false, options: ['A', 'B'], required: true,
      fieldType: 'SELECT', displayName: 'SERIAL name', code: 'SERIAL', ownerTypeId: 41, id: 0
    }
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([beforeDefinition]), afterSnapshot: snapshot([afterDefinition])
    } })

    expect(wrapper.get('[data-testid="diff-row"]').attributes('data-state')).toBe('unchanged')
  })

  it('marks a pure subtype reorder as modified and displays both positions', () => {
    const before = snapshot([subType(55, 'ALPHA'), subType(56, 'BETA')], 'SUB_TYPES')
    const after = snapshot([subType(0, 'BETA'), subType(0, 'ALPHA')], 'SUB_TYPES')
    const wrapper = mount(SnapshotDiff, { props: { beforeSnapshot: before, afterSnapshot: after } })
    const rows = wrapper.findAll('[data-testid="diff-row"]')

    expect(rows.map((row) => row.attributes('data-state'))).toEqual(['modified', 'modified'])
    expect(rows[0].text()).toContain('Before position: 2')
    expect(rows[0].text()).toContain('After position: 1')
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
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([field(11, 41, 'SERIAL')], 'MASTER_FIELDS', 1, overrides),
      afterSnapshot: snapshot([field(0, 41, 'SERIAL')])
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it.each([
    ['departmentId', { departmentId: 4 }],
    ['templateId', { templateId: 42 }],
    ['baseFingerprint', { baseFingerprint: 'b'.repeat(64) }]
  ])('rejects before/after metadata mismatch in %s', (_field, afterOverrides) => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([field(11, 41, 'SERIAL')]),
      afterSnapshot: snapshot([field(0, 41, 'SERIAL')], 'MASTER_FIELDS', 1, afterOverrides)
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it.each([
    ['MASTER_FIELDS missing a serialized field member', 'MASTER_FIELDS' as const,
      without(field(0, 41, 'SERIAL'), 'options')],
    ['SUB_TYPES with a persisted ID in the controller-after snapshot', 'SUB_TYPES' as const,
      subType(55, 'DEVICE')],
    ['SUB_FIELDS with a non-numeric sort order', 'SUB_FIELDS' as const,
      field(0, 55, 'MODEL', { sortOrder: '0' })],
    ['MASTER_FIELDS with a non-ACTIVE status', 'MASTER_FIELDS' as const,
      field(0, 41, 'SERIAL', { status: 'DISABLED' })],
    ['MASTER_FIELDS with invalid selection options', 'MASTER_FIELDS' as const,
      field(0, 41, 'SERIAL', { fieldType: 'SELECT', options: [] })]
  ])('rejects malformed v1 definition: %s', (_label, kind, afterDefinition) => {
    const beforeDefinition = kind === 'SUB_TYPES' ? subType(55, 'DEVICE')
      : field(11, kind === 'SUB_FIELDS' ? 55 : 41, kind === 'SUB_FIELDS' ? 'MODEL' : 'SERIAL')
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([beforeDefinition], kind),
      afterSnapshot: snapshot([afterDefinition], kind)
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it('rejects case-insensitive duplicate codes', () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([]),
      afterSnapshot: snapshot([
        field(0, 41, 'serial', { sortOrder: 0 }),
        field(0, 41, 'SERIAL', { sortOrder: 1 })
      ])
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it('rejects duplicate field sort orders', () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([]),
      afterSnapshot: snapshot([
        field(0, 41, 'SERIAL', { sortOrder: 0 }),
        field(0, 41, 'MODEL', { sortOrder: 0 })
      ])
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it('uses a safe raw-JSON text fallback for unsupported schema versions', () => {
    const malicious = '<img src=x onerror="globalThis.pwned=true">'
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([{ code: 'OLD', displayName: malicious }], 'MASTER_FIELDS', 2),
      afterSnapshot: snapshot([{ code: 'NEW', displayName: malicious }], 'MASTER_FIELDS', 2)
    } })

    expect(wrapper.get('[data-testid="raw-json-fallback"]').text()).toContain('<img src=x onerror=')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<img src=x')
  })

  it('escapes displayed definition values instead of creating executable HTML', () => {
    const malicious = '<script>globalThis.pwned=true</script>'
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([]),
      afterSnapshot: snapshot([field(0, 41, 'SAFE', { displayName: malicious })])
    } })

    expect(wrapper.text()).toContain(malicious)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<script>globalThis.pwned=true</script>')
  })
})
