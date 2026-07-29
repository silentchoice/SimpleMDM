"""Push log model."""

from datetime import datetime

from sqlalchemy import String, DateTime, Integer, Text, ForeignKey, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SysPushLog(Base):
    __tablename__ = "sys_push_log"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    approval_id: Mapped[int | None] = mapped_column(ForeignKey("wf_approval.id"), nullable=True, index=True)
    personnel_id: Mapped[int | None] = mapped_column(ForeignKey("mdm_personnel.id"), nullable=True)
    target_system: Mapped[str] = mapped_column(String(32), nullable=False)  # CRM | MES | HR
    status: Mapped[str] = mapped_column(String(16), default="pending")  # success | failed | pending
    request_body: Mapped[str | None] = mapped_column(Text)  # JSON payload sent
    response_body: Mapped[str | None] = mapped_column(Text)  # JSON response received
    response_code: Mapped[int | None] = mapped_column(Integer, nullable=True)
    retry_count: Mapped[int] = mapped_column(Integer, default=0)
    error_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    pushed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
