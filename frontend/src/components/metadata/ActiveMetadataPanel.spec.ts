import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import ActiveMetadataPanel from './ActiveMetadataPanel.vue'

const metadataApi = vi.hoisted(() => ({ ACTIVE_METADATA_INVALIDATED_EVENT: 'mdm:active-metadata-invalidated', listMasterFields: vi.fn(), listSubTypes: vi.fn(), listSubFields: vi.fn() }))
vi.mock('../../api/metadata', () => metadataApi)

function deferred<T>() { let resolve!: (value: T) => void; return { promise: new Promise<T>((done) => { resolve = done }), resolve } }

describe('ACTIVE metadata panel', () => {
  it('describes a missing department assignment without asking for a manual ID', () => {
    const wrapper = mount(ActiveMetadataPanel, { global: { plugins: [ElementPlus] } })

    expect(wrapper.text()).toContain('No master type is assigned to this department.')
    expect(wrapper.text()).not.toContain('Enter a master type ID')
    wrapper.unmount()
  })

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
    wrapper.unmount()
  })

  it('refreshes active metadata when an approval invalidates it', async () => {
    vi.clearAllMocks()
    metadataApi.listMasterFields.mockResolvedValue([])
    metadataApi.listSubTypes.mockResolvedValue([])
    const wrapper = mount(ActiveMetadataPanel, { props: { masterTypeId: 41 }, global: { plugins: [ElementPlus] } })
    await flushPromises()

    window.dispatchEvent(new Event(metadataApi.ACTIVE_METADATA_INVALIDATED_EVENT))
    await flushPromises()

    expect(metadataApi.listMasterFields).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
