"""Personnel schemas."""

from datetime import datetime

from pydantic import BaseModel, Field


class PersonnelBase(BaseModel):
    employee_code: str
    name: str
    gender: str | None = None
    department: str
    position: str | None = None
    phone: str | None = None
    email: str | None = None


class PersonnelCreate(PersonnelBase):
    pass


class PersonnelUpdate(BaseModel):
    employee_code: str | None = None
    name: str | None = None
    gender: str | None = None
    department: str | None = None
    position: str | None = None
    phone: str | None = None
    email: str | None = None


class PersonnelResponse(PersonnelBase):
    id: int
    status: str
    version: int
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
