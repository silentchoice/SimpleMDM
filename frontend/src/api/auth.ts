import type { Session } from '../types'
import { http } from './http'

export function login(username: string, password: string): Promise<Session> {
  return http.post<Session>('/auth/login', { username, password })
}

export function logout(): Promise<void> {
  return http.post<void>('/auth/logout')
}
