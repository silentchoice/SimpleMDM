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
    private Role editorRole;

    @BeforeEach
    void setUp() {
        systemA = systems.saveAndFlush(SystemEntity.create("SYS_A", "System A"));
        systemB = systems.saveAndFlush(SystemEntity.create("SYS_B", "System B"));
        ownDepartment = departments.create(systemA.getId(), null, "OWN", "Own");
        childDepartment = departments.create(systemA.getId(), ownDepartment.getId(), "CHILD", "Child");
        unrelatedDepartment = departments.create(systemA.getId(), null, "OTHER", "Other");
        departmentInB = departments.create(systemB.getId(), null, "B", "Department B");

        editor = users.saveAndFlush(User.create(systemA, ownDepartment, "editor", "hash", "Editor"));
        editorRole = persist(Role.create(systemA, "EDITOR", "Editor"));
        Permission fieldManage = persist(Permission.create("MDM_FIELD_MANAGE", "Manage fields"));
        persist(RolePermission.grant(editorRole, fieldManage));
        persist(UserRole.assign(systemA, editor, editorRole));
        persist(UserDepartmentScope.grant(systemA, editor, ownDepartment,
            UserDepartmentScope.ScopeMode.SUBTREE, true, true));
    }

    @Test
    void combinesGenericRoleActionWithSelfOrSubtreeScope() {
        assertThat(auth.can(editor.getId(), "MDM_FIELD_MANAGE", ownDepartment.getId())).isTrue();
        assertThat(auth.can(editor.getId(), "MDM_FIELD_MANAGE", childDepartment.getId())).isTrue();
        assertThat(auth.can(editor.getId(), "MDM_FIELD_MANAGE", unrelatedDepartment.getId())).isFalse();
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
    void strictSelfScopeRequiresExplicitRoleAndExactSelfScopeEvenForSystemAdmin() {
        User admin = users.saveAndFlush(User.create(systemA, ownDepartment,
            "approval-admin", "hash", "Approval Admin"));
        admin.makeSystemAdmin();
        users.saveAndFlush(admin);

        assertThat(auth.canInStrictSelfScope(admin.getId(), "APPROVAL_REVIEW", ownDepartment.getId()))
            .isFalse();

        Role role = persist(Role.create(systemA, "APPROVER", "Approver"));
        Permission permission = persist(Permission.create("APPROVAL_REVIEW", "Review approvals"));
        persist(RolePermission.grant(role, permission));
        persist(UserRole.assign(systemA, admin, role));
        persist(UserDepartmentScope.grant(systemA, admin, ownDepartment,
            UserDepartmentScope.ScopeMode.SUBTREE, true, true));
        assertThat(auth.canInStrictSelfScope(admin.getId(), "APPROVAL_REVIEW", ownDepartment.getId()))
            .isFalse();

        persist(UserDepartmentScope.grant(systemA, admin, ownDepartment,
            UserDepartmentScope.ScopeMode.SELF, true, true));
        assertThat(auth.canInStrictSelfScope(admin.getId(), "APPROVAL_REVIEW", ownDepartment.getId()))
            .isTrue();
    }

    @Test
    void deniesActionWhenRoleDoesNotGrantRequestedPermission() {
        assertThat(auth.can(editor.getId(), "MDM_RECORD_DELETE", ownDepartment.getId())).isFalse();
    }

    @Test
    void manualDistributionPermissionUsesPersistedRoleGrantAndDepartmentEditScope() {
        assertThat(auth.can(editor.getId(), "INTEGRATION_MANUAL_PUSH", ownDepartment.getId())).isFalse();

        Permission manualPush = persist(Permission.create(
            "INTEGRATION_MANUAL_PUSH", "Distribute records manually"));
        persist(RolePermission.grant(editorRole, manualPush));

        assertThat(auth.can(editor.getId(), "INTEGRATION_MANUAL_PUSH", ownDepartment.getId())).isTrue();
        assertThat(auth.can(editor.getId(), "INTEGRATION_MANUAL_PUSH", childDepartment.getId())).isTrue();
        assertThat(auth.can(editor.getId(), "INTEGRATION_MANUAL_PUSH", unrelatedDepartment.getId())).isFalse();
        assertThat(auth.can(editor.getId(), "INTEGRATION_MANUAL_PUSH", departmentInB.getId())).isFalse();
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
