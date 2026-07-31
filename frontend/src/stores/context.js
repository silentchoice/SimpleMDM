import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { listSystems } from '../api/systems'
import { getDepartmentTree } from '../api/departments'

export const useContextStore = defineStore('mdm-context', () => {
  const systems = ref([])
  const departments = ref([])
  const systemId = ref(null)
  const systemCode = ref('')
  const objectCode = ref('')
  const departmentId = ref(null)
  const query = computed(() => ({
    ...(systemCode.value ? { system: systemCode.value } : {}),
    ...(objectCode.value ? { object: objectCode.value } : {}),
    ...(departmentId.value == null ? {} : { department: String(departmentId.value) }),
  }))

  async function initialize(user = {}, routeQuery = {}) {
    const [systemResponse, departmentResponse] = await Promise.all([listSystems(), getDepartmentTree()])
    systems.value = systemResponse.data || []
    departments.value = departmentResponse.data || []
    const selected = systems.value.find(item => item.code === routeQuery.system)
      || systems.value.find(item => item.id === user.system_id)
    systemId.value = selected?.id ?? user.system_id ?? null
    systemCode.value = selected?.code ?? routeQuery.system ?? ''
    objectCode.value = routeQuery.object || objectCode.value
    const visibleIds = []
    const collectIds = nodes => nodes.forEach(node => {
      visibleIds.push(Number(node.id))
      collectIds(node.children || [])
    })
    collectIds(departments.value)
    const queryDepartment = Number(routeQuery.department)
    const primary = Number(user.department_id)
    departmentId.value = visibleIds.includes(queryDepartment)
      ? queryDepartment
      : (visibleIds.includes(primary) ? primary : (visibleIds[0] ?? null))
  }

  function select({ system, object, department } = {}) {
    if (system !== undefined) {
      const match = systems.value.find(item => item.code === system || item.id === system)
      systemId.value = match?.id ?? null
      systemCode.value = match?.code ?? ''
    }
    if (object !== undefined) objectCode.value = object
    if (department !== undefined) departmentId.value = department == null ? null : Number(department)
  }
  return { systems, departments, systemId, systemCode, objectCode, departmentId, query, initialize, select }
})
