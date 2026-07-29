"""Push API management endpoints — CRUD for downstream API configs."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import SysUser
from app.schemas.push_api import PushApiCreate, PushApiUpdate
from app.api.deps import get_current_user, require_role
from app.services import push_api_service

router = APIRouter(prefix="/api/push-apis", tags=["API管理"])


@router.get("", response_model=dict)
def list_apis(
    keyword: str = Query("", description="搜索关键词"),
    status: str = Query("", description="状态筛选: active/inactive"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """List all configured push API endpoints."""
    items, total = push_api_service.list_apis(db, keyword, status, page, page_size)
    return {
        "code": 200,
        "message": "ok",
        "data": {
            "items": [
                {
                    "id": a.id,
                    "name": a.name,
                    "target_system": a.target_system,
                    "method": a.method,
                    "base_url": a.base_url,
                    "auth_type": a.auth_type,
                    "auth_config": a.auth_config,
                    "status": a.status,
                    "description": a.description,
                    "retry_max": a.retry_max,
                    "timeout_sec": a.timeout_sec,
                    "created_at": str(a.created_at),
                    "updated_at": str(a.updated_at),
                }
                for a in items
            ],
            "total": total,
            "page": page,
            "page_size": page_size,
        },
    }


@router.get("/active", response_model=dict)
def list_active_apis(
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """Get list of active target_system codes (used by push service)."""
    targets = push_api_service.get_active_targets(db)
    return {"code": 200, "message": "ok", "data": targets}


@router.get("/{api_id}", response_model=dict)
def get_api(
    api_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """Get single API configuration detail."""
    api = push_api_service.get_api(db, api_id)
    if not api:
        raise HTTPException(status_code=404, detail="API配置不存在")
    return {
        "code": 200,
        "message": "ok",
        "data": {
            "id": api.id,
            "name": api.name,
            "target_system": api.target_system,
            "method": api.method,
            "base_url": api.base_url,
            "auth_type": api.auth_type,
            "auth_config": api.auth_config,
            "status": api.status,
            "description": api.description,
            "retry_max": api.retry_max,
            "timeout_sec": api.timeout_sec,
            "created_at": str(api.created_at),
            "updated_at": str(api.updated_at),
        },
    }


@router.post("", response_model=dict)
def create_api(
    body: PushApiCreate,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator", "approver")),
):
    """Create a new push API configuration."""
    from app.models.push_api import SysPushApi
    from sqlalchemy import select as s

    existing = db.execute(
        s(SysPushApi).where(SysPushApi.target_system == body.target_system)
    ).scalar()
    if existing:
        raise HTTPException(status_code=400, detail=f"目标系统 {body.target_system} 已存在")

    api = push_api_service.create_api(db, body)

    # Simulate sync push for the new API config itself
    push_log = push_api_service.simulate_push_to_target(
        db, body.target_system,
        {"action": "api_registered", "target_system": body.target_system, "base_url": body.base_url},
    )

    return {
        "code": 200,
        "message": "API配置已创建并同步推送",
        "data": {
            "id": api.id,
            "target_system": api.target_system,
            "push_log_id": push_log.id,
        },
    }


@router.put("/{api_id}", response_model=dict)
def update_api(
    api_id: int,
    body: PushApiUpdate,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator", "approver")),
):
    """Update a push API configuration."""
    api = push_api_service.update_api(db, api_id, body)
    if not api:
        raise HTTPException(status_code=404, detail="API配置不存在")

    # Simulate sync push for the config change
    push_log = push_api_service.simulate_push_to_target(
        db, api.target_system,
        {"action": "api_updated", "target_system": api.target_system, "base_url": api.base_url},
    )

    return {
        "code": 200,
        "message": "API配置已更新并同步推送",
        "data": {
            "id": api.id,
            "target_system": api.target_system,
            "push_log_id": push_log.id,
        },
    }


@router.delete("/{api_id}", response_model=dict)
def delete_api(
    api_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator", "approver")),
):
    """Delete (or deactivate) a push API configuration."""
    api = push_api_service.get_api(db, api_id)
    if not api:
        raise HTTPException(status_code=404, detail="API配置不存在")

    target = api.target_system
    success = push_api_service.delete_api(db, api_id)

    if success:
        push_api_service.simulate_push_to_target(
            db, target,
            {"action": "api_deleted", "target_system": target},
        )

    return {
        "code": 200,
        "message": f"API配置已{'停用' if success else '删除失败'}",
        "data": {"target_system": target},
    }


@router.post("/{api_id}/test", response_model=dict)
def test_api(
    api_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator", "approver")),
):
    """Test connectivity to a configured API endpoint."""
    result = push_api_service.test_api_connection(db, api_id)
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    return {"code": 200, "message": result["message"], "data": result.get("detail", {})}
