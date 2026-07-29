"""Push API configuration model — stores downstream system endpoint configs."""

from datetime import datetime

from sqlalchemy import String, DateTime, Text, Integer, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SysPushApi(Base):
    __tablename__ = "sys_push_api"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(128), nullable=False)           # display name e.g. "CRM系统"
    target_system: Mapped[str] = mapped_column(String(32), unique=True, nullable=False, index=True)  # code e.g. "CRM"
    method: Mapped[str] = mapped_column(String(8), default="POST")           # HTTP method
    base_url: Mapped[str] = mapped_column(String(512), nullable=False)       # target URL
    auth_type: Mapped[str] = mapped_column(String(16), default="token")      # none | basic | token
    auth_config: Mapped[str | None] = mapped_column(Text, nullable=True)     # JSON: {header_name, token, ...}
    status: Mapped[str] = mapped_column(String(16), default="active")        # active | inactive
    description: Mapped[str | None] = mapped_column(String(512), nullable=True)
    retry_max: Mapped[int] = mapped_column(Integer, default=3)               # max retry count
    timeout_sec: Mapped[int] = mapped_column(Integer, default=30)            # request timeout
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now(), onupdate=func.now())
