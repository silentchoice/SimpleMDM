// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import Logs from './Logs.vue'
import { cancelPushLog, getPushLog, listPushLogs, retryPushLog } from '../../api/integration'

vi.mock('../../api/integration', () => ({ cancelPushLog: vi.fn(), getPushLog: vi.fn(), listPushLogs: vi.fn(), retryPushLog: vi.fn() }))

describe('分发日志', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('user', JSON.stringify({ is_admin: false }))
    localStorage.setItem('permissions', '[]')
    listPushLogs.mockResolvedValue({ data: [{ id: 4, event_id: 'evt-4', record_id: 9, status: 'failed', retry_count: 3, trigger_type: 'AUTOMATIC', can_retry: false }] })
    getPushLog.mockResolvedValue({ data: { id: 4, request_snapshot: '{"secret":false}', response_snapshot: '503' } })
    retryPushLog.mockResolvedValue({ data: { log_id: 5 } })
    cancelPushLog.mockResolvedValue({ data: { log_id: 4 } })
  })

  it('shows failed retry only when the server grants the capability', async () => {
    listPushLogs.mockResolvedValue({ data: [{ id: 4, event_id: 'evt-4', record_id: 9, status: 'failed', retry_count: 3, trigger_type: 'AUTOMATIC', can_retry: true }] })
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('[data-test="retry-4"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('失败')
  })

  it('hides retry and snapshot details from viewer and cross-viewer', async () => {
    localStorage.setItem('permissions', JSON.stringify([{ code: 'MDM_RECORD_CROSS_VIEW' }]))
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('[data-test="retry-4"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="detail-4"]').exists()).toBe(false)
  })

  it('does not infer retry capability from local manual-push permissions', async () => {
    localStorage.setItem('permissions', JSON.stringify([{ code: 'INTEGRATION_MANUAL_PUSH' }]))
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('[data-test="retry-4"]').exists()).toBe(false)
  })

  it('allows administrators to load snapshots', async () => {
    localStorage.setItem('user', JSON.stringify({ is_admin: true }))
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.get('[data-test="detail-4"]').trigger('click')
    await flushPromises()
    expect(getPushLog).toHaveBeenCalledWith(4)
    expect(wrapper.text()).toContain('请求快照')
    expect(wrapper.text()).toContain('日志 #4')
  })

  it('guards concurrent detail loads and clears an old snapshot before loading', async () => {
    localStorage.setItem('user', JSON.stringify({ is_admin: true }))
    let resolveDetail
    getPushLog.mockReturnValue(new Promise(resolve => { resolveDetail = resolve }))
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const first = wrapper.vm.showDetail({ id: 4 })
    wrapper.vm.showDetail({ id: 4 })
    expect(getPushLog).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).not.toContain('secret')
    resolveDetail({ data: { id: 4, request_snapshot: 'fresh', response_snapshot: '200' } })
    await first
    await flushPromises()
    expect(wrapper.text()).toContain('fresh')
  })

  it('does not leave a stale snapshot visible when another detail request fails', async () => {
    localStorage.setItem('user', JSON.stringify({ is_admin: true }))
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.showDetail({ id: 4 })
    await flushPromises()
    expect(wrapper.text()).toContain('secret')
    getPushLog.mockRejectedValueOnce(new Error('detail offline'))
    await wrapper.vm.showDetail({ id: 5 })
    await flushPromises()
    expect(wrapper.text()).not.toContain('secret')
    expect(wrapper.text()).toContain('加载分发快照失败')
  })

  it('separates pending queue from distributed history and cancels only server-authorized rows', async () => {
    listPushLogs
      .mockResolvedValueOnce({ data: [
        { id: 4, event_id: 'pending-4', record_id: 9, status: 'PENDING', trigger_type: 'SCHEDULED', can_cancel: true, can_retry: false },
        { id: 5, event_id: 'cancelled-5', record_id: 10, status: 'CANCELLED', trigger_type: 'MANUAL', cancellation_reason: '计划已变更', can_cancel: false, can_retry: false },
      ] })
      .mockResolvedValueOnce({ data: [] })
    const wrapper = mount(Logs, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('待分发队列')
    expect(wrapper.text()).toContain('已分发记录')
    expect(wrapper.text()).toContain('计划已变更')
    expect(wrapper.text()).toContain('定时')
    await wrapper.get('[data-test="cancel-4"]').trigger('click')
    await flushPromises()

    expect(cancelPushLog).toHaveBeenCalledWith(4, { reason: '管理界面取消任务' })
    expect(wrapper.text()).toContain('已取消待分发任务')
  })
})
