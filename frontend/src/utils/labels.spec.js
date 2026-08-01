// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { eventLabel, fieldLabel, operationLabel, permissionAllowsDepartment, statusLabel, triggerLabel } from './labels'

describe('中文业务标签', () => {
  it('maps backend codes for display without changing the codes', () => {
    expect(statusLabel('PENDING')).toBe('待审批')
    expect(statusLabel('FAILED')).toBe('失败')
    expect(statusLabel('failed')).toBe('失败')
    expect(operationLabel('DELETE')).toBe('删除')
    expect(eventLabel('RECORD_UPDATED')).toBe('记录更新')
    expect(triggerLabel('MANUAL')).toBe('手动')
    expect(eventLabel('CUSTOM_CODE')).toBe('未知事件（CUSTOM_CODE）')
  })

  it('shows Chinese field names while retaining unknown business codes', () => {
    expect(fieldLabel('company')).toBe('兼职单位')
    expect(fieldLabel('custom_key')).toBe('未知字段（custom_key）')
  })

  it('does not treat an unscoped permission code as cross-department authority', () => {
    localStorage.setItem('user', JSON.stringify({ department_id: 20 }))
    localStorage.setItem('permissions', JSON.stringify([{ code: 'INTEGRATION_MANUAL_PUSH' }]))
    expect(permissionAllowsDepartment('INTEGRATION_MANUAL_PUSH', 10)).toBe(false)
    expect(permissionAllowsDepartment('INTEGRATION_MANUAL_PUSH', 20)).toBe(true)
  })
})
