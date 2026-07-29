"""Approval workflow endpoints."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import SysUser
from app.schemas.approval import ApprovalAction
from app.api.deps import get_current_user, require_role
from app.services import approval_service

router = APIRouter(prefix="/api/approvals", tags=["审批管理"])


@router.get("", response_model=dict)
def list_approvals(
    list_type: str = Query("all", description="pending_my | my_submitted | all"),
    status: str = Query("", description="状态筛选"),
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """List approvals with filters."""
    items, total = approval_service.list_approvals(
        db, current_user.id, list_type, status, page, page_size
    )
    # Convert datetimes to strings
    for item in items:
        for dt_field in ["submit_time", "approve_time", "withdrawn_time", "created_at"]:
            if item.get(dt_field):
                item[dt_field] = str(item[dt_field])

    return {
        "code": 200,
        "message": "ok",
        "data": {"items": items, "total": total, "page": page, "page_size": page_size},
    }


@router.get("/{approval_id}", response_model=dict)
def get_approval(
    approval_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """Get single approval detail with change comparison."""
    detail = approval_service.get_approval_detail(db, approval_id)
    if not detail:
        raise HTTPException(status_code=404, detail="审批不存在")
    for dt_field in ["submit_time", "approve_time", "withdrawn_time", "created_at"]:
        if detail.get(dt_field):
            detail[dt_field] = str(detail[dt_field])
    return {"code": 200, "message": "ok", "data": detail}


@router.post("/{approval_id}/approve", response_model=dict)
def approve(
    approval_id: int,
    body: ApprovalAction,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("approver")),
):
    """Approve an approval — applies changes to personnel and triggers push."""
    a = approval_service.approve(db, approval_id, body.comment)
    if not a:
        raise HTTPException(status_code=400, detail="审批不存在或状态不是待审批")
    return {"code": 200, "message": "审批已通过，数据已生效并推送至下游系统", "data": {"id": a.id, "status": a.status}}


@router.post("/{approval_id}/reject", response_model=dict)
def reject(
    approval_id: int,
    body: ApprovalAction,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("approver")),
):
    """Reject an approval — discards changes."""
    a = approval_service.reject(db, approval_id, body.comment)
    if not a:
        raise HTTPException(status_code=400, detail="审批不存在或状态不是待审批")
    return {"code": 200, "message": "审批已驳回", "data": {"id": a.id, "status": a.status}}


@router.post("/{approval_id}/withdraw", response_model=dict)
def withdraw(
    approval_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator")),
):
    """Withdraw a pending approval (submitter only)."""
    a = approval_service.withdraw(db, approval_id, current_user.id)
    if not a:
        raise HTTPException(status_code=400, detail="审批不存在、状态不是待审批、或非本人提交")
    return {"code": 200, "message": "审批已撤回", "data": {"id": a.id, "status": a.status}}
