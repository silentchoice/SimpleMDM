"""Simulated downstream system push service."""

import json
import random
import time
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.push_log import SysPushLog
from app.models.personnel import MdmPersonnel
from app.models.approval import WfApproval
from app.models.push_api import SysPushApi

# Session-level counter: first push always succeeds for demo happy path
_push_session_count: int = 0


def _get_active_targets(db: Session) -> list[str]:
    """Get all active push API target systems."""
    q = select(SysPushApi.target_system).where(SysPushApi.status == "active")
    return [row[0] for row in db.execute(q).all()]


def reset_push_counter():
    """Reset the push counter (useful for repeated demos)."""
    global _push_session_count
    _push_session_count = 0


def execute_push(db: Session, approval: WfApproval) -> list[SysPushLog]:
    """Simulate pushing personnel data to all active configured downstream systems."""
    global _push_session_count

    personnel = db.get(MdmPersonnel, approval.personnel_id)
    if not personnel:
        return []

    # Build payload
    payload = {
        "employee_code": personnel.employee_code,
        "name": personnel.name,
        "gender": personnel.gender,
        "department": personnel.department,
        "position": personnel.position,
        "phone": personnel.phone,
        "email": personnel.email,
        "version": personnel.version,
        "change_data": json.loads(approval.change_data) if approval.change_data else {},
    }
    request_json = json.dumps(payload, ensure_ascii=False)

    # Get dynamic targets from configured APIs
    targets = _get_active_targets(db)
    if not targets:
        # Fallback: use config defaults if no APIs configured
        from app.config import settings
        targets = settings.PUSH_TARGET_SYSTEMS

    logs = []
    for target in targets:
        _push_session_count += 1

        # Look up the API config for display name
        api = db.execute(
            select(SysPushApi).where(
                SysPushApi.target_system == target,
                SysPushApi.status == "active",
            )
        ).scalar()
        display_name = api.name if api else target

        log = SysPushLog(
            approval_id=approval.id,
            personnel_id=personnel.id,
            target_system=target,
            status="pending",
            request_body=request_json,
        )
        db.add(log)
        db.flush()

        # Simulate push with ~300ms latency
        time.sleep(0.15)

        # First push in session ALWAYS succeeds (demo happy path)
        if _push_session_count <= 2:
            success = True
        else:
            success = random.random() < 0.9

        if success:
            response = {"code": 200, "message": f"数据已成功同步到 {display_name}"}
            log.status = "success"
            log.response_code = 200
            log.response_body = json.dumps(response, ensure_ascii=False)
        else:
            response = {"code": 500, "message": f"{display_name} 连接超时，请稍后重试"}
            log.status = "failed"
            log.response_code = 500
            log.response_body = json.dumps(response, ensure_ascii=False)
            log.error_message = f"Connection to {display_name} timed out after 30s"

        log.pushed_at = datetime.now(timezone.utc)
        db.flush()
        logs.append(log)

    db.commit()
    return logs


def list_push_logs(
    db: Session,
    target_system: str = "",
    status_filter: str = "",
    page: int = 1,
    page_size: int = 10,
) -> tuple[list[dict], int]:
    from sqlalchemy import select as s, func

    q = s(SysPushLog)
    if target_system:
        q = q.where(SysPushLog.target_system == target_system)
    if status_filter:
        q = q.where(SysPushLog.status == status_filter)

    count_q = s(func.count()).select_from(q.subquery())
    total = db.execute(count_q).scalar() or 0

    q = q.order_by(SysPushLog.id.desc()).offset((page - 1) * page_size).limit(page_size)
    push_logs = list(db.execute(q).scalars().all())

    results = []
    for pl in push_logs:
        personnel = db.get(MdmPersonnel, pl.personnel_id)
        results.append({
            "id": pl.id,
            "approval_id": pl.approval_id,
            "personnel_id": pl.personnel_id,
            "personnel_name": personnel.name if personnel else "",
            "target_system": pl.target_system,
            "status": pl.status,
            "request_body": pl.request_body,
            "response_body": pl.response_body,
            "response_code": pl.response_code,
            "retry_count": pl.retry_count,
            "error_message": pl.error_message,
            "pushed_at": pl.pushed_at,
            "created_at": pl.created_at,
        })
    return results, total


def retry_push(db: Session, log_id: int) -> SysPushLog | None:
    """Retry a failed push. Always succeeds on retry for the demo."""
    log = db.get(SysPushLog, log_id)
    if not log or log.status != "failed":
        return None

    log.retry_count += 1
    log.status = "success"
    log.response_code = 200
    log.response_body = json.dumps(
        {"code": 200, "message": f"重试成功: 数据已同步到 {log.target_system} 系统"},
        ensure_ascii=False,
    )
    log.error_message = None
    log.pushed_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(log)
    return log
