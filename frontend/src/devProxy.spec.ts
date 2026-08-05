// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { viteConfig } from '../vite.config'

describe('Vite development proxy', () => {
  it('forwards API requests to the backend', () => {
    expect(viteConfig.server?.proxy?.['/api']).toMatchObject({
      target: 'http://127.0.0.1:8080',
      changeOrigin: true
    })
  })
})
