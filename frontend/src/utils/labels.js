const STATUS = {
  PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回',
  SUCCESS: '成功', SUCCEEDED: '成功', FAILED: '失败', RUNNING: '分发中', PROCESSING: '处理中',
  CANCELLED: '已取消',
  pending: '待分发', succeeded: '成功', failed: '失败', processing: '处理中',
  ACTIVE: '启用', INACTIVE: '停用', active: '启用', inactive: '停用',
}
const OPERATION = { CREATE: '新增', UPDATE: '更新', DELETE: '删除' }
const EVENT = {
  RECORD_CHANGED: '记录变更', RECORD_CREATED: '记录新增',
  RECORD_UPDATED: '记录更新', RECORD_DELETED: '记录删除',
}
const TRIGGER = { AUTOMATIC: '审批自动', MANUAL: '手动', RETRY: '重试', SCHEDULED: '定时' }
const FIELD = {
  employee_code: '员工编号', employee_name: '姓名', gender: '性别', birth_date: '出生日期',
  mobile_phone: '手机号', work_email: '工作邮箱', hire_date: '入职日期', employment_status: '在职状态',
  company: '兼职单位', position: '兼职岗位', start_date: '开始日期', end_date: '结束日期',
  part_time_type: '兼职类型', monthly_income: '月收入', notes: '备注',
}

const unknown = (kind, code) => code == null || code === '' ? '—' : `未知${kind}（${code}）`
export const statusLabel = code => STATUS[code] || unknown('状态', code)
export const operationLabel = code => OPERATION[code] || unknown('操作', code)
export const eventLabel = code => EVENT[code] || unknown('事件', code)
export const triggerLabel = code => TRIGGER[code] || unknown('触发方式', code)
export const fieldLabel = code => FIELD[code] || unknown('字段', code)

export function chineseError(error, fallback = '操作失败') {
  const message = error?.response?.data?.detail || error?.response?.data?.message || error?.message
  return typeof message === 'string' && /[\u3400-\u9fff]/.test(message) ? message : fallback
}

export function hasPermission(code) {
  const permissions = JSON.parse(localStorage.getItem('permissions') || '[]')
  return permissions.some(item => item.code === code || item.permission_code === code)
}

export function permissionCapability(code, capability) {
  const permissions = JSON.parse(localStorage.getItem('permissions') || '[]')
  return permissions.some(item =>
    (item.code === code || item.permission_code === code) && item[capability] === true)
}

export function permissionAllowsDepartment(code, departmentId) {
  const permissions = JSON.parse(localStorage.getItem('permissions') || '[]')
  return permissions.some(item => {
    if (item.code !== code && item.permission_code !== code) return false
    if (Array.isArray(item.editable_department_ids)) {
      return item.editable_department_ids.map(Number).includes(Number(departmentId))
    }
    if (item.can_edit != null) return item.can_edit === true
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return Number(user.department_id) === Number(departmentId)
  })
}
