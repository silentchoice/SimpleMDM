export const recordMessages = {
  'zh-CN': {
    record: {
      filters: {
        recordCode: '编码',
        keyword: '关键字',
        status: '状态',
        all: '全部',
        includeDeleted: '包含已删除',
        search: '查询'
      },
      child: {
        addRow: '添加行',
        up: '上移',
        down: '下移',
        delete: '删除'
      },
      history: {
        version: '版本 {version}'
      },
      status: {
        ACTIVE: '启用',
        DELETED: '已删除',
        DRAFT: '草稿',
        APPROVED: '已批准',
        REJECTED: '已驳回',
        PENDING: '待审批'
      },
      list: {
        title: '业务数据',
        description: '按当前启用元数据查询和维护业务数据。',
        createDraft: '创建草稿',
        recordCode: '编码',
        view: '查看',
        nextPage: '下一页'
      },
      detail: {
        title: '业务数据详情',
        description: '查看当前版本、差异和历史。',
        editDraft: '编辑',
        deleteReason: '删除原因',
        requestDelete: '发起删除',
        tabs: {
          current: '当前',
          diff: '差异',
          history: '历史'
        }
      },
      editor: {
        title: '业务数据草稿',
        description: '根据当前启用元数据编辑主表和子表。',
        tabs: {
          current: '当前',
          draft: '草稿',
          history: '历史'
        },
        required: '为必填项',
        submit: '提交',
        deleteReasonRequired: '删除原因为必填项',
        refreshGuidance: '请先刷新最新正式记录后再保存。',
        discardConfirm: '草稿尚未保存，确定离开吗？',
        readOnly: '只读'
      }
    }
  },
  'en-US': {
    record: {
      filters: {
        recordCode: 'Record code',
        keyword: 'Keyword',
        status: 'Status',
        all: 'All',
        includeDeleted: 'Include deleted',
        search: 'Search'
      },
      child: {
        addRow: 'Add row',
        up: 'Up',
        down: 'Down',
        delete: 'Delete'
      },
      history: {
        version: 'Version {version}'
      },
      status: {
        ACTIVE: 'Active',
        DELETED: 'Deleted',
        DRAFT: 'Draft',
        APPROVED: 'Approved',
        REJECTED: 'Rejected',
        PENDING: 'Pending'
      },
      list: {
        title: 'Business Data',
        description: 'Query and maintain business records from ACTIVE metadata.',
        createDraft: 'Create draft',
        recordCode: 'Record code',
        view: 'View',
        nextPage: 'Next page'
      },
      detail: {
        title: 'Business data detail',
        description: 'Review the current version, diff, and history.',
        editDraft: 'Edit',
        deleteReason: 'Delete reason',
        requestDelete: 'Request delete',
        tabs: {
          current: 'Current',
          diff: 'Diff',
          history: 'History'
        }
      },
      editor: {
        title: 'Business data draft',
        description: 'Edit master and child data from ACTIVE metadata.',
        tabs: {
          current: 'Current',
          draft: 'Draft',
          history: 'History'
        },
        required: 'is required',
        submit: 'Submit',
        deleteReasonRequired: 'Delete reason is required',
        refreshGuidance: 'Refresh the latest record before saving again',
        discardConfirm: 'You have unsaved changes. Leave this draft?',
        readOnly: 'Read-only'
      }
    }
  }
} as const
