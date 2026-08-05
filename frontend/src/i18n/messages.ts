export const messages = {
  'zh-CN': {
    common: {
      switchLanguage: '切换语言',
      requestId: '请求 ID：{id}',
      apiError: '{message}（{requestId}）',
      cancel: '取消',
      save: '保存',
      saving: '保存中…',
      edit: '编辑',
      actions: '操作',
      status: '状态',
      activate: '启用',
      deactivate: '停用',
      disable: '禁用',
      global: '全局',
      loading: '加载中…',
      empty: '暂无数据'
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
    },
    routes: {
      login: '登录',
      dashboard: '仪表盘',
      activeMetadata: '当前元数据',
      submitChange: '提交变更',
      approvals: '审批中心',
      approvalDetail: '审批详情',
      masterTypeTemplates: '主数据类型模板',
      users: '用户管理',
      departments: '部门管理',
      roles: '角色管理',
      forbidden: '拒绝访问',
      notFound: '页面未找到'
    },
    menu: {
      dashboard: '仪表盘',
      activeMetadata: '当前元数据',
      submitChange: '提交变更',
      approvals: '审批中心',
      masterTypeTemplates: '主数据类型模板',
      users: '用户管理',
      departments: '部门管理',
      roles: '角色管理'
    },
    dashboard: {
      title: '仪表盘',
      description: '请从导航中选择一个区域来管理主数据。'
    },
    errors: {
      forbiddenTitle: '拒绝访问',
      forbiddenDescription: '您无权访问此页面。',
      notFoundTitle: '页面未找到',
      notFoundDescription: '请求的页面不存在。'
    },
    status: {
      ACTIVE: '启用',
      DISABLED: '停用',
      PENDING: '待审批',
      APPROVED: '已批准',
      REJECTED: '已拒绝'
    },
    system: {
      departments: {
        title: '部门管理',
        description: '管理部门及其访问状态。',
        create: '创建部门',
        createDialog: '创建部门',
        editDialog: '编辑部门',
        code: '代码',
        name: '名称',
        validation: '请输入代码和名称',
        activateConfirm: '确定要启用 {name} 吗？',
        deactivateConfirm: '确定要停用 {name} 吗？',
        disableConfirm: '确定要禁用 {name} 吗？'
      },
      users: {
        title: '用户管理',
        description: '管理用户账户、部门和固定角色。',
        create: '创建用户',
        createDialog: '创建用户',
        editDialog: '编辑用户',
        username: '用户名',
        password: '密码',
        displayName: '显示名称',
        department: '部门',
        departmentId: '部门 ID',
        roles: '角色',
        validationCreate: '请输入用户名、密码和显示名称',
        validationEdit: '请输入显示名称',
        activeDepartmentRequired: '请选择启用的部门后再保存',
        activateConfirm: '确定要启用 {username} 吗？',
        deactivateConfirm: '确定要停用 {username} 吗？'
      },
      roles: {
        title: '角色管理',
        description: '可供用户分配的固定角色。',
        role: '角色'
      }
    }
  },
  'en-US': {
    common: {
      switchLanguage: 'Switch language',
      requestId: 'Request ID: {id}',
      apiError: '{message} ({requestId})',
      cancel: 'Cancel',
      save: 'Save',
      saving: 'Saving…',
      edit: 'Edit',
      actions: 'Actions',
      status: 'Status',
      activate: 'Activate',
      deactivate: 'Deactivate',
      disable: 'Disable',
      global: 'Global',
      loading: 'Loading…',
      empty: 'No data'
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
    },
    routes: {
      login: 'Sign in',
      dashboard: 'Dashboard',
      activeMetadata: 'Active Metadata',
      submitChange: 'Submit Change',
      approvals: 'Approvals',
      approvalDetail: 'Approval detail',
      masterTypeTemplates: 'Master Type Templates',
      users: 'Users',
      departments: 'Departments',
      roles: 'Roles',
      forbidden: 'Access denied',
      notFound: 'Page not found'
    },
    menu: {
      dashboard: 'Dashboard',
      activeMetadata: 'Active Metadata',
      submitChange: 'Submit Change',
      approvals: 'Approvals',
      masterTypeTemplates: 'Master Type Templates',
      users: 'Users',
      departments: 'Departments',
      roles: 'Roles'
    },
    dashboard: {
      title: 'Dashboard',
      description: 'Choose an area from the navigation to manage master data.'
    },
    errors: {
      forbiddenTitle: 'Access denied',
      forbiddenDescription: 'You do not have access to this page.',
      notFoundTitle: 'Page not found',
      notFoundDescription: 'The requested page does not exist.'
    },
    status: {
      ACTIVE: 'Active',
      DISABLED: 'Disabled',
      PENDING: 'Pending',
      APPROVED: 'Approved',
      REJECTED: 'Rejected'
    },
    system: {
      departments: {
        title: 'Departments',
        description: 'Manage departments and their access state.',
        create: 'Create department',
        createDialog: 'Create department',
        editDialog: 'Edit department',
        code: 'Code',
        name: 'Name',
        validation: 'Code and name are required',
        activateConfirm: 'Activate {name}?',
        deactivateConfirm: 'Deactivate {name}?',
        disableConfirm: 'Disable {name}?'
      },
      users: {
        title: 'Users',
        description: 'Manage user accounts, departments, and fixed roles.',
        create: 'Create user',
        createDialog: 'Create user',
        editDialog: 'Edit user',
        username: 'Username',
        password: 'Password',
        displayName: 'Display name',
        department: 'Department',
        departmentId: 'Department ID',
        roles: 'Roles',
        validationCreate: 'Username, password, and display name are required',
        validationEdit: 'Display name is required',
        activeDepartmentRequired: 'Select an active department before saving',
        activateConfirm: 'Activate {username}?',
        deactivateConfirm: 'Deactivate {username}?'
      },
      roles: {
        title: 'Roles',
        description: 'Fixed roles available for user assignment.',
        role: 'Role'
      }
    }
  }
} as const
