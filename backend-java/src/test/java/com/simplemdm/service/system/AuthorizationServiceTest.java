package com.simplemdm.service.system;

import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.Permission;
import com.simplemdm.model.system.Role;
import com.simplemdm.model.system.RolePermission;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.model.system.UserDepartmentScope;
import com.simplemdm.model.system.UserRole;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.repository.system.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@EntityScan(basePackageClasses = SystemEntity.class)
@EnableJpaRepositories(basePackageClasses = SystemRepository.class)
@Import({DepartmentService.class, AuthorizationService.class})
class AuthorizationServiceTest {

    @Autowired
    private AuthorizationService auth;

    @Autowired
    private SystemRepository systems;

    @Autowired
    private UserRepository users;

    @Autowired
    private DepartmentService departments;

    @Autowired
    private EntityManager entityManager;

    private SystemEntity systemA;
    private SystemEntity systemB;
    private Department ownDepartment;
    private Department childDepartment;
    private Department unrelatedDepartment;
    private Department departmentInB;
    private User editor;

    @BeforeEach
    void setUp() {
        systemA = systems.saveAndFlush(SystemEntity.create("SYS_A", "System A"));
        systemB = systems.saveAndFlush(SystemEntity.create("SYS_B", "System B"));
        ownDepartment = departments.create(systemA.getId(), null, "OWN", "Own");
        childDepartment = departments.create(systemA.getId(), ownDepartment.getId(), "CHILD", "Child");
        unrelatedDepartment = departments.create(systemA.getId(), null, "OTHER", "Other");
        departmentInB = departments.create(systemB.getId(), null, "B", "Department B");

        editor = users.saveAndFlush(User.create(systemA, ownDepartment, "editor", "hash", "Editor"));
        Role editorRole = persist(Role.create(systemA, "EDITOR", "Editor"));
        Permission recordEdit = persist(Permission.create("MDM_RECORD_EDIT", "Edit records"));
        persist(RolePermission.grant(editorRole, recordEdit));
        persist(UserRole.assign(systemA, editor, editorRole));
        persist(UserDepartmentScope.grant(systemA, editor, ownDepartment,
            UserDepartmentScope.ScopeMode.SUBTREE, true, true));
    }

    @Test
    void combinesRoleActionWithSelfOrSubtreeScope() {
        assertThat(auth.can(editor.getId(), "MDM_RECORD_EDIT", ownDepartment.getId())).isTrue();
        assertThat(auth.can(editor.getId(), "MDM_RECORD_EDIT", childDepartment.getId())).isTrue();
        assertThat(auth.can(editor.getId(), "MDM_RECORD_EDIT", unrelatedDepartment.getId())).isFalse();
    }

    @Test
    void systemAdminNeverCrossesSystemBoundary() {
        User adminA = users.saveAndFlush(User.create(systemA, ownDepartment, "admin", "hash", "Admin"));
        adminA.makeSystemAdmin();
        users.saveAndFlush(adminA);

        assertThat(auth.can(adminA.getId(), "MDM_RECORD_EDIT", departmentInB.getId())).isFalse();
    }

    @Test
    void returnsConcreteViewableDepartmentsForScopeSubtrees() {
        assertThat(auth.viewableDepartmentIds(editor.getId()))
            .containsExactlyInAnyOrder(ownDepartment.getId(), childDepartment.getId())
            .doesNotContain(unrelatedDepartment.getId());
    }

    @Test
    void systemAdminCanActWithinTheirOwnSystemWithoutRoleOrScope() {
        User adminA = users.saveAndFlush(User.create(systemA, ownDepartment, "local-admin", "hash", "Local Admin"));
        adminA.makeSystemAdmin();
        users.saveAndFlush(adminA);

        assertThat(auth.can(adminA.getId(), "MDM_RECORD_EDIT", childDepartment.getId())).isTrue();
    }

    @Test
    void deniesActionWhenRoleDoesNotGrantRequestedPermission() {
        assertThat(auth.can(editor.getId(), "MDM_RECORD_DELETE", ownDepartment.getId())).isFalse();
    }

    @Test
    void selfScopeDoesNotIncludeChildDepartment() {
        User selfOnly = users.saveAndFlush(User.create(systemA, ownDepartment, "self-only", "hash", "Self Only"));
        Role role = persist(Role.create(systemA, "SELF_VIEWER", "Self viewer"));
        Permission view = persist(Permission.create("MDM_RECORD_VIEW", "View records"));
        persist(RolePermission.grant(role, view));
        persist(UserRole.assign(systemA, selfOnly, role));
        persist(UserDepartmentScope.grant(systemA, selfOnly, ownDepartment,
            UserDepartmentScope.ScopeMode.SELF, true, false));

        assertThat(auth.can(selfOnly.getId(), "MDM_RECORD_VIEW", ownDepartment.getId())).isTrue();
        assertThat(auth.can(selfOnly.getId(), "MDM_RECORD_VIEW", childDepartment.getId())).isFalse();
    }

    @Test
    void rejectsDuplicateUserDepartmentScopeMode() {
        assertThatThrownBy(() -> persist(UserDepartmentScope.grant(systemA, editor, ownDepartment,
            UserDepartmentScope.ScopeMode.SUBTREE, true, true)))
            .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }
    private <T> T persist(T entity) {

        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }
}
