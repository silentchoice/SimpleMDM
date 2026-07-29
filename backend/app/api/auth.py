"""Auth endpoints."""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import SysUser
from app.schemas.user import LoginRequest, TokenResponse, UserResponse
from app.services.auth_service import verify_password, create_access_token
from app.api.deps import get_current_user

router = APIRouter(prefix="/api/auth", tags=["认证"])


@router.post("/login", response_model=dict)
def login(body: LoginRequest, db: Session = Depends(get_db)):
    """User login — returns JWT token and user info."""
    user = db.execute(
        select(SysUser).where(SysUser.username == body.username)
    ).scalar()

    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="用户名或密码错误",
        )

    if user.status != "active":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="账号已被禁用",
        )

    token = create_access_token(user.id)
    return {
        "code": 200,
        "message": "登录成功",
        "data": {
            "token": token,
            "user": {
                "id": user.id,
                "username": user.username,
                "real_name": user.real_name,
                "role": user.role,
                "department": user.department,
                "status": user.status,
            },
        },
    }


@router.get("/me", response_model=dict)
def me(current_user: SysUser = Depends(get_current_user)):
    """Get current user info from token."""
    return {
        "code": 200,
        "message": "ok",
        "data": {
            "id": current_user.id,
            "username": current_user.username,
            "real_name": current_user.real_name,
            "role": current_user.role,
            "department": current_user.department,
            "status": current_user.status,
        },
    }
