"""Push API schemas."""

from datetime import datetime

from pydantic import BaseModel, Field


class PushApiCreate(BaseModel):
    name: str = Field(..., description="显示名称，如 CRM系统")
    target_system: str = Field(..., description="标识符，如 CRM")
    method: str = Field(default="POST", description="HTTP方法")
    base_url: str = Field(..., description="目标URL")
    auth_type: str = Field(default="token", description="认证方式: none/basic/token")
    auth_config: str | None = Field(default=None, description="认证配置JSON")
    status: str = Field(default="active", description="active/inactive")
    description: str | None = None
    retry_max: int = Field(default=3, description="最大重试次数")
    timeout_sec: int = Field(default=30, description="超时秒数")


class PushApiUpdate(BaseModel):
    name: str | None = None
    method: str | None = None
    base_url: str | None = None
    auth_type: str | None = None
    auth_config: str | None = None
    status: str | None = None
    description: str | None = None
    retry_max: int | None = None
    timeout_sec: int | None = None


class PushApiResponse(BaseModel):
    id: int
    name: str
    target_system: str
    method: str
    base_url: str
    auth_type: str
    auth_config: str | None = None
    status: str
    description: str | None = None
    retry_max: int
    timeout_sec: int
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
