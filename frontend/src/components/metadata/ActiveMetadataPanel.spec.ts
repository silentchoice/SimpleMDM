import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import ActiveMetadataPanel from './ActiveMetadataPanel.vue'

const metadataApi = vi.hoisted(() => ({ listMasterFields: vi.fn(), listSubTypes: vi.fn(), listSubFields: vi.fn() }))
vi.mock('../../api/metadata', () => metadataApi)

function deferred<T>() { let resolve!: (value: T) => void; return { promise: new Promise<T>((done) => { resolve = done }), resolve } }

describe('ACTIVE metadata panel', () => {
  it('clears stale data and ignores an older owner response after a master type switch', async () => {
    const oldFields = deferred<any[]>()
    metadataApi.listMasterFields.mockReturnValueOnce(oldFields.promise).mockResolvedValueOnce([{ id: 2, ownerTypeId: 42, code: 'NEW', displayName: 'New field', fieldType: 'TEXT', required: false, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }])
    metadataApi.listSubTypes.mockResolvedValue([])
    const wrapper = mount(ActiveMetadataPanel, { props: { masterTypeId: 41 }, global: { plugins: [ElementPlus] } })
    await wrapper.setProps({ masterTypeId: 42 })
    await flushPromises()
    expect(wrapper.text()).toContain('New field')
    oldFields.resolve([{ id: 1, ownerTypeId: 41, code: 'OLD', displayName: 'Old field', fieldType: 'TEXT', required: false, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }])
    await flushPromises()
    expect(wrapper.text()).toContain('New field')
    expect(wrapper.text()).not.toContain('Old field')
  })
})
