"""User list endpoint (for demo use)."""

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import SysUser
from app.api.deps import get_current_user

router = APIRouter(prefix="/api/users", tags=["用户"])


@router.get("", response_model=dict)
def list_users(
    db: Session = Depends(get_db),
    current_user: SysUser = Depends(get_current_user),
):
    """List all active users."""
    users = db.execute(
        select(SysUser).where(SysUser.status == "active").order_by(SysUser.id)
    ).scalars().all()
    return {
        "code": 200,
        "message": "ok",
        "data": [
            {
                "id": u.id,
                "username": u.username,
                "real_name": u.real_name,
                "role": u.role,
                "department": u.department,
            }
            for u in users
        ],
    }
