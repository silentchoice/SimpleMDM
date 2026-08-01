package com.simplemdm.config;

import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.Permission;
import com.simplemdm.model.system.Role;
import com.simplemdm.model.system.RolePermission;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.model.system.UserDepartmentScope;
import com.simplemdm.model.system.UserRole;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.service.mdm.CreateFieldCommand;
import com.simplemdm.service.integration.CredentialEncryptionService;
import com.simplemdm.service.integration.EndpointUrlPolicy;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final String SYSTEM_CODE = "DEFAULT";
    private static final Set<String> SYSTEM_ADMIN_PERMISSIONS = Set.of(
        "MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "MDM_FIELD_MANAGE", "APPROVAL_REVIEW");

    private final EntityManager entityManager;
    private final SystemRepository systems;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final ObjectTypeRepository objectTypes;
    private final PushEndpointRepository endpoints;
    private final EndpointUrlPolicy endpointUrls;
    private final CredentialEncryptionService credentials;
    private final BootstrapCoordinator coordinator;
    private final boolean enabled;
    private final BCryptPasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    public DataInitializer(EntityManager entityManager, SystemRepository systems,
                           DepartmentRepository departments, UserRepository users,
                           ObjectTypeRepository objectTypes, PushEndpointRepository endpoints,
                           EndpointUrlPolicy endpointUrls, CredentialEncryptionService credentials,
                           BootstrapCoordinator coordinator,
                           @Value("${app.bootstrap.enabled:false}") boolean enabled) {
        this(entityManager, systems, departments, users, objectTypes, endpoints, endpointUrls, credentials, coordinator, enabled,
            new BCryptPasswordEncoder());
    }

    DataInitializer(EntityManager entityManager, SystemRepository systems,
                    DepartmentRepository departments, UserRepository users,
                    ObjectTypeRepository objectTypes, PushEndpointRepository endpoints,
                    EndpointUrlPolicy endpointUrls, CredentialEncryptionService credentials,
                    BootstrapCoordinator coordinator, boolean enabled,
                    BCryptPasswordEncoder passwordEncoder) {
        this.entityManager = entityManager;
        this.systems = systems;
        this.departments = departments;
        this.users = users;
        this.objectTypes = objectTypes;
        this.endpoints = endpoints;
        this.endpointUrls = endpointUrls;
        this.credentials = credentials;
        this.coordinator = coordinator;
        this.enabled = enabled;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        coordinator.withLockedSystem(SYSTEM_CODE, "Default MDM", this::seed);
    }

    private void seed(SystemEntity system) {
        requireActive("system DEFAULT",
            "select count(s) from SystemEntity s where s.id=:id and s.status='active'", system.getId());

        Department root = department(system, null, "ROOT", "组织根");
        Department hr = department(system, root, "HR", "人力资源");
        department(system, root, "IT", "信息技术部");
        Department sales = department(system, root, "SALES", "销售部");
        Department other = department(system, root, "OTHER", "其他部门");

        User admin = admin(system, root);
        Map<String, Permission> permissions = seedPermissions();
        seedAdministratorRbac(system, root, admin, permissions);

        Role approverRole = exactRole(system, "DEPT_APPROVER", "部门审批员", permissions,
            Set.of("MDM_RECORD_VIEW", "APPROVAL_REVIEW", "INTEGRATION_MANUAL_PUSH"));
        Role editorRole = exactRole(system, "DEPT_EDITOR", "部门编辑员", permissions,
            Set.of("MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "INTEGRATION_MANUAL_PUSH"));
        Role viewerRole = exactRole(system, "DEPT_VIEWER", "部门查看员", permissions,
            Set.of("MDM_RECORD_VIEW"));
        Role crossViewerRole = exactRole(system, "CROSS_DEPT_VIEWER", "跨部门查看员", permissions,
            Set.of("MDM_RECORD_VIEW", "MDM_RECORD_CROSS_VIEW"));

        User approver = businessUser(system, hr, "hr_approver", "人力资源审批员", approverRole, true);
        businessUser(system, hr, "hr_editor", "人力资源编辑员", editorRole, true);
        businessUser(system, hr, "hr_viewer", "人力资源查看员", viewerRole, false);
        businessUser(system, other, "cross_viewer", "跨部门查看员", crossViewerRole, false);

        ObjectType person = personObject(system);
        Map<String, FieldDefinition> masterFields = seedPersonFields(person);
        ChildType partTime = childType(person, "part_time", "兼职信息");
        Map<String, ChildFieldDefinition> childFields = seedPartTimeFields(partTime);
        approverAssignment(system, person, hr, approver);

        rejectCurrentDemoIdentityCollisions(person, masterFields.get("employee_code"));

        demoRecord(system, person, hr, admin, "HR-PERSON-001", masterFields, partTime, childFields,
            hrMasterValues(), hrPartTimeValues());
        demoRecord(system, person, sales, admin, "SALES-PERSON-001", masterFields, partTime, childFields,
            salesMasterValues(), salesPartTimeValues());
        seedDistributionEndpoints(system);
    }

    private void seedDistributionEndpoints(SystemEntity system) {
        demoEndpoint(system, "DEMO_HTTPBIN_NONE", "本地演示 - 无认证", "NONE", Map.of());
        demoEndpoint(system, "DEMO_HTTPBIN_BASIC", "本地演示 - Basic", "BASIC",
            Map.of("username", "local-demo-basic-user", "password", "local-demo-basic-password"));
        demoEndpoint(system, "DEMO_HTTPBIN_BEARER", "本地演示 - Bearer", "BEARER",
            Map.of("token", "local-demo-bearer-token"));
        demoEndpoint(system, "DEMO_HTTPBIN_API_KEY", "本地演示 - API Key", "API_KEY",
            Map.of("header_name", "X-Local-Demo-Key", "value", "local-demo-api-key"));
    }

    private void demoEndpoint(SystemEntity system, String code, String name, String authenticationType,
                              Map<String, String> credentialValues) {
        String endpointUrl = "https://1.1.1.1/post";
        var existing = endpoints.findBySystemIdOrderByCode(system.getId()).stream()
            .filter(endpoint -> code.equals(endpoint.getCode())).toList();
        if (existing.size() > 1) throw new IllegalStateException("Bootstrap endpoint " + code + " is not unique");
        if (existing.size() == 1) {
            var endpoint = existing.get(0);
            if (!name.equals(endpoint.getName()) || !endpointUrl.equals(endpoint.getEndpointUrl())
                || !authenticationType.equals(endpoint.getAuthenticationType())
                || !"active".equals(endpoint.getStatus())
                || endpoint.hasCredentials() != !credentialValues.isEmpty()) {
                throw new IllegalStateException("Bootstrap endpoint " + code + " conflicts with required semantics");
            }
            return;
        }
        try {
            endpointUrls.validate(endpointUrl);
            endpoints.saveAndFlush(com.simplemdm.model.integration.PushEndpoint.create(system.getId(), code, name,
                endpointUrl, authenticationType, credentials.encrypt(authenticationType, credentialValues)));
        } catch (EndpointUrlPolicy.RejectedEndpointException | EndpointUrlPolicy.ResolutionTimeoutException
                 | CredentialEncryptionService.CredentialUnavailableException exception) {
            throw new IllegalStateException("Bootstrap endpoint " + code + " is not valid", exception);
        }
    }

    private Department department(SystemEntity system, Department parent, String code, String name) {
        Department department = departments.findBySystemIdAndCode(system.getId(), code).orElseGet(() -> {
            Department created = departments.saveAndFlush(Department.create(system, parent, code, name));
            String path = parent == null ? "/" + created.getId() + "/" : parent.getPath() + created.getId() + "/";
            created.relocate(parent, path, parent == null ? 1 : parent.getLevel() + 1);
            return departments.saveAndFlush(created);
        });
        Long actualParent = department.getParent() == null ? null : department.getParent().getId();
        Long expectedParent = parent == null ? null : parent.getId();
        if (!Objects.equals(actualParent, expectedParent)) {
            throw new IllegalStateException("Bootstrap department " + code + " parent conflicts with expected topology");
        }
        if (!department.getSystem().getId().equals(system.getId())) {
            throw new IllegalStateException("Bootstrap department " + code + " belongs to another system");
        }
        String expectedPath = parent == null ? "/" + department.getId() + "/"
            : parent.getPath() + department.getId() + "/";
        int expectedLevel = parent == null ? 1 : parent.getLevel() + 1;
        if (count("select count(d) from Department d where d.id=:id and d.path=:path and d.level=:level and d.sortOrder=0",
            "id", department.getId(), "path", expectedPath, "level", expectedLevel) != 1) {
            throw new IllegalStateException(
                "Bootstrap department " + code + " path/level/sort conflicts with expected topology");
        }
        requireActive("department " + code,
            "select count(d) from Department d where d.id=:id and d.status='active'", department.getId());
        return department;
    }

    private User admin(SystemEntity system, Department root) {
        User admin = users.findBySystemIdAndUsername(system.getId(), "admin").orElseGet(() -> {
            User created = User.create(system, root, "admin", passwordEncoder.encode("123456"), "Administrator");
            created.makeSystemAdmin();
            return users.saveAndFlush(created);
        });
        if (!admin.isActive() || !admin.isSystemAdmin() || !admin.getSystemId().equals(system.getId())
            || !admin.getDepartmentId().equals(root.getId())) {
            throw new IllegalStateException(
                "Bootstrap admin conflicts with required active ROOT system-admin identity");
        }
        return admin;
    }

    private Map<String, Permission> seedPermissions() {
        Map<String, Permission> result = new LinkedHashMap<>();
        for (String code : List.of("MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "MDM_FIELD_MANAGE", "APPROVAL_REVIEW",
            "MDM_RECORD_CROSS_VIEW", "INTEGRATION_MANUAL_PUSH")) {
            Permission permission = findOne("select p from Permission p where p.code=:code",
                List.of("code", code), Permission.class);
            if (permission == null) {
                permission = Permission.create(code, code);
                entityManager.persist(permission);
                entityManager.flush();
            }
            if (!"active".equals(permission.getStatus())) {
                throw new IllegalStateException("Bootstrap permission " + code + " must be active");
            }
            result.put(code, permission);
        }
        return result;
    }

    private void seedAdministratorRbac(SystemEntity system, Department root, User admin,
                                       Map<String, Permission> permissions) {
        Role role = role(system, "SYSTEM_ADMIN", "System Administrator");
        for (String code : SYSTEM_ADMIN_PERMISSIONS) grant(role, permissions.get(code));
        if (count("select count(ur) from UserRole ur where ur.userId=:user and ur.roleId=:role and ur.systemId=:system",
            "user", admin.getId(), "role", role.getId(), "system", system.getId()) == 0) {
            entityManager.persist(UserRole.assign(system, admin, role));
        }
        long scopeCount = count(
            "select count(s) from UserDepartmentScope s where s.userId=:user and s.departmentId=:department and s.scopeMode=:mode",
            "user", admin.getId(), "department", root.getId(), "mode", UserDepartmentScope.ScopeMode.SUBTREE);
        if (scopeCount == 0) {
            entityManager.persist(UserDepartmentScope.grant(
                system, admin, root, UserDepartmentScope.ScopeMode.SUBTREE, true, true));
        } else if (count("select count(s) from UserDepartmentScope s where s.userId=:user"
                + " and s.departmentId=:department and s.scopeMode=:mode and s.systemId=:system"
                + " and s.canView=true and s.canEdit=true",
            "user", admin.getId(), "department", root.getId(), "mode", UserDepartmentScope.ScopeMode.SUBTREE,
            "system", system.getId()) != 1) {
            throw new IllegalStateException(
                "Bootstrap admin scope conflicts with required view/edit subtree semantics");
        }
    }

    private Role exactRole(SystemEntity system, String code, String name, Map<String, Permission> permissions,
                           Set<String> expectedPermissions) {
        Role role = role(system, code, name);
        Set<String> actual = new LinkedHashSet<>(entityManager.createQuery("""
                select p.code from RolePermission rp, Permission p
                where rp.roleId=:role and rp.permissionId=p.id
                """, String.class)
            .setParameter("role", role.getId()).getResultList());
        Set<String> unexpected = new LinkedHashSet<>(actual);
        unexpected.removeAll(expectedPermissions);
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException(
                "Bootstrap role " + code + " has conflicting permissions: " + unexpected);
        }
        for (String permissionCode : expectedPermissions) grant(role, permissions.get(permissionCode));
        return role;
    }

    private Role role(SystemEntity system, String code, String name) {
        Role role = findOne("select r from Role r where r.system.id=:system and r.code=:code",
            List.of("system", system.getId(), "code", code), Role.class);
        if (role == null) {
            role = Role.create(system, code, name);
            entityManager.persist(role);
            entityManager.flush();
        }
        if (!"active".equals(role.getStatus()) || !role.getSystem().getId().equals(system.getId())) {
            throw new IllegalStateException("Bootstrap role " + code + " conflicts with required active system role");
        }
        return role;
    }

    private void grant(Role role, Permission permission) {
        if (permission == null) throw new IllegalStateException("Bootstrap permission definition is missing");
        if (count("select count(rp) from RolePermission rp where rp.roleId=:role and rp.permissionId=:permission",
            "role", role.getId(), "permission", permission.getId()) == 0) {
            entityManager.persist(RolePermission.grant(role, permission));
            entityManager.flush();
        }
    }

    private User businessUser(SystemEntity system, Department department, String username, String realName,
                              Role expectedRole, boolean canEdit) {
        User user = users.findBySystemIdAndUsername(system.getId(), username).orElseGet(() ->
            users.saveAndFlush(User.create(
                system, department, username, passwordEncoder.encode("123456"), realName)));
        if (!user.isActive() || user.isSystemAdmin() || !user.getSystemId().equals(system.getId())) {
            throw new IllegalStateException("Bootstrap user " + username + " conflicts with required active business identity");
        }
        if (!user.getDepartmentId().equals(department.getId())) {
            throw new IllegalStateException("Bootstrap user " + username + " department conflicts with required assignment");
        }
        exactUserRole(system, user, expectedRole);
        exactSelfScope(system, user, department, canEdit);
        return user;
    }

    private void exactUserRole(SystemEntity system, User user, Role expectedRole) {
        List<Long> roleIds = entityManager.createQuery(
                "select ur.roleId from UserRole ur where ur.userId=:user and ur.systemId=:system", Long.class)
            .setParameter("user", user.getId()).setParameter("system", system.getId()).getResultList();
        if (roleIds.isEmpty()) {
            entityManager.persist(UserRole.assign(system, user, expectedRole));
            entityManager.flush();
        } else if (roleIds.size() != 1 || !roleIds.get(0).equals(expectedRole.getId())) {
            throw new IllegalStateException(
                "Bootstrap user " + user.getUsername() + " role conflicts with required exact role");
        }
    }

    private void exactSelfScope(SystemEntity system, User user, Department department, boolean canEdit) {
        List<UserDepartmentScope> scopes = entityManager.createQuery(
                "select s from UserDepartmentScope s where s.userId=:user", UserDepartmentScope.class)
            .setParameter("user", user.getId()).getResultList();
        if (scopes.isEmpty()) {
            entityManager.persist(UserDepartmentScope.grant(
                system, user, department, UserDepartmentScope.ScopeMode.SELF, true, canEdit));
            entityManager.flush();
            return;
        }
        UserDepartmentScope scope = scopes.get(0);
        if (scopes.size() != 1 || scope.getScopeMode() != UserDepartmentScope.ScopeMode.SELF
            || !scope.getDepartmentId().equals(department.getId()) || !scope.canView()
            || scope.canEdit() != canEdit
            || count("select count(s) from UserDepartmentScope s where s.id=:id and s.systemId=:system",
                "id", scopeId(scope), "system", system.getId()) != 1) {
            throw new IllegalStateException(
                "Bootstrap user " + user.getUsername() + " scope conflicts with required exact SELF scope");
        }
    }

    private Long scopeId(UserDepartmentScope scope) {
        return entityManager.createQuery(
                "select s.id from UserDepartmentScope s where s=:scope", Long.class)
            .setParameter("scope", scope).getSingleResult();
    }

    private ObjectType personObject(SystemEntity system) {
        ObjectType person = objectTypes.findBySystemIdAndCode(system.getId(), "person").orElseGet(() -> {
            ObjectType created = objectTypes.saveAndFlush(ObjectType.create(system, "person", "个人信息"));
            entityManager.createNativeQuery("update mdm_object_type set approval_required=true where id=:id")
                .setParameter("id", created.getId()).executeUpdate();
            entityManager.flush();
            entityManager.refresh(created);
            return created;
        });
        if (count("select count(o) from ObjectType o where o.id=:id and o.systemId=:system"
                + " and o.code='person' and o.name='个人信息' and o.status='active'"
                + " and o.departmentScoped=true and o.approvalRequired=true",
            "id", person.getId(), "system", system.getId()) == 1) {
            return person;
        }
        return upgradeKnownLegacyPerson(system, person);
    }

    private ObjectType upgradeKnownLegacyPerson(SystemEntity system, ObjectType person) {
        boolean exactLegacyObject = count(
            "select count(o) from ObjectType o where o.id=:id and o.systemId=:system"
                + " and o.code='person' and o.name='Person' and o.status='active'"
                + " and o.departmentScoped=true and o.approvalRequired=false",
            "id", person.getId(), "system", system.getId()) == 1;
        List<FieldDefinition> legacyFields = entityManager.createQuery(
                "select f from FieldDefinition f where f.objectTypeId=:object", FieldDefinition.class)
            .setParameter("object", person.getId()).getResultList();
        Map<String, FieldDefinition> byKey = legacyFields.stream().collect(java.util.stream.Collectors.toMap(
            FieldDefinition::getFieldKey, field -> field));
        boolean exactLegacyFields = legacyFields.size() == 3
            && legacyFieldMatches(byKey.get("employee_code"), "Employee Code", true, true, 64, 1)
            && legacyFieldMatches(byKey.get("employee_name"), "Employee Name", true, false, 128, 2)
            && legacyFieldMatches(byKey.get("work_email"), "Work Email", false, true, 255, 3);
        List<ChildType> legacyChildTypes = entityManager.createQuery(
                "select c from ChildType c where c.objectTypeId=:object", ChildType.class)
            .setParameter("object", person.getId()).getResultList();
        ChildType employment = legacyChildTypes.size() == 1 ? legacyChildTypes.get(0) : null;
        boolean exactLegacyChild = employment != null && count(
            "select count(c) from ChildType c where c.id=:id and c.systemId=:system"
                + " and c.objectTypeId=:object and c.code='employment' and c.name='Employment'"
                + " and c.description is null and c.sortOrder=0 and c.status='active'",
            "id", employment.getId(), "system", system.getId(), "object", person.getId()) == 1
            && count("select count(f) from ChildFieldDefinition f where f.childTypeId=:child",
                "child", employment.getId()) == 0;
        if (!exactLegacyObject || !exactLegacyFields || !exactLegacyChild) {
            throw new IllegalStateException(
                "Bootstrap object person does not match the current contract or exact known legacy V1 signature");
        }
        long childRecordCount = ((Number) entityManager.createNativeQuery(
                "select count(*) from mdm_child_record where child_type_id=:child")
            .setParameter("child", employment.getId()).getSingleResult()).longValue();
        long pendingChildChangeCount = ((Number) entityManager.createNativeQuery(
                "select count(*) from wf_approval_child_change where child_type_id=:child")
            .setParameter("child", employment.getId()).getSingleResult()).longValue();
        if (childRecordCount != 0 || pendingChildChangeCount != 0) {
            throw new IllegalStateException(
                "Bootstrap legacy employment data cannot be migrated safely; remove or migrate it explicitly");
        }
        rejectLegacyDemoIdentityCollisions(person, byKey.get("employee_code"));

        entityManager.flush();
        entityManager.createNativeQuery("""
                update mdm_object_type
                set name='个人信息', approval_required=true, updated_at=CURRENT_TIMESTAMP, version=version+1
                where id=:id
                """).setParameter("id", person.getId()).executeUpdate();
        upgradeLegacyField(person.getId(), "employee_code", "员工编号", true, 1);
        upgradeLegacyField(person.getId(), "employee_name", "姓名", false, 2);
        upgradeLegacyField(person.getId(), "work_email", "工作邮箱", false, 6);
        entityManager.createNativeQuery("""
                update mdm_child_type
                set code='part_time', name='兼职信息', updated_at=CURRENT_TIMESTAMP, version=version+1
                where id=:id
                """).setParameter("id", employment.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return objectTypes.findBySystemIdAndCode(system.getId(), "person").orElseThrow();
    }

    private void rejectLegacyDemoIdentityCollisions(ObjectType person, FieldDefinition employeeCodeField) {
        for (Map.Entry<String, String> identity : Map.of(
                "HR-PERSON-001", "HR001", "SALES-PERSON-001", "SALES001").entrySet()) {
            String recordCode = identity.getKey();
            String employeeCode = identity.getValue();
            long effectiveRecordCode = nativeCount("""
                select count(*) from mdm_record
                where object_type_id=:object and record_code=:recordCode and deleted_at is null
                """, "object", person.getId(), "recordCode", recordCode);
            long effectiveEmployeeCode = nativeCount("""
                select count(*) from mdm_record_value
                where field_definition_id=:field and string_value=:employeeCode
                """, "field", employeeCodeField.getId(), "employeeCode", employeeCode);
            long pendingRecordCode = nativeCount("""
                select count(*) from wf_approval_request
                where object_type_id=:object and status='PENDING' and operation='CREATE'
                  and record_code=:recordCode
                """, "object", person.getId(), "recordCode", recordCode);
            long pendingEmployeeCode = nativeCount("""
                select count(*) from wf_approval_change c
                join wf_approval_request r on r.id=c.approval_request_id and r.system_id=c.system_id
                where r.object_type_id=:object and r.status='PENDING'
                  and c.field_definition_id=:field and c.new_string_value=:employeeCode
                """, "object", person.getId(), "field", employeeCodeField.getId(),
                "employeeCode", employeeCode);
            if (effectiveRecordCode != 0 || effectiveEmployeeCode != 0
                || pendingRecordCode != 0 || pendingEmployeeCode != 0) {
                throw new IllegalStateException(
                    "Bootstrap legacy person data conflicts with demo identity "
                        + recordCode + " / " + employeeCode);
            }
        }
    }

    private void rejectCurrentDemoIdentityCollisions(ObjectType person, FieldDefinition employeeCodeField) {
        for (Map.Entry<String, String> identity : Map.of(
                "HR-PERSON-001", "HR001", "SALES-PERSON-001", "SALES001").entrySet()) {
            long conflictingEffective = nativeCount("""
                select count(*) from mdm_record r
                left join mdm_record_value v on v.record_id=r.id and v.field_definition_id=:field
                where r.object_type_id=:object and r.deleted_at is null
                  and ((r.record_code=:recordCode and (v.string_value is null or v.string_value<>:employeeCode))
                    or (v.string_value=:employeeCode and r.record_code<>:recordCode))
                """, "field", employeeCodeField.getId(), "object", person.getId(),
                "recordCode", identity.getKey(), "employeeCode", identity.getValue());
            long pending = nativeCount("""
                select count(*) from wf_approval_request r
                left join wf_approval_change c on c.approval_request_id=r.id and c.system_id=r.system_id
                  and c.field_definition_id=:field
                where r.object_type_id=:object and r.status='PENDING' and r.operation='CREATE'
                  and (r.record_code=:recordCode or c.new_string_value=:employeeCode)
                """, "field", employeeCodeField.getId(), "object", person.getId(),
                "recordCode", identity.getKey(), "employeeCode", identity.getValue());
            if (conflictingEffective != 0 || pending != 0) {
                throw new IllegalStateException("Bootstrap person data conflicts with demo identity "
                    + identity.getKey() + " / " + identity.getValue());
            }
        }
    }

    private long nativeCount(String sql, Object... parameters) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < parameters.length; i += 2) {
            query.setParameter((String) parameters[i], parameters[i + 1]);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private boolean legacyFieldMatches(FieldDefinition field, String name, boolean required, boolean unique,
                                       Integer maxLength, int sortOrder) {
        return field != null && field.getFieldName().equals(name) && field.getDataType() == FieldDataType.STRING
            && field.isRequired() == required && field.isUniqueValue() == unique && field.isSearchable()
            && !field.isShared() && Objects.equals(field.getMaxLength(), maxLength)
            && field.getPrecision() == null && field.getScale() == null
            && field.getReferenceObjectTypeId() == null && field.getDefaultValue() == null
            && field.getValidationRule() == null && field.getSortOrder() == sortOrder
            && "active".equals(field.getStatus());
    }

    private void upgradeLegacyField(Long objectTypeId, String key, String name, boolean unique, int sortOrder) {
        entityManager.createNativeQuery("""
                update mdm_field_definition
                set field_name=:name, unique_value=:unique, sort_order=:sort,
                    updated_at=CURRENT_TIMESTAMP, version=version+1
                where object_type_id=:object and field_key=:key
                """)
            .setParameter("name", name).setParameter("unique", unique).setParameter("sort", sortOrder)
            .setParameter("object", objectTypeId).setParameter("key", key).executeUpdate();
    }

    private Map<String, FieldDefinition> seedPersonFields(ObjectType person) {
        Map<String, FieldDefinition> result = new LinkedHashMap<>();
        List<FieldSpec> specs = List.of(
            new FieldSpec("employee_code", "员工编号", FieldDataType.STRING, true, true, true, false, 64, null, null, 1),
            new FieldSpec("employee_name", "姓名", FieldDataType.STRING, true, false, true, false, 128, null, null, 2),
            new FieldSpec("gender", "性别", FieldDataType.STRING, false, false, true, false, 16, null, null, 3),
            new FieldSpec("birth_date", "出生日期", FieldDataType.DATE, false, false, true, false, null, null, null, 4),
            new FieldSpec("mobile_phone", "手机号", FieldDataType.STRING, false, false, true, false, 64, null, null, 5),
            new FieldSpec("work_email", "工作邮箱", FieldDataType.STRING, false, false, true, false, 255, null, null, 6),
            new FieldSpec("hire_date", "入职日期", FieldDataType.DATE, false, false, true, false, null, null, null, 7),
            new FieldSpec("employment_status", "在职状态", FieldDataType.STRING, false, false, true, false, 32, null, null, 8));
        for (FieldSpec spec : specs) result.put(spec.key(), field(person, spec));
        return result;
    }

    private FieldDefinition field(ObjectType objectType, FieldSpec spec) {
        FieldDefinition existing = findOne(
            "select f from FieldDefinition f where f.objectTypeId=:object and f.fieldKey=:key",
            List.of("object", objectType.getId(), "key", spec.key()), FieldDefinition.class);
        if (existing == null) {
            CreateFieldCommand command = new CreateFieldCommand(spec.key(), spec.name(), spec.type(), spec.required(),
                spec.unique(), spec.searchable(), spec.shared(), spec.maxLength(), spec.precision(), spec.scale(),
                null, null, null, spec.sortOrder());
            FieldDefinition created = FieldDefinition.create(objectType.getId(), objectType, command, null);
            entityManager.persist(created);
            entityManager.flush();
            return created;
        }
        if (!existing.getSystemId().equals(objectType.getSystemId())
            || !existing.getObjectTypeId().equals(objectType.getId())
            || !existing.getFieldKey().equals(spec.key()) || !existing.getFieldName().equals(spec.name())
            || existing.getDataType() != spec.type() || existing.isRequired() != spec.required()
            || existing.isUniqueValue() != spec.unique() || existing.isSearchable() != spec.searchable()
            || existing.isShared() != spec.shared() || !Objects.equals(existing.getMaxLength(), spec.maxLength())
            || !Objects.equals(existing.getPrecision(), spec.precision())
            || !Objects.equals(existing.getScale(), spec.scale()) || existing.getReferenceObjectTypeId() != null
            || existing.getDefaultValue() != null || existing.getValidationRule() != null
            || existing.getSortOrder() != spec.sortOrder() || !"active".equals(existing.getStatus())) {
            throw new IllegalStateException(
                "Bootstrap field " + spec.key() + " conflicts with required semantics");
        }
        return existing;
    }

    private ChildType childType(ObjectType objectType, String code, String name) {
        ChildType childType = findOne(
            "select c from ChildType c where c.objectTypeId=:object and c.code=:code",
            List.of("object", objectType.getId(), "code", code), ChildType.class);
        if (childType == null) {
            childType = ChildType.create(objectType.getId(), objectType, code, name);
            entityManager.persist(childType);
            entityManager.flush();
        }
        if (!childType.getSystemId().equals(objectType.getSystemId())
            || !childType.getObjectTypeId().equals(objectType.getId())
            || count("select count(c) from ChildType c where c.id=:id and c.code=:code and c.name=:name"
                    + " and c.status='active' and c.sortOrder=0",
                "id", childType.getId(), "code", code, "name", name) != 1) {
            throw new IllegalStateException(
                "Bootstrap child type " + code + " conflicts with required semantics");
        }
        return childType;
    }

    private Map<String, ChildFieldDefinition> seedPartTimeFields(ChildType partTime) {
        Map<String, ChildFieldDefinition> result = new LinkedHashMap<>();
        List<FieldSpec> specs = List.of(
            new FieldSpec("company", "兼职单位", FieldDataType.STRING, true, false, true, true, 255, null, null, 1),
            new FieldSpec("position", "兼职岗位", FieldDataType.STRING, true, false, true, true, 128, null, null, 2),
            new FieldSpec("start_date", "开始日期", FieldDataType.DATE, false, false, true, true, null, null, null, 3),
            new FieldSpec("end_date", "结束日期", FieldDataType.DATE, false, false, true, true, null, null, null, 4),
            new FieldSpec("part_time_type", "兼职类型", FieldDataType.STRING, false, false, true, false, 64, null, null, 5),
            new FieldSpec("monthly_income", "月收入", FieldDataType.DECIMAL, false, false, true, false, null, 18, 2, 6),
            new FieldSpec("notes", "备注", FieldDataType.TEXT, false, false, true, false, null, null, null, 7));
        for (FieldSpec spec : specs) result.put(spec.key(), childField(partTime, spec));
        return result;
    }

    private ChildFieldDefinition childField(ChildType childType, FieldSpec spec) {
        ChildFieldDefinition existing = findOne(
            "select f from ChildFieldDefinition f where f.childTypeId=:child and f.fieldKey=:key",
            List.of("child", childType.getId(), "key", spec.key()), ChildFieldDefinition.class);
        if (existing == null) {
            CreateFieldCommand command = new CreateFieldCommand(spec.key(), spec.name(), spec.type(), spec.required(),
                spec.unique(), spec.searchable(), spec.shared(), spec.maxLength(), spec.precision(), spec.scale(),
                null, null, null, spec.sortOrder());
            ChildFieldDefinition created = ChildFieldDefinition.create(childType.getId(), childType, command, null);
            entityManager.persist(created);
            entityManager.flush();
            return created;
        }
        if (count("select count(f) from ChildFieldDefinition f where f.id=:id and f.systemId=:system"
                + " and f.childTypeId=:child and f.fieldKey=:key and f.fieldName=:name and f.dataType=:type"
                + " and f.required=:required and f.uniqueValue=:unique and f.searchable=:searchable"
                + " and f.shared=:shared"
                + " and " + integerClause("f.maxLength", "maxLength", spec.maxLength())
                + " and " + integerClause("f.precision", "precision", spec.precision())
                + " and " + integerClause("f.scale", "scale", spec.scale())
                + " and f.referenceObjectTypeId is null and f.defaultValue is null and f.validationRule is null"
                + " and f.sortOrder=:sort and f.status='active'",
            childFieldParameters(existing, childType, spec)) != 1) {
            throw new IllegalStateException(
                "Bootstrap child field " + spec.key() + " conflicts with required semantics");
        }
        return existing;
    }

    private Object[] childFieldParameters(ChildFieldDefinition existing, ChildType childType, FieldSpec spec) {
        List<Object> parameters = new java.util.ArrayList<>(List.of(
            "id", existing.getId(), "system", childType.getSystemId(), "child", childType.getId(),
            "key", spec.key(), "name", spec.name(), "type", spec.type(), "required", spec.required(),
            "unique", spec.unique(), "searchable", spec.searchable(), "shared", spec.shared(),
            "sort", spec.sortOrder()));
        if (spec.maxLength() != null) { parameters.add("maxLength"); parameters.add(spec.maxLength()); }
        if (spec.precision() != null) { parameters.add("precision"); parameters.add(spec.precision()); }
        if (spec.scale() != null) { parameters.add("scale"); parameters.add(spec.scale()); }
        return parameters.toArray();
    }

    private String integerClause(String property, String parameter, Integer value) {
        return value == null ? property + " is null" : property + "=:" + parameter;
    }

    private void approverAssignment(SystemEntity system, ObjectType person, Department department, User approver) {
        List<Object[]> matches = entityManager.createNativeQuery("""
                select id,status from sys_approver_assignment
                where system_id=:system and object_type_id=:object and department_id=:department
                  and approver_user_id=:user
                """, Object[].class)
            .setParameter("system", system.getId()).setParameter("object", person.getId())
            .setParameter("department", department.getId()).setParameter("user", approver.getId())
            .getResultList();
        if (matches.isEmpty()) {
            entityManager.createNativeQuery("""
                    insert into sys_approver_assignment
                      (system_id,object_type_id,department_id,approver_user_id,status,created_at,updated_at)
                    values (:system,:object,:department,:user,'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """)
                .setParameter("system", system.getId()).setParameter("object", person.getId())
                .setParameter("department", department.getId()).setParameter("user", approver.getId())
                .executeUpdate();
        } else if (matches.size() != 1 || !"active".equals(matches.get(0)[1])) {
            throw new IllegalStateException(
                "Bootstrap HR person approver assignment conflicts with required active semantics");
        }
    }

    private void demoRecord(SystemEntity system, ObjectType person, Department department, User actor,
                            String recordCode, Map<String, FieldDefinition> masterFields, ChildType childType,
                            Map<String, ChildFieldDefinition> childFields, Map<String, TypedValue> masterValues,
                            Map<String, TypedValue> childValues) {
        List<MdmRecord> matches = entityManager.createQuery("""
                select r from MdmRecord r where r.systemId=:system and r.objectTypeId=:object
                  and r.recordCode=:code and r.deletedAt is null
                """, MdmRecord.class)
            .setParameter("system", system.getId()).setParameter("object", person.getId())
            .setParameter("code", recordCode).getResultList();
        MdmRecord record;
        if (matches.isEmpty()) {
            record = MdmRecord.create(system.getId(), person, person.getId(), department, recordCode, actor.getId());
            entityManager.persist(record);
            entityManager.flush();
            for (Map.Entry<String, TypedValue> entry : masterValues.entrySet()) {
                entityManager.persist(RecordValue.create(
                    record, masterFields.get(entry.getKey()), entry.getValue(), actor.getId()));
            }
            entityManager.flush();
        } else {
            if (matches.size() != 1) {
                throw new IllegalStateException("Bootstrap demo record " + recordCode + " is not unique");
            }
            record = matches.get(0);
            if (!record.getDepartmentId().equals(department.getId()) || !"active".equals(record.getStatus())
                || count("select count(r) from MdmRecord r where r.id=:id and r.approvalStatus='approved'",
                    "id", record.getId()) != 1) {
                throw new IllegalStateException(
                    "Bootstrap demo record " + recordCode + " conflicts with required effective semantics");
            }
            validateRecordValues(record, masterFields, masterValues);
        }
        seedOrValidateChild(record, childType, childFields, childValues, actor);
    }

    private void validateRecordValues(MdmRecord record, Map<String, FieldDefinition> fields,
                                      Map<String, TypedValue> expected) {
        List<RecordValue> actual = entityManager.createQuery(
                "select v from RecordValue v where v.recordId=:record", RecordValue.class)
            .setParameter("record", record.getId()).getResultList();
        Map<Long, RecordValue> byField = actual.stream().collect(java.util.stream.Collectors.toMap(
            RecordValue::getFieldDefinitionId, value -> value));
        boolean valid = actual.size() >= expected.size();
        for (Map.Entry<String, TypedValue> entry : expected.entrySet()) {
            RecordValue value = byField.get(fields.get(entry.getKey()).getId());
            valid &= value != null && value.typedValue().sameValueAs(entry.getValue());
        }
        if (!valid) {
            throw new IllegalStateException(
                "Bootstrap demo record " + record.getRecordCode() + " values conflict with required seed data");
        }
    }

    private void seedOrValidateChild(MdmRecord record, ChildType childType,
                                     Map<String, ChildFieldDefinition> fields, Map<String, TypedValue> expected,
                                     User actor) {
        List<ChildRecord> children = entityManager.createQuery("""
                select c from ChildRecord c where c.systemId=:system and c.recordId=:record
                  and c.childTypeId=:type and c.deletedAt is null and c.status='active' order by c.id
                """, ChildRecord.class)
            .setParameter("system", record.getSystemId()).setParameter("record", record.getId())
            .setParameter("type", childType.getId()).getResultList();
        // The lowest-id active row with the complete seed value signature is the stable seed row.
        // All other rows are business data and are intentionally left untouched.
        for (ChildRecord child : children) {
            if (childValuesMatch(child, fields, expected)) return;
        }
        ChildRecord child = ChildRecord.create(record, childType, 0, actor.getId());
        entityManager.persist(child);
        entityManager.flush();
        for (Map.Entry<String, TypedValue> entry : expected.entrySet()) {
            entityManager.persist(ChildRecordValue.create(
                child, fields.get(entry.getKey()), entry.getValue(), actor.getId()));
        }
        entityManager.flush();
    }

    private boolean childValuesMatch(ChildRecord child, Map<String, ChildFieldDefinition> fields,
                                     Map<String, TypedValue> expected) {
        List<ChildRecordValue> actual = entityManager.createQuery(
                "select v from ChildRecordValue v where v.childRecordId=:child", ChildRecordValue.class)
            .setParameter("child", child.getId()).getResultList();
        Map<Long, ChildRecordValue> byField = actual.stream().collect(java.util.stream.Collectors.toMap(
            ChildRecordValue::getFieldDefinitionId, value -> value));
        boolean valid = actual.size() >= expected.size();
        for (Map.Entry<String, TypedValue> entry : expected.entrySet()) {
            ChildRecordValue value = byField.get(fields.get(entry.getKey()).getId());
            valid &= value != null && value.typedValue().sameValueAs(entry.getValue());
        }
        return valid;
    }

    private Map<String, TypedValue> hrMasterValues() {
        return orderedValues(
            "employee_code", stringValue("HR001"), "employee_name", stringValue("张晓梅"),
            "gender", stringValue("女"), "birth_date", dateValue("1990-05-12"),
            "mobile_phone", stringValue("13800000001"), "work_email", stringValue("zhang.xiaomei@example.com"),
            "hire_date", dateValue("2020-03-16"), "employment_status", stringValue("在职"));
    }

    private Map<String, TypedValue> salesMasterValues() {
        return orderedValues(
            "employee_code", stringValue("SALES001"), "employee_name", stringValue("王强"),
            "gender", stringValue("男"), "birth_date", dateValue("1988-09-08"),
            "mobile_phone", stringValue("13800000002"), "work_email", stringValue("wang.qiang@example.com"),
            "hire_date", dateValue("2019-07-01"), "employment_status", stringValue("在职"));
    }

    private Map<String, TypedValue> hrPartTimeValues() {
        return orderedValues(
            "company", stringValue("华星咨询有限公司"), "position", stringValue("人力资源顾问"),
            "start_date", dateValue("2024-01-01"), "end_date", dateValue("2024-12-31"),
            "part_time_type", stringValue("顾问"), "monthly_income", decimalValue("5000.00"),
            "notes", textValue("仅限内部查看"));
    }

    private Map<String, TypedValue> salesPartTimeValues() {
        return orderedValues(
            "company", stringValue("远景商贸有限公司"), "position", stringValue("销售顾问"),
            "start_date", dateValue("2024-02-01"), "end_date", dateValue("2024-11-30"),
            "part_time_type", stringValue("项目合作"), "monthly_income", decimalValue("6500.00"),
            "notes", textValue("销售部门内部备注"));
    }

    private Map<String, TypedValue> orderedValues(Object... entries) {
        Map<String, TypedValue> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], (TypedValue) entries[i + 1]);
        }
        return result;
    }

    private TypedValue stringValue(String value) {
        return new TypedValue(value, null, null, null, null, null, null, null);
    }

    private TypedValue textValue(String value) {
        return new TypedValue(null, value, null, null, null, null, null, null);
    }

    private TypedValue dateValue(String value) {
        return new TypedValue(null, null, null, null, null, LocalDate.parse(value), null, null);
    }

    private TypedValue decimalValue(String value) {
        return new TypedValue(null, null, null, new BigDecimal(value), null, null, null, null);
    }

    private void requireActive(String label, String jpql, Long id) {
        if (count(jpql, "id", id) != 1) {
            throw new IllegalStateException("Bootstrap " + label + " must be active");
        }
    }

    private long count(String jpql, Object... parameters) {
        var query = entityManager.createQuery(jpql, Long.class);
        for (int i = 0; i < parameters.length; i += 2) {
            query.setParameter((String) parameters[i], parameters[i + 1]);
        }
        return query.getSingleResult();
    }

    private <T> T findOne(String jpql, List<Object> parameters, Class<T> type) {
        var query = entityManager.createQuery(jpql, type);
        for (int i = 0; i < parameters.size(); i += 2) {
            query.setParameter((String) parameters.get(i), parameters.get(i + 1));
        }
        return query.getResultStream().findFirst().orElse(null);
    }

    private record FieldSpec(String key, String name, FieldDataType type, boolean required, boolean unique,
                             boolean searchable, boolean shared, Integer maxLength, Integer precision,
                             Integer scale, int sortOrder) {
    }
}
