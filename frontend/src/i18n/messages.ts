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
    metadata: {
      department: {
        title: '部门元数据',
        description: '查看已批准的定义，或提交独立变更以供审批。',
        currentAssignment: '当前分配：{name}',
        currentActive: '当前启用版本',
        submitChanges: '提交变更',
        subType: '子类型',
        selectSubType: '请选择子类型',
        loadFallback: '无法加载当前主数据类型'
      },
      active: {
        ariaLabel: '当前启用版本',
        title: '当前启用版本',
        description: '已批准的元数据为只读。',
        refresh: '刷新',
        noAssignment: '该部门尚未分配主数据类型。',
        masterFields: '主字段'
      },
      templates: {
        title: '主数据类型模板',
        description: '创建可复用的主数据类型模板并分配给部门。',
        create: '创建主数据类型',
        code: '代码',
        name: '名称',
        assignDepartment: '分配部门'
      },
      masterTypeDrawer: {
        ariaLabel: '创建主数据类型',
        title: '创建主数据类型',
        validation: '请输入代码和名称'
      },
      assignment: {
        ariaLabel: '将 {name} 分配给部门',
        title: '将 {name} 分配给部门',
        templateFallback: '模板',
        department: '部门',
        selectDepartment: '请选择部门',
        validation: '请选择部门',
        assigning: '分配中…',
        assign: '分配'
      },
      editor: {
        masterFields: '主字段',
        subTypes: '子类型',
        subFields: '子字段',
        add: '添加',
        remove: '移除',
        up: '上移',
        down: '下移',
        submit: '提交{family}',
        taskSubmitted: '审批任务 #{id} 已提交。当前启用的元数据未发生变化。',
        duplicateCode: '代码重复',
        duplicateSortOrder: '排序序号重复',
        unableSubmit: '无法提交变更'
      },
      fieldEditor: {
        ariaLabel: '编辑元数据项',
        field: '字段',
        subType: '子类型',
        code: '代码',
        name: '名称',
        type: '类型',
        selectType: '请选择类型',
        required: '必填',
        shared: '共享',
        options: '选项（以逗号分隔）',
        saveDraft: '保存草稿',
        invalidCode: '代码必须以字母开头，且只能包含字母、数字或下划线',
        nameRequired: '名称为必填项',
        fieldTypeRequired: '字段类型为必填项',
        optionsRequired: '选择类字段必须提供选项',
        optionsUnique: '选项不能重复'
      },
      fieldTypes: {
        TEXT: '文本',
        NUMBER: '数字',
        DATE: '日期',
        DATETIME: '日期时间',
        SELECT: '下拉选择',
        RADIO: '单选',
        MULTISELECT: '多选',
        SWITCH: '开关'
      }
    },
    approval: {
      list: {
        title: '元数据审批',
        description: '审核您所在部门的元数据变更。',
        filterStatus: '状态',
        loading: '正在加载审批…',
        empty: '没有符合此状态的元数据审批任务。',
        task: '任务',
        metadataKind: '元数据类型',
        entity: '实体',
        submitted: '提交时间'
      },
      detail: {
        back: '返回审批列表',
        loading: '正在加载审批…',
        notFound: '未找到审批任务',
        title: '元数据审批 #{id}',
        kind: '类型',
        entity: '实体',
        submittedBy: '提交人',
        submittedAt: '提交时间',
        reviewedBy: '审核人',
        reviewedAt: '审核时间',
        reviewComment: '审核意见'
      },
      actions: {
        ariaLabel: '审批操作',
        approvalComment: '批准意见（可选）',
        approve: '批准',
        rejectionReason: '拒绝原因',
        reject: '拒绝',
        rejectionRequired: '拒绝原因为必填项'
      },
      diff: {
        ariaLabel: '快照差异',
        malformed: '无法显示快照差异：快照数据格式错误。',
        unsupported: '不支持此快照架构版本。请查看下方原始 JSON。',
        before: '变更前',
        after: '变更后',
        beforePosition: '变更前位置：{position}',
        afterPosition: '变更后位置：{position}',
        states: {
          added: '新增',
          removed: '删除',
          modified: '修改',
          unchanged: '未变化'
        }
      },
      entityKinds: {
        MASTER_FIELDS: '主字段',
        SUB_TYPES: '子类型',
        SUB_FIELDS: '子字段'
      }
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
    metadata: {
      department: {
        title: 'Department metadata',
        description: 'Inspect approved definitions or submit an independent change for approval.',
        currentAssignment: 'Current assignment: {name}',
        currentActive: 'Current active version',
        submitChanges: 'Submit changes',
        subType: 'Sub-type',
        selectSubType: 'Select a sub-type',
        loadFallback: 'Unable to load current master type'
      },
      active: {
        ariaLabel: 'Current active version',
        title: 'Current active version',
        description: 'Approved metadata is read-only.',
        refresh: 'Refresh',
        noAssignment: 'No master type is assigned to this department.',
        masterFields: 'Master fields'
      },
      templates: {
        title: 'Master Type Templates',
        description: 'Create reusable master-type templates and assign them to departments.',
        create: 'Create master type',
        code: 'Code',
        name: 'Name',
        assignDepartment: 'Assign department'
      },
      masterTypeDrawer: {
        ariaLabel: 'Create master type',
        title: 'Create master type',
        validation: 'Code and name are required'
      },
      assignment: {
        ariaLabel: 'Assign {name} to department',
        title: 'Assign {name} to department',
        templateFallback: 'template',
        department: 'Department',
        selectDepartment: 'Select a department',
        validation: 'Select a department',
        assigning: 'Assigning…',
        assign: 'Assign'
      },
      editor: {
        masterFields: 'Master fields',
        subTypes: 'Sub-types',
        subFields: 'Sub-fields',
        add: 'Add',
        remove: 'Remove',
        up: 'Up',
        down: 'Down',
        submit: 'Submit {family}',
        taskSubmitted: 'Approval task #{id} submitted. ACTIVE metadata is unchanged.',
        duplicateCode: 'Duplicate code',
        duplicateSortOrder: 'Duplicate sort order',
        unableSubmit: 'Unable to submit changes'
      },
      fieldEditor: {
        ariaLabel: 'Edit metadata item',
        field: 'Field',
        subType: 'Sub-type',
        code: 'Code',
        name: 'Name',
        type: 'Type',
        selectType: 'Select a type',
        required: 'Required',
        shared: 'Shared',
        options: 'Options (comma-separated)',
        saveDraft: 'Save draft',
        invalidCode: 'Code must start with a letter and contain only letters, numbers, or underscores',
        nameRequired: 'Name is required',
        fieldTypeRequired: 'Field type is required',
        optionsRequired: 'Options are required for selection fields',
        optionsUnique: 'Options must be unique'
      },
      fieldTypes: {
        TEXT: 'Text',
        NUMBER: 'Number',
        DATE: 'Date',
        DATETIME: 'Date and time',
        SELECT: 'Select',
        RADIO: 'Radio',
        MULTISELECT: 'Multi-select',
        SWITCH: 'Switch'
      }
    },
    approval: {
      list: {
        title: 'Metadata approvals',
        description: 'Review metadata changes for your department.',
        filterStatus: 'Status',
        loading: 'Loading approvals…',
        empty: 'No metadata approval tasks match this status.',
        task: 'Task',
        metadataKind: 'Metadata kind',
        entity: 'Entity',
        submitted: 'Submitted'
      },
      detail: {
        back: 'Back to approvals',
        loading: 'Loading approval…',
        notFound: 'Approval task not found',
        title: 'Metadata approval #{id}',
        kind: 'Kind',
        entity: 'Entity',
        submittedBy: 'Submitted by',
        submittedAt: 'Submitted at',
        reviewedBy: 'Reviewed by',
        reviewedAt: 'Reviewed at',
        reviewComment: 'Review comment'
      },
      actions: {
        ariaLabel: 'Approval actions',
        approvalComment: 'Approval comment (optional)',
        approve: 'Approve',
        rejectionReason: 'Rejection reason',
        reject: 'Reject',
        rejectionRequired: 'Rejection reason is required'
      },
      diff: {
        ariaLabel: 'Snapshot differences',
        malformed: 'Unable to display snapshot diff: malformed snapshot data.',
        unsupported: 'Unsupported snapshot schema version. Review the raw JSON below.',
        before: 'Before',
        after: 'After',
        beforePosition: 'Before position: {position}',
        afterPosition: 'After position: {position}',
        states: {
          added: 'Added',
          removed: 'Removed',
          modified: 'Modified',
          unchanged: 'Unchanged'
        }
      },
      entityKinds: {
        MASTER_FIELDS: 'Master fields',
        SUB_TYPES: 'Sub-types',
        SUB_FIELDS: 'Sub-fields'
      }
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
