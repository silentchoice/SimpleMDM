package com.simplemdm.config;

import com.simplemdm.model.mdm.*;
import com.simplemdm.model.system.*;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.system.*;
import com.simplemdm.service.mdm.CreateFieldCommand;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final String SYSTEM_CODE = "DEFAULT";
    private final EntityManager entityManager;
    private final SystemRepository systems;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final ObjectTypeRepository objectTypes;
    private final BootstrapCoordinator coordinator;
    private final boolean enabled;
    private final BCryptPasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    public DataInitializer(EntityManager entityManager, SystemRepository systems,
                           DepartmentRepository departments, UserRepository users,
                           ObjectTypeRepository objectTypes, BootstrapCoordinator coordinator,
                           @Value("${app.bootstrap.enabled:false}") boolean enabled) {
        this(entityManager, systems, departments, users, objectTypes, coordinator, enabled,
            new BCryptPasswordEncoder());
    }

    DataInitializer(EntityManager entityManager, SystemRepository systems,
                    DepartmentRepository departments, UserRepository users,
                    ObjectTypeRepository objectTypes, BootstrapCoordinator coordinator, boolean enabled,
                    BCryptPasswordEncoder passwordEncoder) {
        this.entityManager = entityManager;
        this.systems = systems;
        this.departments = departments;
        this.users = users;
        this.objectTypes = objectTypes;
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
        requireActive("system DEFAULT", "select count(s) from SystemEntity s where s.id=:id and s.status='active'", system.getId());
        Department root = department(system, null, "ROOT", "Head Office");
        department(system, root, "HR", "Human Resources");
        department(system, root, "IT", "Information Technology");
        User admin = users.findBySystemIdAndUsername(system.getId(), "admin").orElseGet(() -> {
            User created = User.create(system, root, "admin", passwordEncoder.encode("123456"), "Administrator");
            created.makeSystemAdmin();
            return users.saveAndFlush(created);
        });
        if (!admin.isActive() || !admin.isSystemAdmin() || !admin.getSystemId().equals(system.getId())
            || !admin.getDepartmentId().equals(root.getId()))
            throw new IllegalStateException("Bootstrap admin conflicts with required active ROOT system-admin identity");
        seedRbac(system, root, admin);
        ObjectType person = objectTypes.findBySystemIdAndCode(system.getId(), "person")
            .orElseGet(() -> objectTypes.saveAndFlush(ObjectType.create(system, "person", "Person")));
        if (count("select count(o) from ObjectType o where o.id=:id and o.systemId=:system and o.status='active' and o.departmentScoped=true and o.approvalRequired=false",
            "id",person.getId(),"system",system.getId())!=1)
            throw new IllegalStateException("Bootstrap object person conflicts with required active department-scoped semantics");
        field(person, "employee_code", "Employee Code", FieldDataType.STRING, true, true, 64, 1);
        field(person, "employee_name", "Employee Name", FieldDataType.STRING, true, false, 128, 2);
        field(person, "work_email", "Work Email", FieldDataType.STRING, false, true, 255, 3);
        childType(person, "employment", "Employment");
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
        if (!java.util.Objects.equals(actualParent, expectedParent))
            throw new IllegalStateException("Bootstrap department " + code + " parent conflicts with expected topology");
        if (!department.getSystem().getId().equals(system.getId()))
            throw new IllegalStateException("Bootstrap department " + code + " belongs to another system");
        String expectedPath = parent == null ? "/" + department.getId() + "/" : parent.getPath() + department.getId() + "/";
        int expectedLevel = parent == null ? 1 : parent.getLevel() + 1;
        if (count("select count(d) from Department d where d.id=:id and d.path=:path and d.level=:level and d.sortOrder=0",
            "id", department.getId(), "path", expectedPath, "level", expectedLevel) != 1)
            throw new IllegalStateException("Bootstrap department " + code + " path/level/sort conflicts with expected topology");
        requireActive("department " + code,
            "select count(d) from Department d where d.id=:id and d.status='active'", department.getId());
        return department;
    }

    private void seedRbac(SystemEntity system, Department root, User admin) {
        Role role = findOne("select r from Role r where r.system.id=:system and r.code=:code",
            List.of("system", system.getId(), "code", "SYSTEM_ADMIN"), Role.class);
        if (role == null) { role = Role.create(system, "SYSTEM_ADMIN", "System Administrator"); entityManager.persist(role); entityManager.flush(); }
        if (!"active".equals(role.getStatus())) throw new IllegalStateException("Bootstrap role SYSTEM_ADMIN must be active");
        for (String code : List.of("MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "MDM_FIELD_MANAGE", "APPROVAL_REVIEW")) {
            Permission permission = findOne("select p from Permission p where p.code=:code", List.of("code", code), Permission.class);
            if (permission == null) { permission = Permission.create(code, code); entityManager.persist(permission); entityManager.flush(); }
            if (!"active".equals(permission.getStatus())) throw new IllegalStateException("Bootstrap permission " + code + " must be active");
            long grantCount = count("select count(rp) from RolePermission rp where rp.roleId=:role and rp.permissionId=:permission",
                "role", role.getId(), "permission", permission.getId());
            if (grantCount == 0) entityManager.persist(RolePermission.grant(role, permission));
        }
        long userRoleCount = count("select count(ur) from UserRole ur where ur.userId=:user and ur.roleId=:role and ur.systemId=:system",
            "user", admin.getId(), "role", role.getId(), "system", system.getId());
        if (userRoleCount == 0) entityManager.persist(UserRole.assign(system, admin, role));
        long scopeCount = count("select count(s) from UserDepartmentScope s where s.userId=:user and s.departmentId=:department and s.scopeMode=:mode",
            "user", admin.getId(), "department", root.getId(), "mode", UserDepartmentScope.ScopeMode.SUBTREE);
        if (scopeCount == 0)
            entityManager.persist(UserDepartmentScope.grant(system, admin, root, UserDepartmentScope.ScopeMode.SUBTREE, true, true));
        else if (count("select count(s) from UserDepartmentScope s where s.userId=:user and s.departmentId=:department and s.scopeMode=:mode and s.systemId=:system and s.canView=true and s.canEdit=true",
            "user", admin.getId(), "department", root.getId(), "mode", UserDepartmentScope.ScopeMode.SUBTREE,
            "system", system.getId()) != 1)
            throw new IllegalStateException("Bootstrap admin scope conflicts with required view/edit subtree semantics");
    }

    private void field(ObjectType objectType, String key, String name, FieldDataType type,
                       boolean required, boolean unique, Integer maxLength, int order) {
        FieldDefinition existing = findOne("select f from FieldDefinition f where f.objectTypeId=:object and f.fieldKey=:key",
            List.of("object", objectType.getId(), "key", key), FieldDefinition.class);
        if (existing != null) {
            if (existing.getDataType()!=type || existing.isRequired()!=required || existing.isUniqueValue()!=unique
                || !java.util.Objects.equals(existing.getMaxLength(),maxLength)
                || count("select count(f) from FieldDefinition f where f.id=:id and f.systemId=:system and f.objectTypeId=:object"
                    + " and f.status='active' and f.searchable=true and f.shared=false and f.sortOrder=:sort"
                    + " and f.precision is null and f.scale is null and f.referenceObjectTypeId is null"
                    + " and f.defaultValue is null and f.validationRule is null",
                    "id", existing.getId(), "system", objectType.getSystemId(), "object", objectType.getId(),
                    "sort", order) != 1)
                throw new IllegalStateException("Bootstrap field " + key + " conflicts with required semantics");
            return;
        }
        CreateFieldCommand command = new CreateFieldCommand(key, name, type, required, unique, true,
            false, maxLength, null, null, null, null, null, order);
        entityManager.persist(FieldDefinition.create(objectType.getId(), objectType, command, null));
    }

    private void childType(ObjectType objectType, String code, String name) {
        ChildType existing=findOne("select c from ChildType c where c.objectTypeId=:object and c.code=:code",
            List.of("object",objectType.getId(),"code",code),ChildType.class);
        if (existing==null)
            entityManager.persist(ChildType.create(objectType.getId(), objectType, code, name));
        else if (!existing.getSystemId().equals(objectType.getSystemId()) || !existing.getObjectTypeId().equals(objectType.getId())
            || count("select count(c) from ChildType c where c.id=:id and c.status='active' and c.sortOrder=0",
                "id", existing.getId()) != 1)
            throw new IllegalStateException("Bootstrap child type " + code + " conflicts with required semantics");
    }

    private void requireActive(String label, String jpql, Long id) {
        if (count(jpql,"id",id)!=1) throw new IllegalStateException("Bootstrap " + label + " must be active");
    }

    private long count(String jpql, Object... parameters) {
        var query = entityManager.createQuery(jpql, Long.class);
        for (int i = 0; i < parameters.length; i += 2) query.setParameter((String) parameters[i], parameters[i + 1]);
        return query.getSingleResult();
    }

    private <T> T findOne(String jpql, List<Object> parameters, Class<T> type) {
        var query = entityManager.createQuery(jpql, type);
        for (int i = 0; i < parameters.size(); i += 2) query.setParameter((String) parameters.get(i), parameters.get(i + 1));
        return query.getResultStream().findFirst().orElse(null);
    }
}
