import request from '../utils/request'
export const listApprovals=()=>request.get('/workflow/approvals')
export const getApproval=id=>request.get(`/workflow/approvals/${id}`)
export const submitApproval=data=>request.post('/workflow/approvals/submit',data)
export const approve=id=>request.post(`/workflow/approvals/${id}/approve`)
