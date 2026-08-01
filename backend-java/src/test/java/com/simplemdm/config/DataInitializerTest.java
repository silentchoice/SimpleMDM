package com.simplemdm.config;

import com.simplemdm.model.mdm.*;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.system.*;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalChildChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.mdm.*;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.service.mdm.RecordProjectionService;
import com.simplemdm.service.mdm.CreateFieldCommand;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.system.RecordAccessService;
import com.simplemdm.service.integration.CredentialEncryptionService;
import com.simplemdm.service.integration.EndpointUrlPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "app.bootstrap.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = {SystemEntity.class, ObjectType.class, ApprovalRequest.class, PushEndpoint.class})
@EnableJpaRepositories(basePackages = {"com.simplemdm.repository.system", "com.simplemdm.repository.mdm", "com.simplemdm.repository.integration"})
@Import({DataInitializerTest.Config.class, BootstrapCoordinator.class})
class DataInitializerTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        BCryptPasswordEncoder encoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired SystemRepository systems;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;
    @Autowired ObjectTypeRepository objects;
    @Autowired MdmRecordRepository records;
    @Autowired FieldDefinitionRepository fields;
    @Autowired RecordValueRepository values;
    @Autowired ChildTypeRepository childTypes;
    @Autowired ChildFieldDefinitionRepository childFields;
    @Autowired ChildRecordRepository childRecords;
    @Autowired ChildRecordValueRepository childValues;
    @Autowired PushEndpointRepository endpoints;
    @Autowired EntityManager em;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired BootstrapCoordinator coordinator;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final EndpointUrlPolicy endpointUrls = mock(EndpointUrlPolicy.class);
    private final CredentialEncryptionService credentials = new CredentialEncryptionService(
        new com.fasterxml.jackson.databind.ObjectMapper(), "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void bootstrapSeedsExactBusinessGraphAndIsIdempotent() throws Exception {
        DataInitializer initializer = initializer(true);

        initializer.run();
        initializer.run();
        em.flush();
        em.clear();

        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        Department root = department(system, "ROOT");
        Department hr = department(system, "HR");
        Department sales = department(system, "SALES");
        Department other = department(system, "OTHER");

        assertThat(systems.count()).isEqualTo(1);
        assertThat(departments.findBySystem_Id(system.getId()))
            .extracting(Department::getCode)
            .containsExactlyInAnyOrder("ROOT", "HR", "IT", "SALES", "OTHER");
        assertThat(departments.findBySystem_Id(system.getId()))
            .filteredOn(department -> !department.getCode().equals("ROOT"))
            .allMatch(department -> department.getParent() != null
                && department.getParent().getId().equals(root.getId()));

        assertBusinessIdentity(system, "hr_approver", hr, "DEPT_APPROVER",
            Set.of("MDM_RECORD_VIEW", "APPROVAL_REVIEW", "INTEGRATION_MANUAL_PUSH"), true);
        assertBusinessIdentity(system, "hr_editor", hr, "DEPT_EDITOR",
            Set.of("MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "INTEGRATION_MANUAL_PUSH"), true);
        assertBusinessIdentity(system, "hr_viewer", hr, "DEPT_VIEWER",
            Set.of("MDM_RECORD_VIEW"), false);
        assertBusinessIdentity(system, "cross_viewer", other, "CROSS_DEPT_VIEWER",
            Set.of("MDM_RECORD_VIEW", "MDM_RECORD_CROSS_VIEW"), false);
        assertThat(users.findAll()).hasSize(5);
        assertThat(count("sys_role")).isEqualTo(5);
        assertThat(count("sys_user_role")).isEqualTo(5);
        assertThat(count("sys_user_department_scope")).isEqualTo(5);

        AuthorizationService authorization = new AuthorizationService(em);
        User crossViewer = user(system, "cross_viewer");
        assertThat(authorization.can(crossViewer.getId(), "MDM_RECORD_VIEW", other.getId())).isTrue();
        assertThat(authorization.hasPermission(crossViewer.getId(), "MDM_RECORD_CROSS_VIEW")).isTrue();
        assertThat(authorization.hasPermission(crossViewer.getId(), "MDM_RECORD_EDIT")).isFalse();
        assertThat(authorization.hasPermission(crossViewer.getId(), "APPROVAL_REVIEW")).isFalse();
        assertThat(authorization.hasPermission(crossViewer.getId(), "INTEGRATION_MANUAL_PUSH")).isFalse();

        ObjectType person = objects.findBySystemIdAndCode(system.getId(), "person").orElseThrow();
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", person.getId()))
            .isEqualTo("个人信息");
        assertThat(singleBoolean("select approval_required from mdm_object_type where id=:id", "id", person.getId()))
            .isTrue();
        assertThat(countWhere("mdm_field_definition", "object_type_id", person.getId())).isEqualTo(8);
        assertMasterField(person, "employee_code", "员工编号", "STRING", true, true, 64);
        assertMasterField(person, "employee_name", "姓名", "STRING", true, false, 128);
        assertMasterField(person, "gender", "性别", "STRING", false, false, 16);
        assertMasterField(person, "birth_date", "出生日期", "DATE", false, false, null);
        assertMasterField(person, "mobile_phone", "手机号", "STRING", false, false, 64);
        assertMasterField(person, "work_email", "工作邮箱", "STRING", false, false, 255);
        assertMasterField(person, "hire_date", "入职日期", "DATE", false, false, null);
        assertMasterField(person, "employment_status", "在职状态", "STRING", false, false, 32);

        Long childTypeId = singleLong("select id from mdm_child_type where object_type_id=:object and code='part_time'",
            "object", person.getId());
        assertThat(singleString("select name from mdm_child_type where id=:id", "id", childTypeId))
            .isEqualTo("兼职信息");
        assertThat(countWhere("mdm_child_field_definition", "child_type_id", childTypeId)).isEqualTo(7);
        assertChildField(childTypeId, "company", "兼职单位", "STRING", true, true, 255, null, null);
        assertChildField(childTypeId, "position", "兼职岗位", "STRING", true, true, 128, null, null);
        assertChildField(childTypeId, "start_date", "开始日期", "DATE", false, true, null, null, null);
        assertChildField(childTypeId, "end_date", "结束日期", "DATE", false, true, null, null, null);
        assertChildField(childTypeId, "part_time_type", "兼职类型", "STRING", false, false, 64, null, null);
        assertChildField(childTypeId, "monthly_income", "月收入", "DECIMAL", false, false, null, 18, 2);
        assertChildField(childTypeId, "notes", "备注", "TEXT", false, false, null, null, null);

        User approver = user(system, "hr_approver");
        assertThat(countQuery("""
            select count(*) from sys_approver_assignment
            where system_id=:system and object_type_id=:object and department_id=:department
              and approver_user_id=:user and status='active'
            """, "system", system.getId(), "object", person.getId(), "department", hr.getId(),
            "user", approver.getId())).isEqualTo(1);

        assertEffectiveDemoRecord(system, person, hr, "HR-PERSON-001", "HR001", "张晓梅", childTypeId);
        assertEffectiveDemoRecord(system, person, sales, "SALES-PERSON-001", "SALES001", "王强", childTypeId);
        assertThat(records.findAll()).hasSize(2);
        assertThat(count("mdm_record_value")).isEqualTo(16);
        assertThat(count("mdm_child_record")).isEqualTo(2);
        assertThat(count("mdm_child_record_value")).isEqualTo(14);

        RecordAccessService access = new RecordAccessService(departments, authorization);
        RecordProjectionService projection = new RecordProjectionService(
            fields, values, childRecords, childFields, childValues, access);
        MdmRecord hrRecord = records.findAll().stream()
            .filter(record -> record.getRecordCode().equals("HR-PERSON-001")).findFirst().orElseThrow();
        assertThat(projection.records(crossViewer, "person", person.getId(), List.of(hrRecord)))
            .singleElement().satisfies(response -> assertThat(response.data()).hasSize(8));
        ChildType partTime = childTypes.findBySystemIdAndObjectTypeIdAndCode(
            system.getId(), person.getId(), "part_time").orElseThrow();
        assertThat(projection.children(crossViewer, hrRecord, "part_time", partTime))
            .singleElement().satisfies(row -> assertThat(projectedData(row))
                .containsOnlyKeys("company", "position", "start_date", "end_date"));

        User admin = user(system, "admin");
        MdmRecord ownRecord = records.saveAndFlush(MdmRecord.create(
            system.getId(), person, person.getId(), other, "OTHER-PERSON-TEST", admin.getId()));
        Map<String, FieldDefinition> masterDefinitions = fields.findByObjectTypeId(person.getId()).stream()
            .collect(java.util.stream.Collectors.toMap(FieldDefinition::getFieldKey, field -> field));
        persistRecordValues(ownRecord, masterDefinitions, admin.getId(), Map.of(
            "employee_code", stringValue("OTHER001"),
            "employee_name", stringValue("跨部门查看员本人"),
            "gender", stringValue("女"),
            "birth_date", dateValue(LocalDate.of(1992, 3, 4)),
            "mobile_phone", stringValue("13800000003"),
            "work_email", stringValue("other@example.com"),
            "hire_date", dateValue(LocalDate.of(2022, 6, 1)),
            "employment_status", stringValue("在职")));
        ChildRecord ownChild = ChildRecord.create(ownRecord, partTime, 0, admin.getId());
        em.persist(ownChild);
        em.flush();
        persistChildValues(ownChild, partTime, admin.getId(), Map.of(
            "company", stringValue("本人兼职单位"),
            "position", stringValue("顾问"),
            "start_date", dateValue(LocalDate.of(2025, 2, 1)),
            "end_date", dateValue(LocalDate.of(2025, 9, 30)),
            "part_time_type", stringValue("项目合作"),
            "monthly_income", decimalValue("7000.00"),
            "notes", textValue("本人私有备注")));
        em.flush();

        assertThat(projection.records(crossViewer, "person", person.getId(), List.of(ownRecord)))
            .singleElement().satisfies(response -> assertThat(response.data()).hasSize(8));
        assertThat(projection.children(crossViewer, ownRecord, "part_time", partTime))
            .singleElement().satisfies(row -> assertThat(projectedData(row)).hasSize(7)
                .containsKeys("company", "position", "start_date", "end_date",
                    "part_time_type", "monthly_income", "notes"));
    }

    @Test
    void bootstrapSeedsFourEncryptedPublicDistributionEndpointsWithoutDuplicates() throws Exception {
        SystemEntity isolatedSystem = systems.saveAndFlush(SystemEntity.create("ISOLATED", "Isolated"));
        DataInitializer initializer = initializer(true);

        initializer.run();
        initializer.run();
        em.flush();
        em.clear();

        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        assertThat(endpoints.findBySystemIdOrderByCode(system.getId()))
            .extracting(PushEndpoint::getCode, PushEndpoint::getAuthenticationType,
                PushEndpoint::getEndpointUrl, PushEndpoint::hasCredentials)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("DEMO_HTTPBIN_NONE", "NONE", "https://1.1.1.1/post", false),
                org.assertj.core.groups.Tuple.tuple("DEMO_HTTPBIN_BASIC", "BASIC", "https://1.1.1.1/post", true),
                org.assertj.core.groups.Tuple.tuple("DEMO_HTTPBIN_BEARER", "BEARER", "https://1.1.1.1/post", true),
                org.assertj.core.groups.Tuple.tuple("DEMO_HTTPBIN_API_KEY", "API_KEY", "https://1.1.1.1/post", true));
        assertThat(endpoints.findBySystemIdOrderByCode(isolatedSystem.getId())).isEmpty();
        assertThat(endpoints.findBySystemIdOrderByCode(system.getId()))
            .filteredOn(PushEndpoint::hasCredentials)
            .extracting(PushEndpoint::getEncryptedCredentials)
            .allSatisfy(value -> assertThat(value).doesNotContain("local-demo-"));
        verify(endpointUrls, times(4)).validate("https://1.1.1.1/post");
    }

    @Test
    void disabledBootstrapCreatesNothing() throws Exception {
        initializer(false).run();
        assertThat(systems.count()).isZero();
    }

    @Test
    void bootstrapCompletesPartiallyExistingStableCodesWithoutOverwritingPasswordsOrNames() throws Exception {
        SystemEntity system = systems.saveAndFlush(SystemEntity.create("DEFAULT", "Existing Name"));
        Department root = departments.saveAndFlush(Department.create(system, null, "ROOT", "Existing Root"));
        root.relocate(null, "/" + root.getId() + "/", 1);
        departments.saveAndFlush(root);
        User admin = User.create(system, root, "admin", encoder.encode("keep-admin-secret"), "Existing Admin");
        admin.makeSystemAdmin();
        users.saveAndFlush(admin);
        Department hr = departments.saveAndFlush(Department.create(system, root, "HR", "现有人力资源"));
        hr.relocate(root, root.getPath() + hr.getId() + "/", 2);
        departments.saveAndFlush(hr);
        User editor = users.saveAndFlush(User.create(system, hr, "hr_editor",
            encoder.encode("keep-editor-secret"), "现有编辑员"));
        String adminHash = password(admin.getId());
        String editorHash = password(editor.getId());

        initializer(true).run();
        em.flush();
        em.clear();

        assertThat(password(admin.getId())).isEqualTo(adminHash);
        assertThat(password(editor.getId())).isEqualTo(editorHash);
        assertThat(encoder.matches("keep-admin-secret", adminHash)).isTrue();
        assertThat(encoder.matches("keep-editor-secret", editorHash)).isTrue();
        assertThat(user(system, "hr_editor").getRealName()).isEqualTo("现有编辑员");
        assertThat(roleCodes(editor.getId())).containsExactly("DEPT_EDITOR");
        assertThat(count("mdm_record")).isEqualTo(2);
    }

    @Test
    void concurrentBootstrapCallsBothSucceedAndCreateOneStableGraph() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Void> call = () -> {
            start.await();
            initializer(true).run();
            return null;
        };
        var first = pool.submit(call);
        var second = pool.submit(call);
        start.countDown();
        first.get(10, java.util.concurrent.TimeUnit.SECONDS);
        second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdownNow();

        em.clear();
        assertThat(departments.count()).isEqualTo(5);
        assertThat(users.count()).isEqualTo(5);
        assertThat(count("sys_role")).isEqualTo(5);
        assertThat(count("sys_user_role")).isEqualTo(5);
        assertThat(count("mdm_field_definition")).isEqualTo(8);
        assertThat(count("mdm_child_field_definition")).isEqualTo(7);
        assertThat(count("mdm_record")).isEqualTo(2);
        cleanupConcurrentBootstrapData();
    }

    @Test
    void conflictingStableDepartmentTopologyFailsFast() throws Exception {
        SystemEntity system = systems.saveAndFlush(SystemEntity.create("DEFAULT", "Existing"));
        departments.saveAndFlush(Department.create(system, null, "HR", "Wrong Root"));
        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HR").hasMessageContaining("parent");
    }

    @Test
    void conflictingStableDepartmentPathAndLevelFailFast() throws Exception {
        initializer(true).run();
        em.flush();
        em.createNativeQuery("update sys_department set path='/wrong/', level=9 where code='HR'").executeUpdate();
        em.flush();
        em.clear();
        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HR").hasMessageContaining("path/level");
    }

    @Test
    void conflictingBusinessIdentityFailsFastWithoutReplacingItsPassword() throws Exception {
        initializer(true).run();
        em.flush();
        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        User editor = user(system, "hr_editor");
        String hash = password(editor.getId());
        Long otherId = department(system, "OTHER").getId();
        em.createNativeQuery("update sys_user set department_id=:department where id=:id")
            .setParameter("department", otherId).setParameter("id", editor.getId()).executeUpdate();
        em.flush();
        em.clear();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hr_editor").hasMessageContaining("department");
        assertThat(password(editor.getId())).isEqualTo(hash);
    }

    @Test
    void conflictingStableFieldSemanticsFailsFast() throws Exception {
        initializer(true).run();
        em.flush();
        em.createNativeQuery("update mdm_field_definition set data_type='INTEGER' where field_key='employee_code'")
            .executeUpdate();
        em.flush();
        em.clear();
        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("employee_code").hasMessageContaining("semantics");
    }

    @Test
    void inactiveStableFieldFailsFast() throws Exception {
        assertFieldMutationFails("status='inactive'", "employee_code");
    }

    @Test
    void wrongStableChildSharedFlagFailsFast() throws Exception {
        initializer(true).run();
        em.flush();
        em.createNativeQuery("update mdm_child_field_definition set shared=false where field_key='company'")
            .executeUpdate();
        em.flush();
        em.clear();
        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("company").hasMessageContaining("semantics");
    }

    @Test
    void inactiveStableChildTypeFailsFast() throws Exception {
        initializer(true).run();
        em.flush();
        em.createNativeQuery("update mdm_child_type set status='inactive' where code='part_time'").executeUpdate();
        em.flush();
        em.clear();
        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("part_time").hasMessageContaining("semantics");
    }

    @Test
    void restartPreservesUnrelatedBusinessRecordsAndTheirValues() throws Exception {
        DataInitializer initializer = initializer(true);
        initializer.run();
        em.flush();
        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        User admin = user(system, "admin");
        String hash = password(admin.getId());
        Department hr = department(system, "HR");
        ObjectType person = objects.findBySystemIdAndCode(system.getId(), "person").orElseThrow();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(system.getId(), person, person.getId(), hr,
            "EXISTING", admin.getId()));
        FieldDefinition name = em.createQuery(
                "select f from FieldDefinition f where f.objectTypeId=:object and f.fieldKey='employee_name'",
                FieldDefinition.class)
            .setParameter("object", person.getId()).getSingleResult();
        em.persist(RecordValue.create(existing, name,
            new TypedValue("用户现存数据", null, null, null, null, null, null, null), admin.getId()));
        em.flush();

        initializer.run();
        em.flush();
        em.clear();

        assertThat(password(admin.getId())).isEqualTo(hash);
        assertThat(singleString("""
            select rv.string_value from mdm_record_value rv
            join mdm_field_definition f on f.id=rv.field_definition_id
            where rv.record_id=:record and f.field_key='employee_name'
            """, "record", existing.getId())).isEqualTo("用户现存数据");
        assertThat(records.findAll()).extracting(MdmRecord::getRecordCode)
            .containsExactlyInAnyOrder("HR-PERSON-001", "SALES-PERSON-001", "EXISTING");
    }

    @Test
    void restartPreservesUserExtensionsOnSeedMetadataAndDemoRecords() throws Exception {
        DataInitializer initializer = initializer(true);
        initializer.run();
        em.flush();
        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        User admin = user(system, "admin");
        ObjectType person = objects.findBySystemIdAndCode(system.getId(), "person").orElseThrow();
        MdmRecord hrRecord = records.findAll().stream()
            .filter(record -> record.getRecordCode().equals("HR-PERSON-001")).findFirst().orElseThrow();
        FieldDefinition customMaster = FieldDefinition.create(person.getId(), person,
            new CreateFieldCommand("custom_badge", "用户扩展标识", FieldDataType.STRING,
                false, false, true, false, 64, null, null, null, null, null, 99), null);
        em.persist(customMaster);
        em.flush();
        em.persist(RecordValue.create(hrRecord, customMaster,
            new TypedValue("KEEP-MASTER", null, null, null, null, null, null, null), admin.getId()));

        ChildType partTime = childTypes.findBySystemIdAndObjectTypeIdAndCode(
            system.getId(), person.getId(), "part_time").orElseThrow();
        ChildFieldDefinition customChild = ChildFieldDefinition.create(partTime.getId(), partTime,
            new CreateFieldCommand("custom_child_note", "用户扩展子备注", FieldDataType.TEXT,
                false, false, true, false, null, null, null, null, null, null, 99), null);
        em.persist(customChild);
        em.flush();
        ChildRecord child = childRecords.findBySystemIdAndRecordIdAndChildTypeId(
            system.getId(), hrRecord.getId(), partTime.getId()).get(0);
        em.persist(ChildRecordValue.create(child, customChild,
            new TypedValue(null, "KEEP-CHILD", null, null, null, null, null, null), admin.getId()));
        em.flush();

        initializer.run();
        em.flush();
        em.clear();

        assertThat(countWhere("mdm_field_definition", "object_type_id", person.getId())).isEqualTo(9);
        assertThat(countWhere("mdm_child_field_definition", "child_type_id", partTime.getId())).isEqualTo(8);
        assertThat(recordStringValue(hrRecord.getId(), "custom_badge")).isEqualTo("KEEP-MASTER");
        assertThat(singleString("""
            select v.text_value from mdm_child_record_value v
            join mdm_child_field_definition f on f.id=v.field_definition_id
            where v.child_record_id=:child and f.field_key='custom_child_note'
            """, "child", child.getId())).isEqualTo("KEEP-CHILD");
    }

    @Test
    void restartPreservesAdditionalApprovedPartTimeRows() throws Exception {
        DataInitializer initializer = initializer(true);
        initializer.run();
        em.flush();
        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        User admin = user(system, "admin");
        ObjectType person = objects.findBySystemIdAndCode(system.getId(), "person").orElseThrow();
        ChildType partTime = childTypes.findBySystemIdAndObjectTypeIdAndCode(
            system.getId(), person.getId(), "part_time").orElseThrow();
        MdmRecord hrRecord = records.findAll().stream()
            .filter(record -> record.getRecordCode().equals("HR-PERSON-001")).findFirst().orElseThrow();
        Long seedId = childRecords.findBySystemIdAndRecordIdAndChildTypeId(
            system.getId(), hrRecord.getId(), partTime.getId()).get(0).getId();

        ChildRecord extra = ChildRecord.create(hrRecord, partTime, 99, admin.getId());
        em.persist(extra);
        em.flush();
        persistChildValues(extra, partTime, admin.getId(), Map.of(
            "company", stringValue("用户兼职单位"),
            "position", stringValue("用户岗位"),
            "start_date", dateValue(LocalDate.of(2025, 1, 1)),
            "end_date", dateValue(LocalDate.of(2025, 12, 31)),
            "part_time_type", stringValue("用户扩展"),
            "monthly_income", decimalValue("4321.00"),
            "notes", textValue("KEEP-EXTRA")));
        em.flush();
        Long extraId = extra.getId();

        initializer.run();
        em.flush();
        em.clear();

        assertThat(childRecords.findBySystemIdAndRecordIdAndChildTypeId(
            system.getId(), hrRecord.getId(), partTime.getId()))
            .extracting(ChildRecord::getId).containsExactlyInAnyOrder(seedId, extraId);
        assertThat(singleString("""
            select v.text_value from mdm_child_record_value v
            join mdm_child_field_definition f on f.id=v.field_definition_id
            where v.child_record_id=:child and f.field_key='notes'
            """, "child", extraId)).isEqualTo("KEEP-EXTRA");
    }

    @Test
    void upgradesExactLegacyV1SeedInPlaceAndIsIdempotent() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "LEGACY-PERSON-001", legacy.admin().getId()));
        persistRecordValues(existing, legacy.fields(), legacy.admin().getId(), Map.of(
            "employee_code", stringValue("LEGACY001"),
            "employee_name", stringValue("旧员工"),
            "work_email", stringValue("legacy@example.com")));
        em.flush();
        Long objectId = legacy.person().getId();
        Long employmentId = legacy.employment().getId();
        Map<String, Long> oldFieldIds = fieldIds(objectId);
        Map<String, Long> oldValueIds = recordValueIds(existing.getId());

        DataInitializer initializer = initializer(true);
        initializer.run();
        initializer.run();
        em.flush();
        em.clear();

        ObjectType person = objects.findBySystemIdAndCode(legacy.system().getId(), "person").orElseThrow();
        assertThat(person.getId()).isEqualTo(objectId);
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", objectId))
            .isEqualTo("个人信息");
        assertThat(singleBoolean("select approval_required from mdm_object_type where id=:id", "id", objectId))
            .isTrue();
        assertThat(fieldIds(objectId)).containsAllEntriesOf(oldFieldIds).hasSize(8);
        assertThat(recordValueIds(existing.getId())).isEqualTo(oldValueIds);
        assertThat(recordStringValue(existing.getId(), "employee_code")).isEqualTo("LEGACY001");
        assertThat(recordStringValue(existing.getId(), "employee_name")).isEqualTo("旧员工");
        assertThat(recordStringValue(existing.getId(), "work_email")).isEqualTo("legacy@example.com");
        assertThat(singleLong("""
            select id from mdm_child_type where object_type_id=:object and code='part_time'
            """, "object", objectId)).isEqualTo(employmentId);
        assertThat(countWhere("mdm_child_field_definition", "child_type_id", employmentId)).isEqualTo(7);
    }

    @Test
    void rejectsLegacyV1WithUnknownFieldRenameWithoutPartialUpgrade() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        em.createNativeQuery("""
            update mdm_field_definition set field_name='Custom Employee Code'
            where object_type_id=:object and field_key='employee_code'
            """).setParameter("object", legacy.person().getId()).executeUpdate();
        em.flush();
        em.clear();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("person").hasMessageContaining("legacy");
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
        assertThat(singleBoolean("select approval_required from mdm_object_type where id=:id", "id",
            legacy.person().getId())).isFalse();
    }

    @Test
    void rejectsLegacyV1WithEmploymentBusinessDataWithoutPartialUpgrade() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "LEGACY-WITH-CHILD", legacy.admin().getId()));
        ChildRecord child = ChildRecord.create(existing, legacy.employment(), 1, legacy.admin().getId());
        em.persist(child);
        em.flush();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("employment").hasMessageContaining("data");
        assertThat(countWhere("mdm_child_record", "child_type_id", legacy.employment().getId())).isEqualTo(1);
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
    }

    @Test
    void rejectsLegacyV1WhenPreservedEmployeeCodeWouldCollideWithDemoSeed() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "EXISTING-HR-CODE", legacy.admin().getId()));
        persistRecordValues(existing, legacy.fields(), legacy.admin().getId(), Map.of(
            "employee_code", stringValue("HR001"),
            "employee_name", stringValue("用户已有员工"),
            "work_email", stringValue("existing.hr001@example.com")));
        em.flush();
        Map<String, Long> oldValueIds = recordValueIds(existing.getId());

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("legacy").hasMessageContaining("demo").hasMessageContaining("HR001");
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
        assertThat(recordValueIds(existing.getId())).isEqualTo(oldValueIds);
        assertThat(recordStringValue(existing.getId(), "employee_code")).isEqualTo("HR001");
    }

    @Test
    void rejectsLegacyV1WhenFixedDemoRecordCodeIsAlreadyUserData() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "HR-PERSON-001", legacy.admin().getId()));
        persistRecordValues(existing, legacy.fields(), legacy.admin().getId(), Map.of(
            "employee_code", stringValue("USER001"),
            "employee_name", stringValue("用户固定编码记录"),
            "work_email", stringValue("user001@example.com")));
        em.flush();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("legacy").hasMessageContaining("demo").hasMessageContaining("HR-PERSON-001");
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
        assertThat(recordStringValue(existing.getId(), "employee_code")).isEqualTo("USER001");
    }

    @Test
    void rejectsLegacyV1WhenPendingCreateReservesDemoRecordCode() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        ApprovalRequest pending = ApprovalRequest.pending(legacy.system().getId(), legacy.person().getId(),
            ApprovalRequest.Operation.CREATE, null, "SALES-PERSON-001", legacy.hr().getId(),
            legacy.admin().getId(), null);
        em.persist(pending);
        em.flush();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("legacy").hasMessageContaining("demo").hasMessageContaining("SALES-PERSON-001");
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
        assertThat(singleString("select status from wf_approval_request where id=:id", "id", pending.getId()))
            .isEqualTo("PENDING");
    }

    @Test
    void rejectsLegacyV1WhenPendingUpdateProposesDemoEmployeeCode() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "PENDING-UPDATE", legacy.admin().getId()));
        ApprovalRequest pending = ApprovalRequest.pending(legacy.system().getId(), legacy.person().getId(),
            existing.getId(), legacy.hr().getId(), legacy.admin().getId(), existing.getVersion());
        em.persist(pending);
        em.flush();
        em.persist(ApprovalChange.create(legacy.system().getId(), pending.getId(),
            legacy.fields().get("employee_code").getId(), stringValue("USER002"), stringValue("SALES001")));
        em.flush();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("legacy").hasMessageContaining("demo").hasMessageContaining("SALES001");
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
        assertThat(singleString("select status from wf_approval_request where id=:id", "id", pending.getId()))
            .isEqualTo("PENDING");
    }

    @Test
    void rejectsLegacyV1WithInactiveAndDeletedEmploymentRows() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "LEGACY-INACTIVE-CHILD", legacy.admin().getId()));
        ChildRecord inactive = ChildRecord.create(existing, legacy.employment(), 1, legacy.admin().getId());
        ChildRecord deleted = ChildRecord.create(existing, legacy.employment(), 2, legacy.admin().getId());
        em.persist(inactive);
        em.persist(deleted);
        em.flush();
        em.createNativeQuery("update mdm_child_record set status='inactive' where id=:id")
            .setParameter("id", inactive.getId()).executeUpdate();
        deleted.softDelete(legacy.admin().getId());
        em.flush();
        em.clear();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("employment").hasMessageContaining("data");
        assertThat(countWhere("mdm_child_record", "child_type_id", legacy.employment().getId())).isEqualTo(2);
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
    }

    @Test
    void rejectsLegacyV1WithPendingEmploymentApprovalChange() throws Exception {
        LegacySeed legacy = seedLegacyV1();
        MdmRecord existing = records.saveAndFlush(MdmRecord.create(legacy.system().getId(), legacy.person(),
            legacy.person().getId(), legacy.hr(), "LEGACY-PENDING-CHILD", legacy.admin().getId()));
        ApprovalRequest pending = ApprovalRequest.pending(legacy.system().getId(), legacy.person().getId(),
            existing.getId(), legacy.hr().getId(), legacy.admin().getId(), existing.getVersion());
        em.persist(pending);
        em.flush();
        em.persist(ApprovalChildChange.create(legacy.system().getId(), pending.getId(), "new-employment",
            legacy.employment().getId(), null, ApprovalChildChange.Operation.CREATE, null, 0));
        em.flush();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("employment").hasMessageContaining("data");
        assertThat(countWhere("wf_approval_child_change", "child_type_id", legacy.employment().getId()))
            .isEqualTo(1);
        assertThat(singleString("select name from mdm_object_type where id=:id", "id", legacy.person().getId()))
            .isEqualTo("Person");
    }

    @Test
    void existingBusinessRoleWithExtraPermissionFailsFast() throws Exception {
        initializer(true).run();
        Role role = em.createQuery("select r from Role r where r.code='DEPT_VIEWER'", Role.class).getSingleResult();
        Permission permission = em.createQuery("select p from Permission p where p.code='MDM_FIELD_MANAGE'",
            Permission.class).getSingleResult();
        em.persist(RolePermission.grant(role, permission));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DEPT_VIEWER").hasMessageContaining("permissions");
    }

    @Test
    void existingBusinessUserWithExtraRoleFailsFast() throws Exception {
        initializer(true).run();
        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        User viewer = user(system, "hr_viewer");
        Role extraRole = em.createQuery("select r from Role r where r.code='CROSS_DEPT_VIEWER'", Role.class)
            .getSingleResult();
        em.persist(UserRole.assign(system, viewer, extraRole));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hr_viewer").hasMessageContaining("role");
    }

    @Test
    void existingBusinessUserWithExtraScopeFailsFast() throws Exception {
        initializer(true).run();
        SystemEntity system = systems.findByCode("DEFAULT").orElseThrow();
        User viewer = user(system, "hr_viewer");
        Department other = department(system, "OTHER");
        em.persist(UserDepartmentScope.grant(system, viewer, other,
            UserDepartmentScope.ScopeMode.SELF, true, false));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hr_viewer").hasMessageContaining("SELF scope");
    }

    private LegacySeed seedLegacyV1() {
        SystemEntity system = systems.saveAndFlush(SystemEntity.create("DEFAULT", "Default MDM"));
        Department root = departments.saveAndFlush(Department.create(system, null, "ROOT", "Head Office"));
        root.relocate(null, "/" + root.getId() + "/", 1);
        departments.saveAndFlush(root);
        Department hr = departments.saveAndFlush(Department.create(system, root, "HR", "Human Resources"));
        hr.relocate(root, root.getPath() + hr.getId() + "/", 2);
        departments.saveAndFlush(hr);
        Department it = departments.saveAndFlush(Department.create(system, root, "IT", "Information Technology"));
        it.relocate(root, root.getPath() + it.getId() + "/", 2);
        departments.saveAndFlush(it);

        User admin = User.create(system, root, "admin", encoder.encode("legacy-admin"), "Administrator");
        admin.makeSystemAdmin();
        users.saveAndFlush(admin);
        Role adminRole = Role.create(system, "SYSTEM_ADMIN", "System Administrator");
        em.persist(adminRole);
        em.flush();
        for (String code : List.of("MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "MDM_FIELD_MANAGE", "APPROVAL_REVIEW")) {
            Permission permission = Permission.create(code, code);
            em.persist(permission);
            em.flush();
            em.persist(RolePermission.grant(adminRole, permission));
        }
        em.persist(UserRole.assign(system, admin, adminRole));
        em.persist(UserDepartmentScope.grant(
            system, admin, root, UserDepartmentScope.ScopeMode.SUBTREE, true, true));

        ObjectType person = objects.saveAndFlush(ObjectType.create(system, "person", "Person"));
        Map<String, FieldDefinition> legacyFields = new LinkedHashMap<>();
        legacyFields.put("employee_code", legacyField(person, "employee_code", "Employee Code", true, true, 64, 1));
        legacyFields.put("employee_name", legacyField(person, "employee_name", "Employee Name", true, false, 128, 2));
        legacyFields.put("work_email", legacyField(person, "work_email", "Work Email", false, true, 255, 3));
        ChildType employment = ChildType.create(person.getId(), person, "employment", "Employment");
        em.persist(employment);
        em.flush();
        return new LegacySeed(system, hr, admin, person, legacyFields, employment);
    }

    private FieldDefinition legacyField(ObjectType person, String key, String name, boolean required,
                                        boolean unique, Integer maxLength, int sortOrder) {
        FieldDefinition field = FieldDefinition.create(person.getId(), person,
            new CreateFieldCommand(key, name, FieldDataType.STRING, required, unique, true, false,
                maxLength, null, null, null, null, null, sortOrder), null);
        em.persist(field);
        em.flush();
        return field;
    }

    private void persistRecordValues(MdmRecord record, Map<String, FieldDefinition> definitions, Long actorId,
                                     Map<String, TypedValue> seededValues) {
        seededValues.forEach((key, value) -> em.persist(
            RecordValue.create(record, definitions.get(key), value, actorId)));
    }

    private void persistChildValues(ChildRecord child, ChildType childType, Long actorId,
                                    Map<String, TypedValue> seededValues) {
        Map<String, ChildFieldDefinition> definitions = childFields.findByChildTypeId(childType.getId()).stream()
            .collect(java.util.stream.Collectors.toMap(ChildFieldDefinition::getFieldKey, field -> field));
        seededValues.forEach((key, value) -> em.persist(
            ChildRecordValue.create(child, definitions.get(key), value, actorId)));
    }

    private Map<String, Long> fieldIds(Long objectId) {
        List<Object[]> rows = em.createNativeQuery("""
                select field_key,id from mdm_field_definition where object_type_id=:object
                """, Object[].class)
            .setParameter("object", objectId).getResultList();
        return rows.stream()
            .collect(java.util.stream.Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));
    }

    private Map<String, Long> recordValueIds(Long recordId) {
        List<Object[]> rows = em.createNativeQuery("""
                select f.field_key,v.id from mdm_record_value v
                join mdm_field_definition f on f.id=v.field_definition_id where v.record_id=:record
                """, Object[].class)
            .setParameter("record", recordId).getResultList();
        return rows.stream()
            .collect(java.util.stream.Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));
    }

    private TypedValue stringValue(String value) {
        return new TypedValue(value, null, null, null, null, null, null, null);
    }

    private TypedValue textValue(String value) {
        return new TypedValue(null, value, null, null, null, null, null, null);
    }

    private TypedValue dateValue(LocalDate value) {
        return new TypedValue(null, null, null, null, null, value, null, null);
    }

    private TypedValue decimalValue(String value) {
        return new TypedValue(null, null, null, new BigDecimal(value), null, null, null, null);
    }

    private record LegacySeed(SystemEntity system, Department hr, User admin, ObjectType person,
                              Map<String, FieldDefinition> fields, ChildType employment) {
    }

    private void assertBusinessIdentity(SystemEntity system, String username, Department department, String role,
                                        Set<String> permissions, boolean canEdit) {
        User user = user(system, username);
        assertThat(user.getDepartmentId()).isEqualTo(department.getId());
        assertThat(user.isActive()).isTrue();
        assertThat(user.isSystemAdmin()).isFalse();
        assertThat(encoder.matches("123456", password(user.getId()))).isTrue();
        assertThat(password(user.getId())).startsWith("$2");
        assertThat(roleCodes(user.getId())).containsExactly(role);
        assertThat(permissionCodes(user.getId())).containsExactlyInAnyOrderElementsOf(permissions);
        List<Object[]> scopes = em.createNativeQuery("""
            select department_id,scope_mode,can_view,can_edit from sys_user_department_scope where user_id=:user
            """, Object[].class).setParameter("user", user.getId()).getResultList();
        assertThat(scopes).hasSize(1);
        assertThat(((Number) scopes.get(0)[0]).longValue()).isEqualTo(department.getId());
        assertThat(scopes.get(0)[1]).isEqualTo("SELF");
        assertThat((Boolean) scopes.get(0)[2]).isTrue();
        assertThat((Boolean) scopes.get(0)[3]).isEqualTo(canEdit);
    }

    private void assertMasterField(ObjectType person, String key, String name, String type, boolean required,
                                   boolean unique, Integer maxLength) {
        assertThat(countQuery("""
            select count(*) from mdm_field_definition
            where object_type_id=:object and field_key=:key and field_name=:name and data_type=:type
              and required=:required and unique_value=:unique and searchable=true and shared=false
              and ((:maxLength is null and max_length is null) or max_length=:maxLength)
              and status='active'
            """, "object", person.getId(), "key", key, "name", name, "type", type,
            "required", required, "unique", unique, "maxLength", maxLength)).isEqualTo(1);
    }

    private void assertChildField(Long childTypeId, String key, String name, String type, boolean required,
                                  boolean shared, Integer maxLength, Integer precision, Integer scale) {
        assertThat(countQuery("""
            select count(*) from mdm_child_field_definition
            where child_type_id=:child and field_key=:key and field_name=:name and data_type=:type
              and required=:required and unique_value=false and searchable=true and shared=:shared
              and ((:maxLength is null and max_length is null) or max_length=:maxLength)
              and ((:precision is null and precision_value is null) or precision_value=:precision)
              and ((:scale is null and scale_value is null) or scale_value=:scale)
              and status='active'
            """, "child", childTypeId, "key", key, "name", name, "type", type,
            "required", required, "shared", shared, "maxLength", maxLength,
            "precision", precision, "scale", scale)).isEqualTo(1);
    }

    private void assertEffectiveDemoRecord(SystemEntity system, ObjectType person, Department department,
                                           String recordCode, String employeeCode, String employeeName,
                                           Long childTypeId) {
        Long recordId = singleLong("""
            select id from mdm_record where system_id=:system and object_type_id=:object
              and department_id=:department and record_code=:code and status='active'
              and approval_status='approved' and deleted_at is null
            """, "system", system.getId(), "object", person.getId(), "department", department.getId(),
            "code", recordCode);
        assertThat(countWhere("mdm_record_value", "record_id", recordId)).isEqualTo(8);
        assertThat(recordStringValue(recordId, "employee_code")).isEqualTo(employeeCode);
        assertThat(recordStringValue(recordId, "employee_name")).isEqualTo(employeeName);
        Long childId = singleLong("""
            select id from mdm_child_record where system_id=:system and record_id=:record and child_type_id=:type
              and status='active' and deleted_at is null
            """, "system", system.getId(), "record", recordId, "type", childTypeId);
        assertThat(countWhere("mdm_child_record_value", "child_record_id", childId)).isEqualTo(7);
        assertThat(countQuery("""
            select count(*) from mdm_child_record_value v
            join mdm_child_field_definition f on f.id=v.field_definition_id
            where v.child_record_id=:child and f.shared=true
            """, "child", childId)).isEqualTo(4);
    }

    private String recordStringValue(Long recordId, String key) {
        return singleString("""
            select v.string_value from mdm_record_value v
            join mdm_field_definition f on f.id=v.field_definition_id
            where v.record_id=:record and f.field_key=:key
            """, "record", recordId, "key", key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> projectedData(Map<String, Object> row) {
        assertThat(row.get("data")).isInstanceOf(Map.class);
        return (Map<String, Object>) row.get("data");
    }

    private Department department(SystemEntity system, String code) {
        return departments.findBySystemIdAndCode(system.getId(), code).orElseThrow();
    }

    private User user(SystemEntity system, String username) {
        return users.findBySystemIdAndUsername(system.getId(), username).orElseThrow();
    }

    private List<String> roleCodes(Long userId) {
        return em.createNativeQuery("""
            select r.code from sys_user_role ur join sys_role r on r.id=ur.role_id
            where ur.user_id=:user order by r.code
            """, String.class).setParameter("user", userId).getResultList();
    }

    private List<String> permissionCodes(Long userId) {
        return em.createNativeQuery("""
            select distinct p.code from sys_user_role ur
            join sys_role_permission rp on rp.role_id=ur.role_id
            join sys_permission p on p.id=rp.permission_id
            where ur.user_id=:user order by p.code
            """, String.class).setParameter("user", userId).getResultList();
    }

    private DataInitializer initializer(boolean enabled) {
        return new DataInitializer(em, systems, departments, users, objects, endpoints, endpointUrls,
            credentials, coordinator, enabled, encoder);
    }

    private void assertFieldMutationFails(String assignment, String key) throws Exception {
        initializer(true).run();
        em.flush();
        em.createNativeQuery("update mdm_field_definition set " + assignment + " where field_key=:key")
            .setParameter("key", key).executeUpdate();
        em.flush();
        em.clear();
        assertThatThrownBy(() -> initializer(true).run()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(key).hasMessageContaining("semantics");
    }

    private long count(String table) {
        return ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
    }

    private long countWhere(String table, String column, Long value) {
        return ((Number) em.createNativeQuery("select count(*) from " + table + " where " + column + "=:value")
            .setParameter("value", value).getSingleResult()).longValue();
    }

    private long countQuery(String sql, Object... parameters) {
        return ((Number) nativeQuery(sql, parameters).getSingleResult()).longValue();
    }

    private Long singleLong(String sql, Object... parameters) {
        return ((Number) nativeQuery(sql, parameters).getSingleResult()).longValue();
    }

    private String singleString(String sql, Object... parameters) {
        return (String) nativeQuery(sql, parameters).getSingleResult();
    }

    private boolean singleBoolean(String sql, Object... parameters) {
        return (Boolean) nativeQuery(sql, parameters).getSingleResult();
    }

    private jakarta.persistence.Query nativeQuery(String sql, Object... parameters) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < parameters.length; i += 2) {
            query.setParameter((String) parameters[i], parameters[i + 1]);
        }
        return query;
    }

    private String password(Long id) {
        return (String) em.createNativeQuery("select password_hash from sys_user where id=:id")
            .setParameter("id", id).getSingleResult();
    }

    private void cleanupConcurrentBootstrapData() {
        var cleanup = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        cleanup.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> {
            List<String> tables = List.of(
                "mdm_child_record_value", "mdm_child_record", "mdm_record_value", "mdm_record",
                "sys_approver_assignment", "mdm_child_field_definition", "mdm_child_type",
                "mdm_field_definition", "mdm_object_type", "sys_user_department_scope", "sys_user_role",
                "sys_role_permission", "sys_permission", "sys_role", "sys_user", "sys_department", "sys_system");
            tables.forEach(table -> em.createNativeQuery("delete from " + table).executeUpdate());
        });
    }
}
