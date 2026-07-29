"""User schemas."""

from datetime import datetime

from pydantic import BaseModel


class LoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    token: str
    user: "UserResponse"


class UserResponse(BaseModel):
    id: int
    username: str
    real_name: str
    role: str
    department: str | None = None
    status: str

    class Config:
        from_attributes = True


class UserListResponse(BaseModel):
    id: int
    username: str
    real_name: str
    role: str
    department: str | None = None

    class Config:
        from_attributes = True
