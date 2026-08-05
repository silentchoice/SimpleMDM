import { config, flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import SnapshotDiff from './SnapshotDiff.vue'
import { i18n, setLocale } from '../../i18n'

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
  beforeEach(() => {
    localStorage.clear()
    setLocale('zh-CN')
    config.global.plugins = [i18n]
  })

  it.each([
    ['master fields', 'MASTER_FIELDS' as const, field(11, 41, 'SERIAL'), field(0, 41, 'SERIAL')],
    ['sub types', 'SUB_TYPES' as const, subType(55, 'DEVICE'), subType(0, 'DEVICE')],
    ['sub fields', 'SUB_FIELDS' as const, field(71, 55, 'MODEL'), field(0, 55, 'MODEL')]
  ])('accepts real backend %s persisted-before and controller-after shapes',
    (_label, kind, beforeDefinition, afterDefinition) => {
      const wrapper = mount(SnapshotDiff, { props: {
        beforeSnapshot: snapshot([beforeDefinition], kind),
        afterSnapshot: snapshot([afterDefinition], kind),
        entityKind: kind,
        entityId: kind === 'SUB_FIELDS' ? 55 : 41
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

    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: before, afterSnapshot: after, entityKind: 'MASTER_FIELDS', entityId: 41
    } })
    const rows = wrapper.findAll('[data-testid="diff-row"]')

    expect(rows.map((row) => row.attributes('data-code'))).toEqual(['UNCHANGED', 'MODIFIED', 'ADDED', 'REMOVED'])
    expect(rows.map((row) => row.attributes('data-state'))).toEqual(['unchanged', 'modified', 'added', 'removed'])
    expect(rows.map((row) => row.find('header span').text())).toEqual(['未变化', '修改', '新增', '删除'])
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
      beforeSnapshot: snapshot([beforeDefinition]), afterSnapshot: snapshot([afterDefinition]),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.get('[data-testid="diff-row"]').attributes('data-state')).toBe('unchanged')
  })

  it('marks a pure subtype reorder as modified and displays both positions', () => {
    const before = snapshot([subType(55, 'ALPHA'), subType(56, 'BETA')], 'SUB_TYPES')
    const after = snapshot([subType(0, 'BETA'), subType(0, 'ALPHA')], 'SUB_TYPES')
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: before, afterSnapshot: after, entityKind: 'SUB_TYPES', entityId: 41
    } })
    const rows = wrapper.findAll('[data-testid="diff-row"]')

    expect(rows.map((row) => row.attributes('data-state'))).toEqual(['modified', 'modified'])
    expect(rows[0].text()).toContain('变更前位置：2')
    expect(rows[0].text()).toContain('变更后位置：1')
  })

  it('renders malformed version-one envelopes as an error state', () => {
    const wrapper = mount(SnapshotDiff, {
      props: {
        beforeSnapshot: '{not-json', afterSnapshot: JSON.stringify({ schemaVersion: 1 }),
        entityKind: 'MASTER_FIELDS', entityId: 41
      }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
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
      afterSnapshot: snapshot([field(0, 41, 'SERIAL')]),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it('rejects an envelope kind that differs from the approval task kind', () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([subType(55, 'DEVICE')], 'SUB_TYPES'),
      afterSnapshot: snapshot([subType(0, 'DEVICE')], 'SUB_TYPES'),
      entityKind: 'MASTER_FIELDS',
      entityId: 41
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it.each([
    ['MASTER_FIELDS', field(11, 42, 'SERIAL'), field(0, 42, 'SERIAL')],
    ['SUB_TYPES', subType(55, 'DEVICE', { masterTypeId: 42 }),
      subType(0, 'DEVICE', { masterTypeId: 42 })]
  ] as const)('rejects a %s envelope template that differs from task entity ID',
    (entityKind, beforeDefinition, afterDefinition) => {
      const wrapper = mount(SnapshotDiff, { props: {
        beforeSnapshot: snapshot([beforeDefinition], entityKind, 1, { templateId: 42 }),
        afterSnapshot: snapshot([afterDefinition], entityKind, 1, { templateId: 42 }),
        entityKind,
        entityId: 41
      } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
    })

  it('rejects SUB_FIELDS definitions owned by a different subtype than the task entity', () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([field(71, 56, 'MODEL')], 'SUB_FIELDS'),
      afterSnapshot: snapshot([field(0, 56, 'MODEL')], 'SUB_FIELDS'),
      entityKind: 'SUB_FIELDS',
      entityId: 55
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it.each([
    ['departmentId', { departmentId: 4 }],
    ['templateId', { templateId: 42 }],
    ['baseFingerprint', { baseFingerprint: 'b'.repeat(64) }]
  ])('rejects before/after metadata mismatch in %s', (_field, afterOverrides) => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([field(11, 41, 'SERIAL')]),
      afterSnapshot: snapshot([field(0, 41, 'SERIAL')], 'MASTER_FIELDS', 1, afterOverrides),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
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
      afterSnapshot: snapshot([afterDefinition], kind),
      entityKind: kind,
      entityId: kind === 'SUB_FIELDS' ? 55 : 41
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it('rejects case-insensitive duplicate codes', () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([]),
      afterSnapshot: snapshot([
        field(0, 41, 'serial', { sortOrder: 0 }),
        field(0, 41, 'SERIAL', { sortOrder: 1 })
      ]),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it('rejects duplicate field sort orders', () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([]),
      afterSnapshot: snapshot([
        field(0, 41, 'SERIAL', { sortOrder: 0 }),
        field(0, 41, 'MODEL', { sortOrder: 0 })
      ]),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it('uses a safe raw-JSON text fallback for unsupported schema versions', () => {
    const malicious = '<img src=x onerror="globalThis.pwned=true">'
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([{ code: 'OLD', displayName: malicious }], 'MASTER_FIELDS', 2),
      afterSnapshot: snapshot([{ code: 'NEW', displayName: malicious }], 'MASTER_FIELDS', 2),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.get('[data-testid="raw-json-fallback"]').text()).toContain('<img src=x onerror=')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<img src=x')
  })

  it('switches safe diff presentation to English without translating raw JSON values or state attributes', async () => {
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([field(11, 41, 'SERIAL', { displayName: 'User value' })]),
      afterSnapshot: snapshot([field(0, 41, 'SERIAL', { displayName: 'Changed value' })]),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.text()).toContain('修改')
    expect(wrapper.text()).toContain('Changed value')
    expect(wrapper.get('[data-testid="diff-row"]').attributes('data-state')).toBe('modified')
    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Modified')
    expect(wrapper.text()).toContain('Changed value')
    expect(wrapper.get('[data-testid="diff-row"]').attributes('data-state')).toBe('modified')
  })

  it('escapes displayed definition values instead of creating executable HTML', () => {
    const malicious = '<script>globalThis.pwned=true</script>'
    const wrapper = mount(SnapshotDiff, { props: {
      beforeSnapshot: snapshot([]),
      afterSnapshot: snapshot([field(0, 41, 'SAFE', { displayName: malicious })]),
      entityKind: 'MASTER_FIELDS', entityId: 41
    } })

    expect(wrapper.text()).toContain(malicious)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<script>globalThis.pwned=true</script>')
  })
})
