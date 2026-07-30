package com.simplemdm.config;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {
    record DemoField(String systemCode, String department, String tableType, String subType,
                     String fieldKey, String fieldName, String fieldType, boolean required,
                     int sortOrder, boolean shared, String optionsJson) {}

    static List<DemoField> demoFields() {
        return List.of(
            new DemoField("HR","ALL","master","basic","employee_code","工号","string",true,1,false,null),
            new DemoField("HR","ALL","master","basic","employee_name","姓名","string",true,2,false,null),
            new DemoField("HR","ALL","master","basic","gender","性别","radio",true,3,false,"[\"男\",\"女\"]"),
            new DemoField("HR","ALL","master","basic","job_title","职位","string",false,4,false,null),
            new DemoField("HR","ALL","master","basic","mobile_phone","手机号","string",false,5,false,null),
            new DemoField("HR","ALL","master","basic","work_email","工作邮箱","string",false,6,false,null),
            new DemoField("HR","工程部","sub","project","engineering_project_name","项目名称","string",true,1,true,null),
            new DemoField("HR","工程部","sub","project","engineering_project_role","项目角色","string",true,2,true,null),
            new DemoField("HR","工程部","sub","project","engineering_allocation_rate","投入比例","number",false,3,false,null),
            new DemoField("HR","工程部","sub","payroll","engineering_base_pay","基本工资","number",true,1,false,null),
            new DemoField("HR","工程部","sub","payroll","engineering_performance_bonus","绩效奖金","number",false,2,false,null),
            new DemoField("HR","产品部","sub","roadmap","product_quarter_target","季度目标","string",true,1,true,null),
            new DemoField("HR","产品部","sub","roadmap","product_delivery_rate","交付率","number",false,2,true,null),
            new DemoField("HR","人力资源部","sub","contract","hr_contract_type","合同类型","string",true,1,true,null),
            new DemoField("HR","人力资源部","sub","contract","hr_contract_term","合同期限","string",true,2,false,null),
            new DemoField("HR","人力资源部","sub","contract","hr_contract_expiry_date","合同到期日","date",false,3,false,null),
            new DemoField("HR","市场部","sub","campaign","marketing_campaign_name","活动名称","string",true,1,true,null),
            new DemoField("HR","市场部","sub","campaign","marketing_budget","活动预算","number",false,2,false,null),
            new DemoField("HR","销售部","sub","target","sales_quarter_amount","季度销售额","number",true,1,true,null),
            new DemoField("HR","销售部","sub","target","sales_collection_rate","回款率","number",false,2,false,null)
        );
    }

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
    private final boolean demoReset;

    public DataInitializer(SysUserRepository ur, MdmPersonnelRepository pr, WfApprovalRepository ar,
                           SysPushLogRepository slr, SysPushApiRepository sar, SysUserPermissionRepository spr,
                           SysApproverDeptRepository sadr, MdmPersonnelSubRepository psr,
                           MdmFieldDefinitionRepository fdr, AuthService as,
                           @Value("${app.demo.reset:false}") boolean demoReset) {
        this.userRepo = ur; this.personnelRepo = pr; this.approvalRepo = ar;
        this.pushLogRepo = slr; this.pushApiRepo = sar; this.permRepo = spr;
        this.approverDeptRepo = sadr; this.personnelSubRepo = psr;
        this.fieldDefRepo = fdr; this.authService = as; this.demoReset = demoReset;
    }
    boolean demoSchemaIsCurrent() {
        return demoFields().stream().allMatch(field ->
            fieldDefRepo.findBySystemCodeAndFieldKey(field.systemCode(), field.fieldKey()).isPresent());
    }
    void resetBusinessDemoData() {
        pushLogRepo.deleteAllInBatch();
        approvalRepo.deleteAllInBatch();
        personnelSubRepo.deleteAllInBatch();
        personnelRepo.deleteAllInBatch();
        fieldDefRepo.deleteAllInBatch();
    }
    @Override
    public void run(String... args) {
        boolean seedAccounts = userRepo.count() == 0;
        if (!seedAccounts && !demoReset && demoSchemaIsCurrent()) return;

        SysUser wangwu;
        SysUser lisi;
        SysUser zhaoliu;
        SysUser admin;
        if (seedAccounts) {
            wangwu = createUser("wangwu", "123456", "王五", "人力资源部", false);
            lisi = createUser("lisi", "123456", "李四", "人力资源部", false);
            zhaoliu = createUser("zhaoliu", "123456", "赵六", "IT部", false);
            admin = createUser("admin", "admin123", "管理员", "IT部", true);

            createPerm(wangwu.getId(), "VIEW", "ALL", null, "HR");
            createPerm(wangwu.getId(), "EDIT", "DEPT", "人力资源部", "HR");
            createPerm(wangwu.getId(), "EDIT", "DEPT", "工程部", "HR");
            createPerm(lisi.getId(), "VIEW", "ALL", null, "HR");
            createPerm(zhaoliu.getId(), "VIEW", "ALL", null, "HR");
            createPerm(admin.getId(), "VIEW", "ALL", null, null);
            createPerm(admin.getId(), "VIEW", "ALL", null, "HR");

            SysApproverDept assignment = new SysApproverDept();
            assignment.setUserId(lisi.getId());
            assignment.setDepartment("人力资源部");
            approverDeptRepo.save(assignment);
        } else {
            wangwu = requireDemoUser("wangwu");
            lisi = requireDemoUser("lisi");
            zhaoliu = requireDemoUser("zhaoliu");
            admin = requireDemoUser("admin");
        }

        resetBusinessDemoData();
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

        // ── Field definitions ──
        for (DemoField field : demoFields()) {
            SysUser creator = "ALL".equals(field.department()) || "工程部".equals(field.department())
                || "人力资源部".equals(field.department()) ? wangwu : lisi;
            if ("ALL".equals(field.department())) creator = admin;
            createFieldDef(field, creator);
        }
        // ── Sub table demo data ──
        createPersonnelSub(personnelList.get(0).getId(), "project",
            "{\"engineering_project_name\":\"智能工厂平台\",\"engineering_project_role\":\"后端负责人\",\"engineering_allocation_rate\":80}", "工程部");
        createPersonnelSub(personnelList.get(0).getId(), "payroll",
            "{\"engineering_base_pay\":25000,\"engineering_performance_bonus\":5000}", "工程部");
        createPersonnelSub(personnelList.get(2).getId(), "project",
            "{\"engineering_project_name\":\"数据中台\",\"engineering_project_role\":\"技术负责人\",\"engineering_allocation_rate\":100}", "工程部");
        createPersonnelSub(personnelList.get(1).getId(), "roadmap",
            "{\"product_quarter_target\":\"电商中台上线\",\"product_delivery_rate\":90}", "产品部");
        createPersonnelSub(personnelList.get(4).getId(), "roadmap",
            "{\"product_quarter_target\":\"移动端改版\",\"product_delivery_rate\":75}", "产品部");
        createPersonnelSub(personnelList.get(5).getId(), "contract",
            "{\"hr_contract_type\":\"无固定期限\",\"hr_contract_term\":\"长期\",\"hr_contract_expiry_date\":\"2027-12-31\"}", "人力资源部");
        createPersonnelSub(personnelList.get(3).getId(), "campaign",
            "{\"marketing_campaign_name\":\"年度品牌活动\",\"marketing_budget\":500000}", "市场部");
        createPersonnelSub(personnelList.get(7).getId(), "target",
            "{\"sales_quarter_amount\":8000000,\"sales_collection_rate\":95}", "销售部");
        // ── Historical Approval #1 — EMP002 update (approved) ──
        MdmPersonnel emp002 = personnelList.get(1);
        WfApproval approval1 = new WfApproval();
        approval1.setPersonnelId(emp002.getId());
        approval1.setWorkflowType("update");
        approval1.setSubmitterId(wangwu.getId());
        approval1.setApproverId(lisi.getId());
        approval1.setStatus("approved");
        approval1.setChangeData("{\"owner_dept\":{\"old\":\"运营部\",\"new\":\"产品部\"},\"job_title\":{\"old\":\"运营总监\",\"new\":\"产品总监\"}}");
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
        approval2.setChangeData("{\"owner_dept\":{\"old\":\"销售部\",\"new\":\"市场部\"}}");
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

    private SysUser requireDemoUser(String username) {
        return userRepo.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Missing demo user: " + username));
    }

    private void createFieldDef(DemoField field, SysUser createdBy) {
        MdmFieldDefinition definition = new MdmFieldDefinition();
        definition.setSystemCode(field.systemCode());
        definition.setDepartment(field.department());
        definition.setTableType(field.tableType());
        definition.setSubType(field.subType());
        definition.setFieldKey(field.fieldKey());
        definition.setFieldName(field.fieldName());
        definition.setFieldType(field.fieldType());
        definition.setRequired(field.required());
        definition.setSortOrder(field.sortOrder());
        definition.setShared(field.shared());
        definition.setSystemField(false);
        definition.setOptionsJson(field.optionsJson());
        definition.setCreatedBy(createdBy.getId());
        definition.setCreatedByName(createdBy.getRealName());
        fieldDefRepo.save(definition);
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
        p.setOwnerDept(dept);
        try {
            p.setDataJson(mapper.writeValueAsString(Map.of(
                "employee_code", code, "employee_name", name, "gender", gender,
                "job_title", pos, "mobile_phone", phone, "work_email", email
            )));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create seed personnel", exception);
        }
        p.setStatus("active"); p.setVersion(1);
        return personnelRepo.save(p);
    }

    private void createPushLog(Long approvalId, Long personnelId, String target, String status, String resp) {
        SysPushLog log = new SysPushLog();
        log.setApprovalId(approvalId); log.setPersonnelId(personnelId);
        log.setTargetSystem(target); log.setStatus(status);
        log.setRequestBody("{\"id\":2,\"system_code\":\"HR\",\"owner_dept\":\"产品部\",\"data\":{\"employee_code\":\"EMP002\",\"employee_name\":\"李丽\",\"job_title\":\"产品总监\"},\"version\":2}");
        log.setResponseBody(resp); log.setResponseCode(200);
        log.setPushedAt(LocalDateTime.of(2026, 7, 20, 14, 20, 5));
        pushLogRepo.save(log);
    }

    private void createPushApi(String name, String target, String method, String url,
                                String authType, String authConfig, String status,
                                String desc, int retryMax, int timeout) {
        if (pushApiRepo.findByTargetSystem(target).isPresent()) return;
        SysPushApi api = new SysPushApi();
        api.setName(name); api.setTargetSystem(target); api.setMethod(method);
        api.setBaseUrl(url); api.setAuthType(authType); api.setAuthConfig(authConfig);
        api.setStatus(status); api.setDescription(desc);
        api.setRetryMax(retryMax); api.setTimeoutSec(timeout);
        pushApiRepo.save(api);
    }

    private void createPersonnelSub(Long personnelId, String subType, String dataJson,
                                     String ownerDept) {
        MdmPersonnelSub sub = new MdmPersonnelSub();
        sub.setSystemCode("HR");
        sub.setPersonnelId(personnelId);
        sub.setSubType(subType);
        sub.setDataJson(dataJson);
        sub.setOwnerDept(ownerDept);
        sub.setVersion(1);
        personnelSubRepo.save(sub);
    }

}
