"""Approval workflow state machine."""

import json
from datetime import datetime, timezone

from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.models.approval import WfApproval
from app.models.personnel import MdmPersonnel
from app.models.user import SysUser
from app.schemas.personnel import PersonnelCreate, PersonnelUpdate
from app.services import personnel_service


def create_approval_for_create(
    db: Session,
    submitter_id: int,
    personnel_data: PersonnelCreate,
) -> WfApproval:
    """Create a personnel record as pending_approval, and create a 'create' approval."""
    p = MdmPersonnel(**personnel_data.model_dump(), status="pending_approval")
    db.add(p)
    db.flush()  # get p.id

    change_data = {}
    for field, value in personnel_data.model_dump().items():
        change_data[field] = {"old": None, "new": value}

    approval = WfApproval(
        personnel_id=p.id,
        workflow_type="create",
        submitter_id=submitter_id,
        approver_id=_find_approver(db),
        status="pending",
        change_data=json.dumps(change_data, ensure_ascii=False),
    )
    db.add(approval)
    db.commit()
    db.refresh(approval)
    return approval


def create_approval_for_update(
    db: Session,
    personnel_id: int,
    submitter_id: int,
    data: PersonnelUpdate,
) -> WfApproval | None:
    """Compute diff, mark personnel as pending_approval, create approval. Returns None if no changes."""
    personnel = db.get(MdmPersonnel, personnel_id)
    if not personnel or personnel.status == "pending_approval":
        return None

    diff = personnel_service.compute_diff(personnel, data)
    if diff is None:
        return None

    personnel.status = "pending_approval"

    approval = WfApproval(
        personnel_id=personnel_id,
        workflow_type="update",
        submitter_id=submitter_id,
        approver_id=_find_approver(db),
        status="pending",
        change_data=json.dumps(diff, ensure_ascii=False),
    )
    db.add(approval)
    db.commit()
    db.refresh(approval)
    return approval


def list_approvals(
    db: Session,
    current_user_id: int,
    list_type: str = "all",
    status_filter: str = "",
    page: int = 1,
    page_size: int = 10,
) -> tuple[list[dict], int]:
    """Return approvals with joined user/personnel names."""
    q = select(WfApproval)

    if list_type == "pending_my":
        q = q.where(WfApproval.approver_id == current_user_id, WfApproval.status == "pending")
    elif list_type == "my_submitted":
        q = q.where(WfApproval.submitter_id == current_user_id)
    # else: "all" — no filter

    if status_filter:
        q = q.where(WfApproval.status == status_filter)

    # Count
    count_q = select(func.count()).select_from(q.subquery())
    total = db.execute(count_q).scalar() or 0

    # Paginate
    q = q.order_by(WfApproval.id.desc()).offset((page - 1) * page_size).limit(page_size)
    approvals = list(db.execute(q).scalars().all())

    # Enrich with names
    results = []
    for a in approvals:
        submitter = db.get(SysUser, a.submitter_id)
        approver = db.get(SysUser, a.approver_id) if a.approver_id else None
        personnel = db.get(MdmPersonnel, a.personnel_id)
        results.append({
            "id": a.id,
            "personnel_id": a.personnel_id,
            "personnel_name": personnel.name if personnel else "",
            "workflow_type": a.workflow_type,
            "submitter_id": a.submitter_id,
            "submitter_name": submitter.real_name if submitter else "",
            "approver_id": a.approver_id,
            "approver_name": approver.real_name if approver else "",
            "status": a.status,
            "change_data": a.change_data,
            "submit_time": a.submit_time,
            "approve_time": a.approve_time,
            "approve_comment": a.approve_comment,
            "withdrawn_time": a.withdrawn_time,
            "created_at": a.created_at,
        })
    return results, total


def get_approval_detail(db: Session, approval_id: int) -> dict | None:
    """Get a single approval with all joined data."""
    a = db.get(WfApproval, approval_id)
    if not a:
        return None
    submitter = db.get(SysUser, a.submitter_id)
    approver = db.get(SysUser, a.approver_id) if a.approver_id else None
    personnel = db.get(MdmPersonnel, a.personnel_id)
    return {
        "id": a.id,
        "personnel_id": a.personnel_id,
        "personnel_name": personnel.name if personnel else "",
        "workflow_type": a.workflow_type,
        "submitter_id": a.submitter_id,
        "submitter_name": submitter.real_name if submitter else "",
        "approver_id": a.approver_id,
        "approver_name": approver.real_name if approver else "",
        "status": a.status,
        "change_data": a.change_data,
        "submit_time": a.submit_time,
        "approve_time": a.approve_time,
        "approve_comment": a.approve_comment,
        "withdrawn_time": a.withdrawn_time,
        "created_at": a.created_at,
    }


def approve(db: Session, approval_id: int, comment: str) -> WfApproval | None:
    """Approve: apply changes to personnel, set status, return updated approval."""
    from app.services.push_service import execute_push

    a = db.get(WfApproval, approval_id)
    if not a or a.status != "pending":
        return None

    a.status = "approved"
    a.approve_time = datetime.now(timezone.utc)
    a.approve_comment = comment

    # Apply the change data to personnel
    personnel = db.get(MdmPersonnel, a.personnel_id)
    if personnel and a.change_data:
        personnel_service.apply_changes(db, personnel, a.change_data)

    db.commit()
    db.refresh(a)

    # Trigger simulated push to downstream systems
    execute_push(db, a)

    return a


def reject(db: Session, approval_id: int, comment: str) -> WfApproval | None:
    """Reject: discard changes, reset personnel status, return updated approval."""
    a = db.get(WfApproval, approval_id)
    if not a or a.status != "pending":
        return None

    a.status = "rejected"
    a.approve_time = datetime.now(timezone.utc)
    a.approve_comment = comment

    # If this was an update (not create), revert personnel status
    personnel = db.get(MdmPersonnel, a.personnel_id)
    if personnel:
        if a.workflow_type == "create":
            # For rejected creates, mark the personnel as inactive
            personnel.status = "inactive"
        else:
            personnel.status = "active"

    db.commit()
    db.refresh(a)
    return a


def withdraw(db: Session, approval_id: int, user_id: int) -> WfApproval | None:
    """Withdraw: only allowed if current user is submitter and status is pending."""
    a = db.get(WfApproval, approval_id)
    if not a or a.status != "pending":
        return None
    if a.submitter_id != user_id:
        return None

    a.status = "withdrawn"
    a.withdrawn_time = datetime.now(timezone.utc)

    # Revert personnel status
    personnel = db.get(MdmPersonnel, a.personnel_id)
    if personnel:
        if a.workflow_type == "create":
            personnel.status = "inactive"
        else:
            personnel.status = "active"

    db.commit()
    db.refresh(a)
    return a


def _find_approver(db: Session) -> int | None:
    """Find the first active approver user. In a real system this would use configurable rules."""
    from sqlalchemy import select as s
    approver = db.execute(
        s(SysUser).where(SysUser.role == "approver", SysUser.status == "active").limit(1)
    ).scalar()
    return approver.id if approver else None
