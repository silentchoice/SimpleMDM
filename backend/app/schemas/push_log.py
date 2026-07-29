"""Push log schemas."""

from datetime import datetime

from pydantic import BaseModel


class PushLogResponse(BaseModel):
    id: int
    approval_id: int
    personnel_id: int
    personnel_name: str = ""
    target_system: str
    status: str
    request_body: str | None = None
    response_body: str | None = None
    response_code: int | None = None
    retry_count: int
    error_message: str | None = None
    pushed_at: datetime | None = None
    created_at: datetime

    class Config:
        from_attributes = True
