package com.simplemdm.config;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository userRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final WfApprovalRepository approvalRepo;
    private final SysPushLogRepository pushLogRepo;
    private final SysPushApiRepository pushApiRepo;
    private final SysUserPermissionRepository permRepo;
    private final SysApproverDeptRepository approverDeptRepo;
    private final MdmPersonnelSubRepository personnelSubRepo;
    private final MdmFieldDefinitionRepository fieldDefRepo;
    private final AuthService authService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataInitializer(SysUserRepository ur, MdmPersonnelRepository pr, WfApprovalRepository ar,
                           SysPushLogRepository slr, SysPushApiRepository sar, SysUserPermissionRepository spr,
                           SysApproverDeptRepository sadr, MdmPersonnelSubRepository psr,
                           MdmFieldDefinitionRepository fdr, AuthService as) {
        this.userRepo = ur; this.personnelRepo = pr; this.approvalRepo = ar;
        this.pushLogRepo = slr; this.pushApiRepo = sar; this.permRepo = spr;
        this.approverDeptRepo = sadr; this.personnelSubRepo = psr;
        this.fieldDefRepo = fdr; this.authService = as;
    }

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) return; // Already seeded

        // ── Users ──
        SysUser wangwu = createUser("wangwu", "123456", "王五", "人力资源部", false);
        SysUser lisi = createUser("lisi", "123456", "李四", "人力资源部", false);
        SysUser zhaoliu = createUser("zhaoliu", "123456", "赵六", "IT部", false);
        SysUser admin = createUser("admin", "admin123", "管理员", "IT部", true);

        // ── Permissions (system_code=HR) ──
        // wangwu: VIEW all HR depts + EDIT HR/工程部
        createPerm(wangwu.getId(), "VIEW", "ALL", null, "HR");
        createPerm(wangwu.getId(), "EDIT", "DEPT", "人力资源部", "HR");
        createPerm(wangwu.getId(), "EDIT", "DEPT", "工程部", "HR");
        // lisi: VIEW all HR, no EDIT
        createPerm(lisi.getId(), "VIEW", "ALL", null, "HR");
        // zhaoliu: VIEW all HR, no EDIT
        createPerm(zhaoliu.getId(), "VIEW", "ALL", null, "HR");
        // admin: VIEW ALL systems, no EDIT
        createPerm(admin.getId(), "VIEW", "ALL", null, null);
        createPerm(admin.getId(), "VIEW", "ALL", null, "HR");

        // ── Approver Assignment: lisi manages HR department ──
        SysApproverDept ad = new SysApproverDept();
        ad.setUserId(lisi.getId());
        ad.setDepartment("人力资源部");
        approverDeptRepo.save(ad);

        // ── Personnel ──
        List<MdmPersonnel> personnelList = List.of(
            createPersonnel("EMP001", "张三", "男", "工程部", "高级工程师", "13800001001", "zhangsan@demo.com"),
            createPersonnel("EMP002", "李丽", "女", "产品部", "产品总监", "13800001002", "lili@demo.com"),
            createPersonnel("EMP003", "王磊", "男", "工程部", "架构师", "13800001003", "wanglei@demo.com"),
            createPersonnel("EMP004", "陈芳", "女", "市场部", "市场经理", "13800001004", "chenfang@demo.com"),
            createPersonnel("EMP005", "刘伟", "男", "产品部", "产品经理", "13800001005", "liuwei@demo.com"),
            createPersonnel("EMP006", "周敏", "女", "人力资源部", "HR主管", "13800001006", "zhoumin@demo.com"),
            createPersonnel("EMP007", "孙浩", "男", "工程部", "开发工程师", "13800001007", "sunhao@demo.com"),
            createPersonnel("EMP008", "马超", "男", "销售部", "销售代表", "13800001008", "machao@demo.com")
        );

        // ── Field definitions: master (shared) ──
        createFieldDef("ALL", "master", "basic", "工号", "string", true, 1, admin);
        createFieldDef("ALL", "master", "basic", "姓名", "string", true, 2, admin);
        createFieldDef("ALL", "master", "basic", "性别", "string", true, 3, admin);
        createFieldDef("ALL", "master", "basic", "部门", "string", true, 4, admin);
        createFieldDef("ALL", "master", "basic", "职位", "string", false, 5, admin);
        createFieldDef("ALL", "master", "basic", "手机号", "string", false, 6, admin);
        createFieldDef("ALL", "master", "basic", "邮箱", "string", false, 7, admin);

        // ── Field definitions: sub (department-specific) ──
        createFieldDef("工程部", "sub", "project", "项目名称", "string", true, 1, wangwu);
        createFieldDef("工程部", "sub", "project", "角色", "string", true, 2, wangwu);
        createFieldDef("工程部", "sub", "project", "工时占比", "string", false, 3, wangwu);
        createFieldDef("工程部", "sub", "salary", "基本工资", "number", true, 1, wangwu);
        createFieldDef("工程部", "sub", "salary", "绩效奖金", "number", false, 2, wangwu);
        createFieldDef("工程部", "sub", "salary", "年终奖基数", "string", false, 3, wangwu);
        createFieldDef("产品部", "sub", "project", "项目名称", "string", true, 1, lisi);
        createFieldDef("产品部", "sub", "project", "角色", "string", true, 2, lisi);
        createFieldDef("产品部", "sub", "project", "工时占比", "string", false, 3, lisi);
        createFieldDef("产品部", "sub", "sales_target", "Q3销售额", "string", true, 1, lisi);
        createFieldDef("产品部", "sub", "sales_target", "Q4销售额", "string", true, 2, lisi);
        createFieldDef("产品部", "sub", "sales_target", "回款率", "string", false, 3, lisi);
        createFieldDef("人力资源部", "sub", "salary", "基本工资", "number", true, 1, wangwu);
        createFieldDef("人力资源部", "sub", "salary", "绩效系数", "number", false, 2, wangwu);
        createFieldDef("人力资源部", "sub", "salary", "社保基数", "number", false, 3, wangwu);
        createFieldDef("人力资源部", "sub", "contract", "合同类型", "string", true, 1, wangwu);
        createFieldDef("人力资源部", "sub", "contract", "合同期限", "string", true, 2, wangwu);
        createFieldDef("人力资源部", "sub", "contract", "到期日期", "date", false, 3, wangwu);

        // ── Sub table demo data (JSON key 必须与 field_name 一致) ──
        createPersonnelSub(personnelList.get(0).getId(), "project",
            "{\"项目名称\":\"智能工厂平台\",\"角色\":\"后端负责人\",\"工时占比\":\"80%\"}", "工程部", "shared");
        createPersonnelSub(personnelList.get(0).getId(), "salary",
            "{\"基本工资\":\"25000\",\"绩效奖金\":\"5000\",\"年终奖基数\":\"3个月\"}", "工程部", "private");
        createPersonnelSub(personnelList.get(1).getId(), "project",
            "{\"项目名称\":\"电商中台\",\"角色\":\"产品负责人\",\"工时占比\":\"100%\"}", "产品部", "shared");
        createPersonnelSub(personnelList.get(4).getId(), "sales_target",
            "{\"Q3销售额\":\"500万\",\"Q4销售额\":\"800万\",\"回款率\":\"95%\"}", "产品部", "private");
        // 人力资源部子表数据
        createPersonnelSub(personnelList.get(5).getId(), "salary",
            "{\"基本工资\":\"18000\",\"绩效系数\":\"1.2\",\"社保基数\":\"12000\"}", "人力资源部", "shared");
        createPersonnelSub(personnelList.get(5).getId(), "contract",
            "{\"合同类型\":\"无固定期限\",\"合同期限\":\"长期\",\"到期日期\":\"-\"}", "人力资源部", "private");
        // 工程部更多数据
        createPersonnelSub(personnelList.get(2).getId(), "project",
            "{\"项目名称\":\"数据中台\",\"角色\":\"技术负责人\",\"工时占比\":\"100%\"}", "工程部", "shared");
        createPersonnelSub(personnelList.get(2).getId(), "salary",
            "{\"基本工资\":\"30000\",\"绩效奖金\":\"8000\",\"年终奖基数\":\"4个月\"}", "工程部", "private");
        createPersonnelSub(personnelList.get(6).getId(), "project",
            "{\"项目名称\":\"智能工厂平台\",\"角色\":\"前端开发\",\"工时占比\":\"100%\"}", "工程部", "shared");
        // 产品部更多数据
        createPersonnelSub(personnelList.get(1).getId(), "sales_target",
            "{\"Q3销售额\":\"300万\",\"Q4销售额\":\"500万\",\"回款率\":\"90%\"}", "产品部", "shared");
        createPersonnelSub(personnelList.get(4).getId(), "project",
            "{\"项目名称\":\"电商中台\",\"角色\":\"产品助理\",\"工时占比\":\"50%\"}", "产品部", "private");

        // ── Historical Approval #1 — EMP002 update (approved) ──
        MdmPersonnel emp002 = personnelList.get(1);
        WfApproval approval1 = new WfApproval();
        approval1.setPersonnelId(emp002.getId());
        approval1.setWorkflowType("update");
        approval1.setSubmitterId(wangwu.getId());
        approval1.setApproverId(lisi.getId());
        approval1.setStatus("approved");
        approval1.setChangeData("{\"department\":{\"old\":\"运营部\",\"new\":\"产品部\"},\"position\":{\"old\":\"运营总监\",\"new\":\"产品总监\"}}");
        approval1.setSubmitTime(LocalDateTime.of(2026, 7, 20, 10, 30, 0));
        approval1.setApproveTime(LocalDateTime.of(2026, 7, 20, 14, 20, 0));
        approval1.setApproveComment("同意调动，即日起生效");
        approvalRepo.save(approval1);

        // Push logs for approval 1
        createPushLog(approval1.getId(), emp002.getId(), "CRM", "success",
            "{\"code\":200,\"message\":\"数据已成功同步到 CRM 系统\"}");
        createPushLog(approval1.getId(), emp002.getId(), "MES", "success",
            "{\"code\":200,\"message\":\"数据已成功同步到 MES 系统\"}");

        // ── Historical Approval #2 — EMP008 update (rejected) ──
        MdmPersonnel emp008 = personnelList.get(7);
        WfApproval approval2 = new WfApproval();
        approval2.setPersonnelId(emp008.getId());
        approval2.setWorkflowType("update");
        approval2.setSubmitterId(wangwu.getId());
        approval2.setApproverId(lisi.getId());
        approval2.setStatus("rejected");
        approval2.setChangeData("{\"department\":{\"old\":\"销售部\",\"new\":\"市场部\"}}");
        approval2.setSubmitTime(LocalDateTime.of(2026, 7, 22, 9, 0, 0));
        approval2.setApproveTime(LocalDateTime.of(2026, 7, 22, 11, 15, 0));
        approval2.setApproveComment("该员工尚在试用期，暂不调动");
        approvalRepo.save(approval2);

        // ── Push API Configs ──
        createPushApi("CRM系统", "CRM", "POST",
            "http://crm.internal.example.com/api/personnel/sync",
            "token", "{\"header\":\"Authorization\",\"prefix\":\"Bearer\",\"token\":\"crm-demo-token\"}",
            "active", "客户关系管理系统", 3, 30);
        createPushApi("MES系统", "MES", "POST",
            "http://mes.internal.example.com/api/employee/sync",
            "token", "{\"header\":\"X-API-Key\",\"token\":\"mes-demo-key\"}",
            "active", "制造执行系统", 3, 30);
        createPushApi("HR系统", "HR", "PUT",
            "http://hr.internal.example.com/api/staff/sync",
            "token", "{\"header\":\"Authorization\",\"prefix\":\"Bearer\",\"token\":\"hr-demo-token\"}",
            "inactive", "人力资源系统（计划接入）", 5, 60);

        System.out.println("[OK] Demo data seeded: 4 users, 8 personnel, 2 historical approvals, 3 push APIs");
    }

    private SysUser createUser(String uname, String pwd, String realName, String dept, boolean isAdmin) {
        SysUser u = new SysUser();
        u.setUsername(uname);
        u.setPasswordHash(authService.hashPassword(pwd));
        u.setRealName(realName);
        u.setDepartment(dept);
        u.setIsAdmin(isAdmin);
        u.setStatus("active");
        return userRepo.save(u);
    }

    private void createPerm(Long userId, String permType, String scopeType,
                             String scopeValue, String systemCode) {
        SysUserPermission p = new SysUserPermission();
        p.setUserId(userId);
        p.setPermType(permType);
        p.setScopeType(scopeType);
        p.setScopeValue(scopeValue);
        p.setSystemCode(systemCode);
        permRepo.save(p);
    }

    private MdmPersonnel createPersonnel(String code, String name, String gender,
                                          String dept, String pos, String phone, String email) {
        MdmPersonnel p = new MdmPersonnel();
        p.setSystemCode("HR");
        p.setEmployeeCode(code); p.setName(name); p.setGender(gender);
        p.setDepartment(dept); p.setPosition(pos); p.setPhone(phone); p.setEmail(email);
        p.setStatus("active"); p.setVersion(1);
        return personnelRepo.save(p);
    }

    private void createPushLog(Long approvalId, Long personnelId, String target, String status, String resp) {
        SysPushLog log = new SysPushLog();
        log.setApprovalId(approvalId); log.setPersonnelId(personnelId);
        log.setTargetSystem(target); log.setStatus(status);
        log.setRequestBody("{\"employee_code\":\"EMP002\",\"name\":\"李丽\",\"department\":\"产品部\",\"position\":\"产品总监\",\"version\":2}");
        log.setResponseBody(resp); log.setResponseCode(200);
        log.setPushedAt(LocalDateTime.of(2026, 7, 20, 14, 20, 5));
        pushLogRepo.save(log);
    }

    private void createPushApi(String name, String target, String method, String url,
                                String authType, String authConfig, String status,
                                String desc, int retryMax, int timeout) {
        SysPushApi api = new SysPushApi();
        api.setName(name); api.setTargetSystem(target); api.setMethod(method);
        api.setBaseUrl(url); api.setAuthType(authType); api.setAuthConfig(authConfig);
        api.setStatus(status); api.setDescription(desc);
        api.setRetryMax(retryMax); api.setTimeoutSec(timeout);
        pushApiRepo.save(api);
    }

    private void createPersonnelSub(Long personnelId, String subType, String dataJson,
                                     String ownerDept, String visibility) {
        MdmPersonnelSub sub = new MdmPersonnelSub();
        sub.setSystemCode("HR");
        sub.setPersonnelId(personnelId);
        sub.setSubType(subType);
        sub.setDataJson(dataJson);
        sub.setOwnerDept(ownerDept);
        sub.setVisibility(visibility);
        sub.setVersion(1);
        personnelSubRepo.save(sub);
    }

    private void createFieldDef(String dept, String tableType, String subType, String fieldName,
                                 String fieldType, boolean required, int sortOrder, SysUser createdBy) {
        MdmFieldDefinition def = new MdmFieldDefinition();
        def.setSystemCode("HR");
        def.setDepartment(dept);
        def.setTableType(tableType);
        def.setSubType(subType);
        def.setFieldName(fieldName);
        def.setFieldType(fieldType);
        def.setRequired(required);
        def.setSortOrder(sortOrder);
        def.setCreatedBy(createdBy.getId());
        def.setCreatedByName(createdBy.getRealName());
        fieldDefRepo.save(def);
    }
}
