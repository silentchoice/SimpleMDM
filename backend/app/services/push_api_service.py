"""Push API configuration CRUD service."""

import json
import time
from datetime import datetime, timezone

from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.models.push_api import SysPushApi
from app.models.push_log import SysPushLog
from app.models.personnel import MdmPersonnel
from app.schemas.push_api import PushApiCreate, PushApiUpdate


# ── Push API CRUD ────────────────────────────────────────────

def list_apis(
    db: Session,
    keyword: str = "",
    status_filter: str = "",
    page: int = 1,
    page_size: int = 10,
) -> tuple[list[SysPushApi], int]:
    q = select(SysPushApi)
    if keyword:
        q = q.where(
            SysPushApi.name.contains(keyword)
            | SysPushApi.target_system.contains(keyword)
        )
    if status_filter:
        q = q.where(SysPushApi.status == status_filter)

    count_q = select(func.count()).select_from(q.subquery())
    total = db.execute(count_q).scalar() or 0

    q = q.order_by(SysPushApi.id).offset((page - 1) * page_size).limit(page_size)
    items = list(db.execute(q).scalars().all())
    return items, total


def get_api(db: Session, api_id: int) -> SysPushApi | None:
    return db.get(SysPushApi, api_id)


def create_api(db: Session, data: PushApiCreate) -> SysPushApi:
    api = SysPushApi(**data.model_dump())
    db.add(api)
    db.commit()
    db.refresh(api)
    return api


def update_api(db: Session, api_id: int, data: PushApiUpdate) -> SysPushApi | None:
    api = db.get(SysPushApi, api_id)
    if not api:
        return None
    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(api, field, value)
    db.commit()
    db.refresh(api)
    return api


def delete_api(db: Session, api_id: int) -> bool:
    api = db.get(SysPushApi, api_id)
    if not api:
        return False
    # Don't allow deleting if there are push logs referencing it
    has_logs = db.execute(
        select(func.count()).where(SysPushLog.target_system == api.target_system)
    ).scalar() or 0
    if has_logs > 0:
        # Soft-delete: deactivate instead
        api.status = "inactive"
        db.commit()
    else:
        db.delete(api)
        db.commit()
    return True


def get_active_targets(db: Session) -> list[str]:
    """Return list of active target_system codes for push routing."""
    q = select(SysPushApi.target_system).where(SysPushApi.status == "active")
    return [row[0] for row in db.execute(q).all()]


# ── Simulated Push using configured APIs ─────────────────────

def simulate_push_to_target(db: Session, target_system: str, payload: dict) -> SysPushLog:
    """Simulate a push to a specific configured target. Returns a push log entry."""
    api = db.execute(
        select(SysPushApi).where(
            SysPushApi.target_system == target_system,
            SysPushApi.status == "active",
        )
    ).scalar()

    log = SysPushLog(
        approval_id=0,  # system-level push
        personnel_id=0,
        target_system=target_system,
        status="pending",
        request_body=json.dumps(payload, ensure_ascii=False),
    )
    db.add(log)
    db.flush()

    time.sleep(0.15)  # simulate network delay

    if api:
        success = True  # configured APIs always succeed in simulation
        response = {
            "code": 200,
            "message": f"数据已成功推送到 {api.name} ({api.base_url})",
        }
    else:
        success = False
        response = {
            "code": 404,
            "message": f"目标系统 {target_system} 未配置或已停用",
        }

    log.status = "success" if success else "failed"
    log.response_code = response["code"]
    log.response_body = json.dumps(response, ensure_ascii=False)
    if not success:
        log.error_message = response["message"]
    log.pushed_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(log)
    return log


def test_api_connection(db: Session, api_id: int) -> dict:
    """Simulate testing a connection to the configured API."""
    api = db.get(SysPushApi, api_id)
    if not api:
        return {"success": False, "message": "API配置不存在"}

    time.sleep(0.3)  # simulate network test

    return {
        "success": True,
        "message": f"连接成功: {api.name} ({api.base_url})",
        "detail": {
            "url": api.base_url,
            "method": api.method,
            "auth_type": api.auth_type,
            "response_time_ms": 245,
            "status_code": 200,
        },
    }
