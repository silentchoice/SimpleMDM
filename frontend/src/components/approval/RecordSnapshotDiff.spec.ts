import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import RecordSnapshotDiff from './RecordSnapshotDiff.vue'
import { i18n, setLocale } from '../../i18n'

function snapshot(overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    schemaVersion: 1,
    departmentId: 7,
    masterTypeId: 41,
    recordId: 81,
    recordCode: 'AST-0001',
    action: 'UPDATE',
    baseVersion: 4,
    masterValues: { name: 'Laptop fleet', owner: 'Alice', active: true },
    children: [
      {
        subTypeId: 301,
        rows: [
          { recordId: 101, rowOrder: 0, values: { contact: 'Li', city: 'Shanghai' } },
          { recordId: 102, rowOrder: 1, values: { contact: 'Wang', city: 'Beijing' } },
          { recordId: 103, rowOrder: 2, values: { contact: 'Zhao', city: 'Nanjing' } },
          { recordId: 104, rowOrder: 3, values: { contact: 'Remove', city: 'Shenzhen' } }
        ]
      }
    ],
    ...overrides
  })
}

describe('record snapshot diff', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('en-US')
  })

  it('renders master-field and child-row diffs for row add/change/delete/reorder in one safe text-only view', () => {
    const wrapper = mount(RecordSnapshotDiff, {
      global: { plugins: [i18n] },
      props: {
        beforeSnapshot: snapshot(),
        afterSnapshot: snapshot({
          masterValues: { name: 'Laptop fleet 2', owner: 'Alice', active: true },
          children: [
            {
              subTypeId: 301,
              rows: [
                { recordId: 102, rowOrder: 0, values: { contact: 'Wang', city: 'Beijing' } },
                { recordId: 101, rowOrder: 1, values: { contact: 'Li', city: 'Shanghai' } },
                { recordId: 103, rowOrder: 2, values: { contact: 'Zhao', city: 'Suzhou' } },
                { recordId: null, rowOrder: 3, values: { contact: '<script>alert(1)</script>', city: 'Hangzhou' } }
              ]
            }
          ]
        })
      }
    })

    expect(wrapper.get('[data-testid="record-snapshot-diff"]').text()).toContain('Master field changes')
    expect(wrapper.text()).toContain('name')
    expect(wrapper.text()).toContain('Laptop fleet')
    expect(wrapper.text()).toContain('Laptop fleet 2')
    expect(wrapper.findAll('[data-testid="child-row-diff"]').map((row) => row.attributes('data-state')))
      .toEqual(['reordered', 'reordered', 'modified', 'added', 'removed'])
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<script>alert(1)</script>')
  })

  it('rejects malformed or task-mismatched record snapshots before diffing', () => {
    const wrapper = mount(RecordSnapshotDiff, {
      global: { plugins: [i18n] },
      props: {
        beforeSnapshot: snapshot(),
        afterSnapshot: snapshot({ recordId: 82, baseVersion: 5 })
      }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display record snapshot diff')
    expect(wrapper.find('[data-testid="child-row-diff"]').exists()).toBe(false)
  })

  it('uses a safe raw JSON fallback for unsupported schema versions', () => {
    const wrapper = mount(RecordSnapshotDiff, {
      global: { plugins: [i18n] },
      props: {
        beforeSnapshot: snapshot({ schemaVersion: 2, masterValues: { name: '<img src=x onerror=alert(1)>' } }),
        afterSnapshot: snapshot({ schemaVersion: 2, masterValues: { name: '<img src=x onerror=alert(1)>' } })
      }
    })

    expect(wrapper.get('[data-testid="record-raw-json-fallback"]').text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('switches labels bilingually without translating user-supplied values', async () => {
    const wrapper = mount(RecordSnapshotDiff, {
      global: { plugins: [i18n] },
      props: {
        beforeSnapshot: snapshot(),
        afterSnapshot: snapshot({
          masterValues: { name: 'User value', owner: 'Changed owner', active: true }
        })
      }
    })

    expect(wrapper.text()).toContain('Master field changes')
    expect(wrapper.text()).toContain('Changed owner')

    setLocale('zh-CN')
    await flushPromises()
    expect(wrapper.text()).toContain('主记录字段差异')
    expect(wrapper.text()).toContain('Changed owner')
  })
})
