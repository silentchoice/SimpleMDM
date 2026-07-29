"""Push log endpoints."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import SysUser
from app.api.deps import get_current_user, require_role
from app.services import push_service

router = APIRouter(prefix="/api/push-logs", tags=["推送日志"])


@router.get("", response_model=dict)
def list_push_logs(
    target_system: str = Query("", description="目标系统筛选"),
    status: str = Query("", description="状态筛选"),
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """List push logs with filters."""
    items, total = push_service.list_push_logs(db, target_system, status, page, page_size)
    for item in items:
        for dt_field in ["pushed_at", "created_at"]:
            if item.get(dt_field):
                item[dt_field] = str(item[dt_field])
    return {
        "code": 200,
        "message": "ok",
        "data": {"items": items, "total": total, "page": page, "page_size": page_size},
    }


@router.post("/{log_id}/retry", response_model=dict)
def retry_push(
    log_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator")),
):
    """Retry a failed push."""
    log = push_service.retry_push(db, log_id)
    if not log:
        raise HTTPException(status_code=400, detail="推送日志不存在或状态不是失败")
    return {"code": 200, "message": f"重试成功: 数据已同步到 {log.target_system}", "data": {"id": log.id, "status": log.status}}
