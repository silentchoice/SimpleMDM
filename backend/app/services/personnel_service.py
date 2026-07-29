"""Personnel CRUD service with diff computation for approvals."""

import json

from sqlalchemy import select, func, or_
from sqlalchemy.orm import Session

from app.models.personnel import MdmPersonnel
from app.schemas.personnel import PersonnelCreate, PersonnelUpdate


FIELD_LABELS = {
    "employee_code": "工号",
    "name": "姓名",
    "gender": "性别",
    "department": "部门",
    "position": "职位",
    "phone": "手机号",
    "email": "邮箱",
}


def list_personnel(
    db: Session,
    keyword: str = "",
    department: str = "",
    page: int = 1,
    page_size: int = 10,
) -> tuple[list[MdmPersonnel], int]:
    q = select(MdmPersonnel)
    if keyword:
        q = q.where(
            or_(
                MdmPersonnel.name.contains(keyword),
                MdmPersonnel.employee_code.contains(keyword),
                MdmPersonnel.position.contains(keyword),
            )
        )
    if department:
        q = q.where(MdmPersonnel.department == department)

    # Total count
    count_q = select(func.count()).select_from(q.subquery())
    total = db.execute(count_q).scalar() or 0

    # Paginated results
    q = q.order_by(MdmPersonnel.id.desc()).offset((page - 1) * page_size).limit(page_size)
    items = list(db.execute(q).scalars().all())

    return items, total


def get_personnel(db: Session, personnel_id: int) -> MdmPersonnel | None:
    return db.get(MdmPersonnel, personnel_id)


def create_personnel(db: Session, data: PersonnelCreate) -> MdmPersonnel:
    p = MdmPersonnel(**data.model_dump(), status="active")
    db.add(p)
    db.commit()
    db.refresh(p)
    return p


def compute_diff(existing: MdmPersonnel, data: PersonnelUpdate) -> dict | None:
    """Compare update payload against existing record, return {field: {old, new}} for changed fields only. Returns None if no changes detected."""
    diff = {}
    for field, value in data.model_dump(exclude_unset=True).items():
        old_value = getattr(existing, field)
        if old_value != value:
            diff[field] = {"old": old_value, "new": value}
    return diff if diff else None


def apply_changes(db: Session, personnel: MdmPersonnel, change_data_json: str) -> None:
    """Apply approved changes from JSON diff to the personnel record."""
    changes = json.loads(change_data_json)
    for field, vals in changes.items():
        setattr(personnel, field, vals["new"])
    personnel.status = "active"
    personnel.version = (personnel.version or 1) + 1
    db.commit()
    db.refresh(personnel)


def get_departments(db: Session) -> list[str]:
    """Return distinct department names for the filter dropdown."""
    q = select(MdmPersonnel.department).distinct().order_by(MdmPersonnel.department)
    return [row[0] for row in db.execute(q).all()]
