"""Approval schemas."""

from datetime import datetime

from pydantic import BaseModel


class ApprovalAction(BaseModel):
    comment: str = ""


class ApprovalResponse(BaseModel):
    id: int
    personnel_id: int
    personnel_name: str = ""
    workflow_type: str
    submitter_id: int
    submitter_name: str = ""
    approver_id: int | None = None
    approver_name: str = ""
    status: str
    change_data: str | None = None  # JSON string
    submit_time: datetime
    approve_time: datetime | None = None
    approve_comment: str | None = None
    withdrawn_time: datetime | None = None
    created_at: datetime

    class Config:
        from_attributes = True
