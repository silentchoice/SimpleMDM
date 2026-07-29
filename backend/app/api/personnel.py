"""Personnel CRUD endpoints."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import SysUser
from app.schemas.personnel import PersonnelCreate, PersonnelUpdate, PersonnelResponse
from app.api.deps import get_current_user, require_role
from app.services import personnel_service
from app.services import approval_service

router = APIRouter(prefix="/api/personnel", tags=["人员管理"])


@router.get("", response_model=dict)
def list_personnel(
    keyword: str = Query("", description="搜索关键词（姓名/工号/职位）"),
    department: str = Query("", description="部门筛选"),
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=100),
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """List personnel with search, filter, and pagination."""
    items, total = personnel_service.list_personnel(db, keyword, department, page, page_size)
    return {
        "code": 200,
        "message": "ok",
        "data": {
            "items": [
                {
                    "id": p.id,
                    "employee_code": p.employee_code,
                    "name": p.name,
                    "gender": p.gender,
                    "department": p.department,
                    "position": p.position,
                    "phone": p.phone,
                    "email": p.email,
                    "status": p.status,
                    "version": p.version,
                    "created_at": str(p.created_at),
                    "updated_at": str(p.updated_at),
                }
                for p in items
            ],
            "total": total,
            "page": page,
            "page_size": page_size,
        },
    }


@router.get("/departments", response_model=dict)
def list_departments(
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """Get distinct department names for filter dropdown."""
    depts = personnel_service.get_departments(db)
    return {"code": 200, "message": "ok", "data": depts}


@router.get("/{personnel_id}", response_model=dict)
def get_personnel(
    personnel_id: int,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """Get single personnel detail."""
    p = personnel_service.get_personnel(db, personnel_id)
    if not p:
        raise HTTPException(status_code=404, detail="人员不存在")
    return {
        "code": 200,
        "message": "ok",
        "data": {
            "id": p.id,
            "employee_code": p.employee_code,
            "name": p.name,
            "gender": p.gender,
            "department": p.department,
            "position": p.position,
            "phone": p.phone,
            "email": p.email,
            "status": p.status,
            "version": p.version,
            "created_at": str(p.created_at),
            "updated_at": str(p.updated_at),
        },
    }


@router.post("", response_model=dict)
def create_personnel(
    body: PersonnelCreate,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator")),
):
    """Create personnel — automatically creates approval and sets status to pending_approval."""
    from app.models.personnel import MdmPersonnel
    from sqlalchemy import select as s
    existing = db.execute(
        s(MdmPersonnel).where(MdmPersonnel.employee_code == body.employee_code)
    ).scalar()
    if existing:
        raise HTTPException(status_code=400, detail=f"工号 {body.employee_code} 已存在")

    approval = approval_service.create_approval_for_create(db, current_user.id, body)
    return {
        "code": 200,
        "message": "提交成功，请等待审批",
        "data": {"personnel_id": approval.personnel_id, "approval_id": approval.id},
    }


@router.put("/{personnel_id}", response_model=dict)
def update_personnel(
    personnel_id: int,
    body: PersonnelUpdate,
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(require_role("operator")),
):
    """Update personnel — creates approval, changes not applied until approved."""
    p = personnel_service.get_personnel(db, personnel_id)
    if not p:
        raise HTTPException(status_code=404, detail="人员不存在")
    if p.status == "pending_approval":
        raise HTTPException(status_code=400, detail="该人员已有待审批的变更，请等待审批完成")

    approval = approval_service.create_approval_for_update(db, personnel_id, current_user.id, body)
    if approval is None:
        return {"code": 200, "message": "没有变更需要提交", "data": None}

    return {
        "code": 200,
        "message": "变更已提交，请等待审批",
        "data": {"personnel_id": personnel_id, "approval_id": approval.id},
    }
