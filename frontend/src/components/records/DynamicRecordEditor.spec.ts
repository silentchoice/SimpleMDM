import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppRouter } from '../../router'
import { i18n, setLocale } from '../../i18n'
import { useAuthStore } from '../../stores/auth'

const metadataApi = vi.hoisted(() => ({
  listMasterFields: vi.fn(),
  listSubTypes: vi.fn(),
  listSubFields: vi.fn()
}))
const recordsApi = vi.hoisted(() => ({
  getRecordDraft: vi.fn(),
  getRecord: vi.fn(),
  listRecordHistory: vi.fn(),
  updateRecordDraft: vi.fn(),
  submitRecordDraft: vi.fn(),
  acquireRecordLock: vi.fn(),
  renewRecordLock: vi.fn(),
  releaseRecordLock: vi.fn()
}))

vi.mock('../../api/metadata', () => metadataApi)
vi.mock('../../api/records', () => recordsApi)

const RouterHost = { template: '<router-view />' }

const masterFields = [
  { id: 101, ownerTypeId: 41, code: 'name', displayName: 'Name', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' },
  { id: 102, ownerTypeId: 41, code: 'quantity', displayName: 'Quantity', fieldType: 'NUMBER', required: false, options: [], shared: false, sortOrder: 1, status: 'ACTIVE' },
  { id: 103, ownerTypeId: 41, code: 'purchaseDate', displayName: 'Purchase date', fieldType: 'DATE', required: false, options: [], shared: false, sortOrder: 2, status: 'ACTIVE' },
  { id: 104, ownerTypeId: 41, code: 'updatedAt', displayName: 'Updated at', fieldType: 'DATETIME', required: false, options: [], shared: false, sortOrder: 3, status: 'ACTIVE' },
  { id: 105, ownerTypeId: 41, code: 'stage', displayName: 'Stage', fieldType: 'SELECT', required: true, options: ['NEW', 'USED'], shared: false, sortOrder: 4, status: 'ACTIVE' },
  { id: 106, ownerTypeId: 41, code: 'ownerType', displayName: 'Owner type', fieldType: 'RADIO', required: false, options: ['INTERNAL', 'EXTERNAL'], shared: false, sortOrder: 5, status: 'ACTIVE' },
  { id: 107, ownerTypeId: 41, code: 'labels', displayName: 'Labels', fieldType: 'MULTISELECT', required: false, options: ['A', 'B'], shared: false, sortOrder: 6, status: 'ACTIVE' },
  { id: 108, ownerTypeId: 41, code: 'enabled', displayName: 'Enabled', fieldType: 'SWITCH', required: false, options: [], shared: false, sortOrder: 7, status: 'ACTIVE' }
]
const subTypes = [
  { id: 301, masterTypeId: 41, code: 'CONTACT', name: 'Contacts', status: 'ACTIVE' },
  { id: 302, masterTypeId: 41, code: 'NOTE', name: 'Notes', status: 'ACTIVE' }
]
const subTypeFields: Record<number, any[]> = {
  301: [
    { id: 401, ownerTypeId: 301, code: 'email', displayName: 'Email', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' },
    { id: 402, ownerTypeId: 301, code: 'amount', displayName: 'Amount', fieldType: 'NUMBER', required: false, options: [], shared: false, sortOrder: 1, status: 'ACTIVE' },
    { id: 403, ownerTypeId: 301, code: 'startDate', displayName: 'Start date', fieldType: 'DATE', required: false, options: [], shared: false, sortOrder: 2, status: 'ACTIVE' },
    { id: 404, ownerTypeId: 301, code: 'changedAt', displayName: 'Changed at', fieldType: 'DATETIME', required: false, options: [], shared: false, sortOrder: 3, status: 'ACTIVE' },
    { id: 405, ownerTypeId: 301, code: 'kind', displayName: 'Kind', fieldType: 'SELECT', required: false, options: ['WORK', 'HOME'], shared: false, sortOrder: 4, status: 'ACTIVE' },
    { id: 406, ownerTypeId: 301, code: 'tone', displayName: 'Tone', fieldType: 'RADIO', required: false, options: ['WORK', 'HOME'], shared: false, sortOrder: 5, status: 'ACTIVE' },
    { id: 407, ownerTypeId: 301, code: 'labels', displayName: 'Labels', fieldType: 'MULTISELECT', required: false, options: ['VIP', 'PRIMARY'], shared: false, sortOrder: 6, status: 'ACTIVE' },
    { id: 408, ownerTypeId: 301, code: 'primary', displayName: 'Primary', fieldType: 'SWITCH', required: false, options: [], shared: false, sortOrder: 7, status: 'ACTIVE' }
  ],
  302: [{ id: 409, ownerTypeId: 302, code: 'body', displayName: 'Body', fieldType: 'TEXT', required: false, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }]
}
const draft = {
  id: 91,
  recordId: 81,
  masterTypeId: 41,
  departmentId: 7,
  recordCode: 'AST-0001',
  action: 'UPDATE',
  baseVersion: 4,
  masterValues: {
    name: 'Laptop fleet',
    quantity: 12,
    purchaseDate: '2026-08-01',
    updatedAt: '2026-08-01T09:30:00',
    stage: 'LEGACY<option>',
    ownerType: 'INTERNAL',
    labels: ['A'],
    enabled: true
  },
  children: [
    {
      subTypeId: 301,
      rows: [{
        recordId: 900,
        rowOrder: 0,
        values: {
          email: 'ops@example.com',
          amount: 5,
          startDate: '2026-08-02',
          changedAt: '2026-08-02T10:45:00',
          kind: 'LEGACY_KIND<script>',
          tone: 'HOME',
          labels: ['VIP'],
          primary: true
        }
      }]
    },
    { subTypeId: 302, rows: [{ recordId: 901, rowOrder: 0, values: { body: 'Existing note' } }] }
  ],
  status: 'DRAFT',
  createdBy: 12,
  deleteReason: null
}
const record = {
  id: 81,
  masterTypeId: 41,
  departmentId: 7,
  recordCode: 'AST-0001',
  masterValues: { name: 'Laptop fleet', enabled: true },
  children: [],
  version: 4,
  status: 'ACTIVE'
}
const history = [
  record,
  { ...record, version: 3, masterValues: { name: 'Laptop fleet v3', enabled: false } }
]

async function mountEditor() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    accessToken: 'token',
    user: { id: 12, username: 'editor', displayName: 'Editor' },
    roles: ['DEPT_EDITOR'],
    department: { id: 7, code: 'OPS', name: 'Operations' }
  })
  const router = createAppRouter()
  await router.push('/records/drafts/91')
  await router.isReady()
  const wrapper = mount(RouterHost, { global: { plugins: [pinia, ElementPlus, i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('dynamic record editor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    setLocale('en-US')
    metadataApi.listMasterFields.mockResolvedValue(masterFields)
    metadataApi.listSubTypes.mockResolvedValue(subTypes)
    metadataApi.listSubFields.mockImplementation((id: number) => Promise.resolve(subTypeFields[id] ?? []))
    recordsApi.getRecordDraft.mockResolvedValue(structuredClone(draft))
    recordsApi.getRecord.mockResolvedValue(record)
    recordsApi.listRecordHistory.mockResolvedValue(history)
    recordsApi.updateRecordDraft.mockResolvedValue(structuredClone(draft))
    recordsApi.submitRecordDraft.mockResolvedValue(undefined)
    recordsApi.acquireRecordLock.mockResolvedValue({
      recordId: 81,
      departmentId: 7,
      userId: 12,
      displayName: 'Editor',
      token: 'lock-token',
      expiresAt: '2026-08-07T09:30:00.000Z'
    })
    recordsApi.renewRecordLock.mockResolvedValue({
      recordId: 81,
      departmentId: 7,
      userId: 12,
      displayName: 'Editor',
      token: 'lock-token',
      expiresAt: '2026-08-07T09:45:00.000Z'
    })
    recordsApi.releaseRecordLock.mockResolvedValue(undefined)
  })

  it('renders every field type from ACTIVE metadata, keeps generated codes read-only, validates required fields, and preserves unknown values as escaped text', async () => {
    const { wrapper } = await mountEditor()

    expect(recordsApi.getRecordDraft).toHaveBeenCalledWith(91)
    expect(metadataApi.listMasterFields).toHaveBeenCalledWith(41)
    expect(wrapper.get('[name="recordCode"]').attributes()).toHaveProperty('readonly')
    expect(wrapper.get('[name="field-name"]').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.get('[name="field-quantity"]').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.get('[name="field-purchaseDate"]').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.get('[name="field-updatedAt"]').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.get('[name="field-stage"]').element).toBeInstanceOf(HTMLSelectElement)
    expect(wrapper.get('[name="field-ownerType"]').element).toBeInstanceOf(HTMLFieldSetElement)
    expect(wrapper.get('[name="field-labels"]').element).toBeInstanceOf(HTMLSelectElement)
    expect(wrapper.get('[name="field-enabled"]').element).toBeInstanceOf(HTMLInputElement)
    expect(wrapper.text()).toContain('LEGACY<option>')
    expect(wrapper.html()).not.toContain('<option>LEGACY<option></option>')

    await wrapper.get('[name="field-name"]').setValue('')
    await wrapper.get('[name="field-stage"]').setValue('')
    await wrapper.get('[data-testid="record-save"]').trigger('click')
    await flushPromises()
    expect(recordsApi.updateRecordDraft).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Name is required')
    expect(wrapper.text()).toContain('Stage is required')
  })

  it('supports child subtype tabs, add-delete-reorder row editing, deep-copies API data, and keeps payload enums unchanged when saving', async () => {
    const original = structuredClone(draft)
    recordsApi.getRecordDraft.mockResolvedValueOnce(original)
    const { wrapper } = await mountEditor()

    expect(wrapper.get('[data-testid="subtype-tab-301"]').text()).toContain('Contacts')
    expect(wrapper.get('[data-testid="subtype-tab-302"]').text()).toContain('Notes')
    expect((wrapper.get('[name="child-301-row-0-email"]').element as HTMLInputElement).value).toBe('ops@example.com')
    expect(wrapper.get('[name="child-301-row-0-amount"]').attributes('type')).toBe('number')
    expect(wrapper.get('[name="child-301-row-0-startDate"]').attributes('type')).toBe('date')
    expect(wrapper.get('[name="child-301-row-0-changedAt"]').attributes('type')).toBe('datetime-local')
    expect(wrapper.get('[name="child-301-row-0-kind"]').element).toBeInstanceOf(HTMLSelectElement)
    expect(wrapper.get('[name="child-301-row-0-tone"]').element).toBeInstanceOf(HTMLFieldSetElement)
    expect(wrapper.get('[name="child-301-row-0-labels"]').element).toBeInstanceOf(HTMLSelectElement)
    expect(wrapper.get('[name="child-301-row-0-primary"]').attributes('type')).toBe('checkbox')
    expect(wrapper.text()).toContain('LEGACY_KIND<script>')
    expect(wrapper.findAll('script')).toHaveLength(0)
    expect(wrapper.get('[data-testid="child-301-add"]').text()).toContain('Add row')
    await wrapper.get('[data-testid="child-301-add"]').trigger('click')
    await wrapper.get('[name="child-301-row-1-email"]').setValue('new@example.com')
    await wrapper.get('[name="child-301-row-1-amount"]').setValue('7')
    await wrapper.get('[name="child-301-row-1-startDate"]').setValue('2026-08-03')
    await wrapper.get('[name="child-301-row-1-changedAt"]').setValue('2026-08-03T11:00')
    await wrapper.get('[name="child-301-row-1-kind"]').setValue('HOME')
    await wrapper.get('[name="child-301-row-1-labels"]').setValue(['PRIMARY'])
    await wrapper.get('[data-testid="child-301-row-1-home-radio"]').setValue()
    await wrapper.get('[name="child-301-row-1-primary"]').setValue(true)
    await wrapper.get('[data-testid="child-301-row-1-up"]').trigger('click')
    await wrapper.get('[data-testid="subtype-tab-302"]').trigger('click')
    expect(wrapper.get('[data-testid="child-302-row-0-delete"]').text()).toContain('Delete')
    await wrapper.get('[data-testid="child-302-row-0-delete"]').trigger('click')
    await wrapper.get('[data-testid="record-save"]').trigger('click')
    await flushPromises()

    expect(recordsApi.updateRecordDraft).toHaveBeenCalledTimes(1)
    expect(recordsApi.updateRecordDraft).toHaveBeenCalledWith(91, {
      recordId: 81,
      masterTypeId: 41,
      baseVersion: 4,
      action: 'UPDATE',
      masterValues: {
        name: 'Laptop fleet',
        quantity: 12,
        purchaseDate: '2026-08-01',
        updatedAt: '2026-08-01T09:30:00',
        stage: 'LEGACY<option>',
        ownerType: 'INTERNAL',
        labels: ['A'],
        enabled: true
      },
      children: [
        {
          subTypeId: 301,
          rows: [
            {
              recordId: null,
              rowOrder: 0,
              values: {
                email: 'new@example.com',
                amount: 7,
                startDate: '2026-08-03',
                changedAt: '2026-08-03T11:00',
                kind: 'HOME',
                tone: 'HOME',
                labels: ['PRIMARY'],
                primary: true
              }
            },
            {
              recordId: 900,
              rowOrder: 1,
              values: {
                email: 'ops@example.com',
                amount: 5,
                startDate: '2026-08-02',
                changedAt: '2026-08-02T10:45:00',
                kind: 'LEGACY_KIND<script>',
                tone: 'HOME',
                labels: ['VIP'],
                primary: true
              }
            }
          ]
        },
        { subTypeId: 302, rows: [] }
      ],
      deleteReason: null
    })
    expect(original.children[0].rows).toHaveLength(1)
    expect(original.children[0].rows[0].values).toMatchObject({ email: 'ops@example.com' })
  })

  it('localizes child table actions in Chinese and English without routing user values through translations', async () => {
    setLocale('zh-CN')
    const { wrapper } = await mountEditor()

    expect(wrapper.get('[data-testid="child-301-add"]').text()).toContain('添加行')
    expect(wrapper.get('[data-testid="child-301-row-0-up"]').text()).toContain('上移')
    expect(wrapper.get('[data-testid="child-301-row-0-down"]').text()).toContain('下移')
    expect(wrapper.get('[data-testid="child-301-row-0-delete"]').text()).toContain('删除')
    expect(wrapper.text()).toContain('LEGACY_KIND<script>')

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.get('[data-testid="child-301-add"]').text()).toContain('Add row')
    expect(wrapper.get('[data-testid="child-301-row-0-up"]').text()).toContain('Up')
    expect(wrapper.get('[data-testid="child-301-row-0-down"]').text()).toContain('Down')
    expect(wrapper.get('[data-testid="child-301-row-0-delete"]').text()).toContain('Delete')
  })

  it('freezes repeated save and submit actions, requires delete reasons, and shows refresh guidance on version conflicts', async () => {
    let resolveSave!: () => void
    recordsApi.updateRecordDraft.mockReturnValueOnce(new Promise((done) => { resolveSave = () => done(structuredClone(draft)) }))
    const { wrapper } = await mountEditor()

    await wrapper.get('[data-testid="record-save"]').trigger('click')
    await wrapper.get('[data-testid="record-save"]').trigger('click')
    expect(recordsApi.updateRecordDraft).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="record-save"]').attributes()).toHaveProperty('disabled')
    resolveSave()
    await flushPromises()

    let resolveSubmit!: (value: void) => void
    recordsApi.submitRecordDraft.mockReturnValueOnce(new Promise((done) => { resolveSubmit = done }))
    await wrapper.get('[data-testid="record-submit"]').trigger('click')
    await wrapper.get('[data-testid="record-submit"]').trigger('click')
    expect(recordsApi.submitRecordDraft).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="record-submit"]').attributes()).toHaveProperty('disabled')
    resolveSubmit(undefined)
    await flushPromises()

    recordsApi.getRecordDraft.mockResolvedValueOnce({ ...structuredClone(draft), id: 92, action: 'DELETE', deleteReason: '' })
    const deletion = await mountEditor()
    expect(deletion.wrapper.get('[data-testid="record-submit"]').attributes()).toHaveProperty('disabled')
    await deletion.wrapper.get('[name="deleteReason"]').setValue('Duplicate record')
    expect(deletion.wrapper.get('[data-testid="record-submit"]').attributes('disabled')).toBeUndefined()

    recordsApi.updateRecordDraft.mockRejectedValueOnce({ status: 409, message: 'Version conflict', requestId: 'req-409' })
    await deletion.wrapper.get('[data-testid="record-save"]').trigger('click')
    await flushPromises()
    expect(deletion.wrapper.get('[role="alert"]').text()).toContain('req-409')
    expect(deletion.wrapper.text()).toContain('Refresh the latest record before saving again')
  })

  it('acquires an edit lock for existing records, renews it before expiry, and releases it on cancel and unmount without persisting the token', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-07T09:00:00.000Z'))
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper } = await mountEditor()

    expect(recordsApi.acquireRecordLock).toHaveBeenCalledWith(81)
    expect(sessionStorage.getItem('lock-token')).toBeNull()
    expect(localStorage.getItem('lock-token')).toBeNull()

    await wrapper.get('[name="field-name"]').setValue('Laptop fleet updated')
    await vi.advanceTimersByTimeAsync(15 * 60 * 1000)
    expect(recordsApi.renewRecordLock).toHaveBeenCalledWith(81, 'lock-token')

    await wrapper.get('[data-testid="record-cancel"]').trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(recordsApi.releaseRecordLock).toHaveBeenCalledWith(81, 'lock-token')

    wrapper.unmount()
    await flushPromises()
    expect(recordsApi.releaseRecordLock).toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('switches to read-only conflict mode when another editor holds the lock and shows the holder and expiry', async () => {
    recordsApi.acquireRecordLock.mockRejectedValueOnce({
      status: 409,
      message: 'Record is being edited by Other Editor until 2026-08-07T09:30:00Z',
      requestId: 'req-lock'
    })
    const { wrapper } = await mountEditor()

    expect(wrapper.get('[role="alert"]').text()).toContain('Other Editor')
    expect(wrapper.get('[role="alert"]').text()).toContain('2026-08-07T09:30:00Z')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-lock')
    expect(wrapper.find('[data-testid="record-save"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="record-submit"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Read-only')
  })
})
