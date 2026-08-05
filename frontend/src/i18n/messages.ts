export const messages = {
  'zh-CN': {
    common: {
      switchLanguage: '切换语言',
      requestId: '请求 ID：{id}'
    },
    auth: {
      console: '管理控制台',
      username: '用户名',
      password: '密码',
      signIn: '登录',
      signingIn: '登录中…',
      required: '请输入用户名和密码',
      localLogout: '已在本地退出，无法确认服务器退出状态。'
    },
    layout: {
      menu: '菜单',
      global: '全局',
      signOut: '退出登录',
      mainNavigation: '主导航'
    }
  },
  'en-US': {
    common: {
      switchLanguage: 'Switch language',
      requestId: 'Request ID: {id}'
    },
    auth: {
      console: 'Management Console',
      username: 'Username',
      password: 'Password',
      signIn: 'Sign in',
      signingIn: 'Signing in…',
      required: 'Username and password are required',
      localLogout: 'Signed out locally. Server sign-out could not be confirmed.'
    },
    layout: {
      menu: 'Menu',
      global: 'Global',
      signOut: 'Sign out',
      mainNavigation: 'Main navigation'
    }
  }
} as const
