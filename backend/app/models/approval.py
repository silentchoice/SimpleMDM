"""Approval workflow model."""

from datetime import datetime

from sqlalchemy import String, DateTime, Integer, Text, ForeignKey, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class WfApproval(Base):
    __tablename__ = "wf_approval"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    personnel_id: Mapped[int] = mapped_column(ForeignKey("mdm_personnel.id"), nullable=False, index=True)
    workflow_type: Mapped[str] = mapped_column(String(16), nullable=False)  # create | update
    submitter_id: Mapped[int] = mapped_column(ForeignKey("sys_user.id"), nullable=False)
    approver_id: Mapped[int | None] = mapped_column(ForeignKey("sys_user.id"), nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="pending")  # pending | approved | rejected | withdrawn
    change_data: Mapped[str | None] = mapped_column(Text)  # JSON: {field: {old, new}}
    submit_time: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    approve_time: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    approve_comment: Mapped[str | None] = mapped_column(Text, nullable=True)
    withdrawn_time: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
