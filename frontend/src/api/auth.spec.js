import { describe, expect, it, vi } from 'vitest'
import request from '../utils/request'
import { login } from './auth'

vi.mock('../utils/request', () => ({
  default: { post: vi.fn(), get: vi.fn() },
}))

describe('auth api', () => {
  it('sends the tenant system code with login credentials', () => {
    login('ERP', 'operator', 'secret')

    expect(request.post).toHaveBeenCalledWith('/auth/login', {
      system_code: 'ERP',
      username: 'operator',
      password: 'secret',
    })
  })
})
