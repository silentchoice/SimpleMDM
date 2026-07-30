import { describe, expect, it } from 'vitest'
import { buildInitialData, buildRules, normalizePayload, sortedDefinitions } from './dynamicFields'

const definitions = [
  { field_key: 'age', field_name: '年龄', field_type: 'number', required: false, sort_order: 2 },
  { field_key: 'name', field_name: '姓名', field_type: 'string', required: true, sort_order: 1 },
  { field_key: 'level', field_name: '职级', field_type: 'select', required: true, sort_order: 3, options: ['P1', 'P2'] },
]

describe('dynamic field helpers', () => {
  it('uses field_key and preserves zero values', () => {
    expect(buildInitialData(definitions, { age: 0, name: '张三' })).toEqual({
      name: '张三',
      age: 0,
      level: '',
    })
  })

  it('builds required rules by stable key', () => {
    const rules = buildRules(definitions)
    expect(rules.name[0].required).toBe(true)
    expect(rules.level[0].message).toBe('请选择职级')
    expect(rules.age).toBeUndefined()
  })

  it('normalizes numbers and drops unknown keys', () => {
    expect(normalizePayload(definitions, {
      name: '张三',
      age: '20.5',
      level: 'P2',
      unknown: 'x',
    })).toEqual({ name: '张三', age: 20.5, level: 'P2' })
  })

  it('sorts without mutating the source array', () => {
    const result = sortedDefinitions(definitions)
    expect(result.map(field => field.field_key)).toEqual(['name', 'age', 'level'])
    expect(definitions[0].field_key).toBe('age')
  })
})
