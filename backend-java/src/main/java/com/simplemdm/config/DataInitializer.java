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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final String SYSTEM_CODE = "DEFAULT";
    private final EntityManager entityManager;
    private final SystemRepository systems;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final ObjectTypeRepository objectTypes;
    private final boolean enabled;
    private final BCryptPasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired
    public DataInitializer(EntityManager entityManager, SystemRepository systems,
                           DepartmentRepository departments, UserRepository users,
                           ObjectTypeRepository objectTypes,
                           @Value("${app.bootstrap.enabled:false}") boolean enabled) {
        this(entityManager, systems, departments, users, objectTypes, enabled,
            new BCryptPasswordEncoder());
    }

    DataInitializer(EntityManager entityManager, SystemRepository systems,
                    DepartmentRepository departments, UserRepository users,
                    ObjectTypeRepository objectTypes, boolean enabled,
                    BCryptPasswordEncoder passwordEncoder) {
        this.entityManager = entityManager;
        this.systems = systems;
        this.departments = departments;
        this.users = users;
        this.objectTypes = objectTypes;
        this.enabled = enabled;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        SystemEntity system = systems.findByCode(SYSTEM_CODE)
            .orElseGet(() -> systems.saveAndFlush(SystemEntity.create(SYSTEM_CODE, "Default MDM")));
        Department root = department(system, null, "ROOT", "Head Office");
        department(system, root, "HR", "Human Resources");
        department(system, root, "IT", "Information Technology");
        User admin = users.findBySystemIdAndUsername(system.getId(), "admin").orElseGet(() -> {
            User created = User.create(system, root, "admin", passwordEncoder.encode("123456"), "Administrator");
            created.makeSystemAdmin();
            return users.saveAndFlush(created);
        });
        seedRbac(system, root, admin);
        ObjectType person = objectTypes.findBySystemIdAndCode(system.getId(), "person")
            .orElseGet(() -> objectTypes.saveAndFlush(ObjectType.create(system, "person", "Person")));
        field(person, "employee_code", "Employee Code", FieldDataType.STRING, true, true, 64, 1);
        field(person, "employee_name", "Employee Name", FieldDataType.STRING, true, false, 128, 2);
        field(person, "work_email", "Work Email", FieldDataType.STRING, false, true, 255, 3);
        childType(person, "employment", "Employment");
    }

    private Department department(SystemEntity system, Department parent, String code, String name) {
        return departments.findBySystemIdAndCode(system.getId(), code).orElseGet(() -> {
            Department created = departments.saveAndFlush(Department.create(system, parent, code, name));
            String path = parent == null ? "/" + created.getId() + "/" : parent.getPath() + created.getId() + "/";
            created.relocate(parent, path, parent == null ? 1 : parent.getLevel() + 1);
            return departments.saveAndFlush(created);
        });
    }

    private void seedRbac(SystemEntity system, Department root, User admin) {
        Role role = findOne("select r from Role r where r.system.id=:system and r.code=:code",
            List.of("system", system.getId(), "code", "SYSTEM_ADMIN"), Role.class);
        if (role == null) { role = Role.create(system, "SYSTEM_ADMIN", "System Administrator"); entityManager.persist(role); entityManager.flush(); }
        for (String code : List.of("MDM_RECORD_VIEW", "MDM_RECORD_EDIT", "MDM_FIELD_MANAGE", "APPROVAL_REVIEW")) {
            Permission permission = findOne("select p from Permission p where p.code=:code", List.of("code", code), Permission.class);
            if (permission == null) { permission = Permission.create(code, code); entityManager.persist(permission); entityManager.flush(); }
            if (count("select count(rp) from RolePermission rp where rp.roleId=:role and rp.permissionId=:permission",
                "role", role.getId(), "permission", permission.getId()) == 0) entityManager.persist(RolePermission.grant(role, permission));
        }
        if (count("select count(ur) from UserRole ur where ur.userId=:user and ur.roleId=:role",
            "user", admin.getId(), "role", role.getId()) == 0) entityManager.persist(UserRole.assign(system, admin, role));
        if (count("select count(s) from UserDepartmentScope s where s.userId=:user and s.departmentId=:department and s.scopeMode=:mode",
            "user", admin.getId(), "department", root.getId(), "mode", UserDepartmentScope.ScopeMode.SUBTREE) == 0)
            entityManager.persist(UserDepartmentScope.grant(system, admin, root, UserDepartmentScope.ScopeMode.SUBTREE, true, true));
    }

    private void field(ObjectType objectType, String key, String name, FieldDataType type,
                       boolean required, boolean unique, Integer maxLength, int order) {
        if (count("select count(f) from FieldDefinition f where f.objectTypeId=:object and f.fieldKey=:key",
            "object", objectType.getId(), "key", key) != 0) return;
        CreateFieldCommand command = new CreateFieldCommand(key, name, type, required, unique, true,
            false, maxLength, null, null, null, null, null, order);
        entityManager.persist(FieldDefinition.create(objectType.getId(), objectType, command, null));
    }

    private void childType(ObjectType objectType, String code, String name) {
        if (count("select count(c) from ChildType c where c.objectTypeId=:object and c.code=:code",
            "object", objectType.getId(), "code", code) == 0)
            entityManager.persist(ChildType.create(objectType.getId(), objectType, code, name));
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
