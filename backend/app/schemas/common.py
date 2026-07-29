"""Common response schemas."""

from typing import Any, Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel):
    """Uniform API response envelope."""
    code: int = 200
    message: str = "ok"
    data: Any = None


class PaginatedData(BaseModel):
    """Paginated list wrapper."""
    items: list[Any]
    total: int
    page: int
    page_size: int
