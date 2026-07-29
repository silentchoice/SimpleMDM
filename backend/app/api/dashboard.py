"""Dashboard stats endpoints."""

from fastapi import APIRouter, Depends
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.personnel import MdmPersonnel
from app.models.approval import WfApproval
from app.models.push_log import SysPushLog
from app.models.user import SysUser
from app.api.deps import get_current_user

router = APIRouter(prefix="/api/dashboard", tags=["仪表盘"])


@router.get("/stats", response_model=dict)
def get_stats(
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """Return dashboard statistics."""
    total_personnel = db.execute(
        select(func.count()).select_from(MdmPersonnel)
    ).scalar() or 0

    pending_approvals = db.execute(
        select(func.count()).where(WfApproval.status == "pending")
    ).scalar() or 0

    total_pushes = db.execute(
        select(func.count()).select_from(SysPushLog)
    ).scalar() or 0

    success_pushes = db.execute(
        select(func.count()).where(SysPushLog.status == "success")
    ).scalar() or 0

    push_success_rate = round(success_pushes / total_pushes * 100, 1) if total_pushes > 0 else 100.0

    # Recent 5 approvals
    recent_approvals_q = (
        select(WfApproval).order_by(WfApproval.id.desc()).limit(5)
    )
    recent = []
    for a in db.execute(recent_approvals_q).scalars().all():
        submitter = db.get(SysUser, a.submitter_id)
        approver = db.get(SysUser, a.approver_id) if a.approver_id else None
        personnel = db.get(MdmPersonnel, a.personnel_id)
        recent.append({
            "id": a.id,
            "personnel_name": personnel.name if personnel else "",
            "workflow_type": a.workflow_type,
            "submitter_name": submitter.real_name if submitter else "",
            "approver_name": approver.real_name if approver else "",
            "status": a.status,
            "submit_time": str(a.submit_time) if a.submit_time else "",
        })

    return {
        "code": 200,
        "message": "ok",
        "data": {
            "total_personnel": total_personnel,
            "pending_approvals": pending_approvals,
            "push_success_rate": push_success_rate,
            "recent_approvals": recent,
        },
    }
