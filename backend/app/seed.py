"""Seed demo data into the database."""

import json
from datetime import datetime, timezone

from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.models.user import SysUser
from app.models.personnel import MdmPersonnel
from app.models.approval import WfApproval
from app.models.push_log import SysPushLog
from app.models.push_api import SysPushApi
from app.services.auth_service import hash_password


def _first(db: Session, model, **filters):
    """Helper: return first record matching filters using SQLAlchemy 2.0 style."""
    stmt = select(model)
    for k, v in filters.items():
        stmt = stmt.where(getattr(model, k) == v)
    return db.execute(stmt.limit(1)).scalar()


def seed_all(db: Session):
    """Seed all demo data if the database is empty."""
    count = db.execute(select(func.count()).select_from(SysUser)).scalar() or 0
    if count > 0:
        return  # Already seeded

    # ── Users ──────────────────────────────────────────────
    users = [
        SysUser(username="wangwu", password_hash=hash_password("123456"),
                real_name="王五", role="operator", department="人力资源部", status="active"),
        SysUser(username="lisi", password_hash=hash_password("123456"),
                real_name="李四", role="approver", department="人力资源部", status="active"),
        SysUser(username="zhaoliu", password_hash=hash_password("123456"),
                real_name="赵六", role="viewer", department="IT部", status="active"),
        SysUser(username="admin", password_hash=hash_password("admin123"),
                real_name="管理员", role="approver", department="IT部", status="active"),
    ]
    for u in users:
        db.add(u)
    db.commit()

    # ── Personnel ──────────────────────────────────────────
    personnel_list = [
        MdmPersonnel(employee_code="EMP001", name="张三", gender="男", department="工程部",
                      position="高级工程师", phone="13800001001", email="zhangsan@demo.com",
                      status="active", version=1),
        MdmPersonnel(employee_code="EMP002", name="李丽", gender="女", department="产品部",
                      position="产品总监", phone="13800001002", email="lili@demo.com",
                      status="active", version=3),
        MdmPersonnel(employee_code="EMP003", name="王磊", gender="男", department="工程部",
                      position="架构师", phone="13800001003", email="wanglei@demo.com",
                      status="active", version=1),
        MdmPersonnel(employee_code="EMP004", name="陈芳", gender="女", department="市场部",
                      position="市场经理", phone="13800001004", email="chenfang@demo.com",
                      status="active", version=1),
        MdmPersonnel(employee_code="EMP005", name="刘伟", gender="男", department="产品部",
                      position="产品经理", phone="13800001005", email="liuwei@demo.com",
                      status="active", version=2),
        MdmPersonnel(employee_code="EMP006", name="周敏", gender="女", department="人力资源部",
                      position="HR主管", phone="13800001006", email="zhoumin@demo.com",
                      status="active", version=1),
        MdmPersonnel(employee_code="EMP007", name="孙浩", gender="男", department="工程部",
                      position="开发工程师", phone="13800001007", email="sunhao@demo.com",
                      status="active", version=1),
        MdmPersonnel(employee_code="EMP008", name="马超", gender="男", department="销售部",
                      position="销售代表", phone="13800001008", email="machao@demo.com",
                      status="active", version=1),
    ]
    for p in personnel_list:
        db.add(p)
    db.commit()

    # ── Historical Approval #1 — EMP002: 李丽 运营部→产品部 (approved) ──
    op = _first(db, SysUser, username="wangwu")
    ap = _first(db, SysUser, username="lisi")
    emp002 = _first(db, MdmPersonnel, employee_code="EMP002")

    change1 = json.dumps({
        "department": {"old": "运营部", "new": "产品部"},
        "position": {"old": "运营总监", "new": "产品总监"},
    }, ensure_ascii=False)

    approval1 = WfApproval(
        personnel_id=emp002.id,
        workflow_type="update",
        submitter_id=op.id,
        approver_id=ap.id,
        status="approved",
        change_data=change1,
        submit_time=datetime(2026, 7, 20, 10, 30, 0, tzinfo=timezone.utc),
        approve_time=datetime(2026, 7, 20, 14, 20, 0, tzinfo=timezone.utc),
        approve_comment="同意调动，即日起生效",
    )
    db.add(approval1)
    db.commit()

    # Push log for approval 1
    push_json = json.dumps({
        "employee_code": "EMP002", "name": "李丽", "department": "产品部",
        "position": "产品总监", "version": 2,
    }, ensure_ascii=False)
    resp_json = json.dumps({"code": 200, "message": "数据已成功同步到 CRM 系统"}, ensure_ascii=False)
    db.add(SysPushLog(
        approval_id=approval1.id, personnel_id=emp002.id,
        target_system="CRM", status="success",
        request_body=push_json, response_body=resp_json, response_code=200,
        pushed_at=datetime(2026, 7, 20, 14, 20, 5, tzinfo=timezone.utc),
    ))
    resp_json2 = json.dumps({"code": 200, "message": "数据已成功同步到 MES 系统"}, ensure_ascii=False)
    db.add(SysPushLog(
        approval_id=approval1.id, personnel_id=emp002.id,
        target_system="MES", status="success",
        request_body=push_json, response_body=resp_json2, response_code=200,
        pushed_at=datetime(2026, 7, 20, 14, 20, 6, tzinfo=timezone.utc),
    ))

    # ── Historical Approval #2 — EMP008: 马超 部门变更 (rejected) ──
    emp008 = _first(db, MdmPersonnel, employee_code="EMP008")
    change2 = json.dumps({
        "department": {"old": "销售部", "new": "市场部"},
    }, ensure_ascii=False)

    approval2 = WfApproval(
        personnel_id=emp008.id,
        workflow_type="update",
        submitter_id=op.id,
        approver_id=ap.id,
        status="rejected",
        change_data=change2,
        submit_time=datetime(2026, 7, 22, 9, 0, 0, tzinfo=timezone.utc),
        approve_time=datetime(2026, 7, 22, 11, 15, 0, tzinfo=timezone.utc),
        approve_comment="该员工尚在试用期，暂不调动",
    )
    db.add(approval2)
    db.commit()

    # ── Push API Configs ──────────────────────────────────────
    api_configs = [
        SysPushApi(
            name="CRM系统", target_system="CRM", method="POST",
            base_url="http://crm.internal.example.com/api/personnel/sync",
            auth_type="token",
            auth_config=json.dumps({"header": "Authorization", "prefix": "Bearer", "token": "crm-demo-token"}),
            status="active", description="客户关系管理系统", retry_max=3, timeout_sec=30,
        ),
        SysPushApi(
            name="MES系统", target_system="MES", method="POST",
            base_url="http://mes.internal.example.com/api/employee/sync",
            auth_type="token",
            auth_config=json.dumps({"header": "X-API-Key", "token": "mes-demo-key"}),
            status="active", description="制造执行系统", retry_max=3, timeout_sec=30,
        ),
        SysPushApi(
            name="HR系统", target_system="HR", method="PUT",
            base_url="http://hr.internal.example.com/api/staff/sync",
            auth_type="token",
            auth_config=json.dumps({"header": "Authorization", "prefix": "Bearer", "token": "hr-demo-token"}),
            status="inactive", description="人力资源系统（计划接入）", retry_max=5, timeout_sec=60,
        ),
    ]
    for api in api_configs:
        db.add(api)
    db.commit()

    print("[OK] Demo data seeded: 4 users, 8 personnel, 2 historical approvals, 3 push APIs")
