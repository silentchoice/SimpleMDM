export function sortedDefinitions(definitions = []) {
  return [...definitions].sort((left, right) => {
    const order = (left.sort_order ?? 0) - (right.sort_order ?? 0)
    return order || String(left.field_key).localeCompare(String(right.field_key))
  })
}

export function buildInitialData(definitions = [], source = {}) {
  const result = {}
  for (const definition of sortedDefinitions(definitions)) {
    const key = definition.field_key
    result[key] = source[key] ?? ''
  }
  return result
}

export function buildRules(definitions = []) {
  const rules = {}
  for (const definition of definitions) {
    if (!definition.required || definition.system_field) continue
    const choice = ['select', 'radio'].includes(definition.field_type)
    rules[definition.field_key] = [{
      required: true,
      message: `${choice ? '请选择' : '请输入'}${definition.field_name}`,
      trigger: choice ? 'change' : 'blur',
    }]
  }
  return rules
}

export function normalizePayload(definitions = [], source = {}) {
  const result = {}
  for (const definition of sortedDefinitions(definitions)) {
    if (definition.system_field) continue
    const key = definition.field_key
    const value = source[key]
    if (value === '' || value === null || value === undefined) continue
    result[key] = definition.field_type === 'number' ? Number(value) : value
  }
  return result
}
