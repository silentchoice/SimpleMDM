import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import App from './App.vue'

describe('App', () => {
  it('displays the MDM system title', () => {
    const wrapper = mount(App)

    expect(wrapper.get('h1').text()).toBe('主数据管理系统')
  })
})
