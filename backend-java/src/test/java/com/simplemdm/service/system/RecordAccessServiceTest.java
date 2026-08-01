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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@EntityScan(basePackageClasses = SystemEntity.class)
@EnableJpaRepositories(basePackageClasses = SystemRepository.class)
@Import({DepartmentService.class, AuthorizationService.class, RecordAccessService.class})
class RecordAccessServiceTest {

    @Autowired private RecordAccessService access;
    @Autowired private AuthorizationService authorization;
    @Autowired private SystemRepository systems;
    @Autowired private UserRepository users;
    @Autowired private DepartmentService departments;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private SystemEntity systemA;
    private SystemEntity systemB;
    private Department own;
    private Department other;
    private Department systemBDepartment;

    @BeforeEach
    void setUp() {
        systemA = systems.saveAndFlush(SystemEntity.create("ACCESS_A", "Access A"));
        systemB = systems.saveAndFlush(SystemEntity.create("ACCESS_B", "Access B"));
        own = departments.create(systemA.getId(), null, "OWN", "Own");
        other = departments.create(systemA.getId(), null, "OTHER", "Other");
        systemBDepartment = departments.create(systemB.getId(), null, "B", "System B");
    }

    @Test
    void explicitSelfViewIsFullButSubtreeAndOtherDepartmentsAreNotFull() {
        User selfViewer = user("self-viewer", own);
        grant(selfViewer, own, UserDepartmentScope.ScopeMode.SELF, "MDM_RECORD_VIEW");

        User subtreeViewer = user("subtree-viewer", own);
        grant(subtreeViewer, own, UserDepartmentScope.ScopeMode.SUBTREE, "MDM_RECORD_VIEW");

        assertThat(access.access(selfViewer, own.getId())).isEqualTo(RecordAccessService.Decision.FULL);
        assertThat(access.access(selfViewer, other.getId())).isEqualTo(RecordAccessService.Decision.DENY);
        assertThat(access.access(subtreeViewer, own.getId())).isEqualTo(RecordAccessService.Decision.DENY);
    }

    @Test
    void everyExplicitSelfViewGrantIsFullAndReadableEvenOutsidePrimaryDepartment() {
        User multiDepartmentViewer = user("multi-self-viewer", own);
        grant(multiDepartmentViewer, own, UserDepartmentScope.ScopeMode.SELF, "MDM_RECORD_VIEW");
        persist(UserDepartmentScope.grant(systemA, multiDepartmentViewer, other,
            UserDepartmentScope.ScopeMode.SELF, true, false));

        assertThat(access.access(multiDepartmentViewer, other.getId()))
            .isEqualTo(RecordAccessService.Decision.FULL);
        assertThat(access.readableDepartmentIds(multiDepartmentViewer))
            .containsExactlyInAnyOrder(own.getId(), other.getId());
    }

    @Test
    void crossDepartmentPermissionReadsEveryCurrentAndFutureActiveDepartmentAsShared() {
        User crossViewer = user("cross-viewer", own);
        grant(crossViewer, own, UserDepartmentScope.ScopeMode.SELF,
            "MDM_RECORD_VIEW", "MDM_RECORD_CROSS_VIEW", "MDM_RECORD_EDIT", "APPROVAL_REVIEW");

        Department insertedLater = departments.create(systemA.getId(), null, "LATER", "Later");
        Department inactive = departments.create(systemA.getId(), null, "INACTIVE", "Inactive");
        ReflectionTestUtils.setField(inactive, "status", "inactive");
        entityManager.flush();
        entityManager.clear();

        assertThat(access.access(crossViewer, own.getId())).isEqualTo(RecordAccessService.Decision.FULL);
        assertThat(access.access(crossViewer, other.getId())).isEqualTo(RecordAccessService.Decision.SHARED);
        assertThat(access.access(crossViewer, insertedLater.getId())).isEqualTo(RecordAccessService.Decision.SHARED);
        assertThat(access.access(crossViewer, inactive.getId())).isEqualTo(RecordAccessService.Decision.DENY);
        assertThat(access.access(crossViewer, systemBDepartment.getId())).isEqualTo(RecordAccessService.Decision.DENY);
        assertThat(access.readableDepartmentIds(crossViewer))
            .containsExactlyInAnyOrder(own.getId(), other.getId(), insertedLater.getId())
            .doesNotContain(inactive.getId(), systemBDepartment.getId());

        assertThat(authorization.can(crossViewer.getId(), "MDM_RECORD_EDIT", own.getId())).isTrue();
        assertThat(authorization.can(crossViewer.getId(), "APPROVAL_REVIEW", own.getId())).isTrue();
        assertThat(authorization.can(crossViewer.getId(), "MDM_RECORD_EDIT", other.getId())).isFalse();
        assertThat(authorization.can(crossViewer.getId(), "APPROVAL_REVIEW", other.getId())).isFalse();
    }

    @Test
    void currentSystemAdminGetsFullAccessWithoutCrossingTheSystemBoundary() {
        User admin = user("admin", own);
        admin.makeSystemAdmin();
        users.saveAndFlush(admin);

        assertThat(access.access(admin, own.getId())).isEqualTo(RecordAccessService.Decision.FULL);
        assertThat(access.access(admin, other.getId())).isEqualTo(RecordAccessService.Decision.FULL);
        assertThat(access.access(admin, systemBDepartment.getId())).isEqualTo(RecordAccessService.Decision.DENY);
        assertThat(access.readableDepartmentIds(admin)).containsExactlyInAnyOrder(own.getId(), other.getId());
    }

    @Test
    void editAndApprovalNeverInheritSubtreeScope() {
        Department child = departments.create(systemA.getId(), own.getId(), "CHILD", "Child");
        User subtreeActor = user("subtree-actor", own);
        grant(subtreeActor, own, UserDepartmentScope.ScopeMode.SUBTREE,
            "MDM_RECORD_EDIT", "APPROVAL_REVIEW");

        assertThat(authorization.can(subtreeActor.getId(), "MDM_RECORD_EDIT", child.getId())).isFalse();
        assertThat(authorization.can(subtreeActor.getId(), "APPROVAL_REVIEW", child.getId())).isFalse();
    }

    @Test
    void snapshotBulkLoadsAuthorizationOnceRegardlessOfDepartmentDecisionCount() {
        User crossViewer = user("bulk-cross-viewer", own);
        grant(crossViewer, own, UserDepartmentScope.ScopeMode.SELF,
            "MDM_RECORD_VIEW", "MDM_RECORD_CROSS_VIEW");
        Department third = departments.create(systemA.getId(), null, "THIRD", "Third");
        Department fourth = departments.create(systemA.getId(), null, "FOURTH", "Fourth");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        entityManager.flush();
        statistics.clear();

        RecordAccessService.Snapshot snapshot = access.snapshot(crossViewer);

        assertThat(snapshot.decision(own.getId())).isEqualTo(RecordAccessService.Decision.FULL);
        assertThat(snapshot.decision(other.getId())).isEqualTo(RecordAccessService.Decision.SHARED);
        assertThat(snapshot.decision(third.getId())).isEqualTo(RecordAccessService.Decision.SHARED);
        assertThat(snapshot.decision(fourth.getId())).isEqualTo(RecordAccessService.Decision.SHARED);
        assertThat(snapshot.decision(systemBDepartment.getId())).isEqualTo(RecordAccessService.Decision.DENY);
        assertThat(snapshot.readableDepartmentIds())
            .containsExactlyInAnyOrder(own.getId(), other.getId(), third.getId(), fourth.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);

        snapshot.decision(own.getId());
        snapshot.decision(other.getId());
        snapshot.readableDepartmentIds();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void administratorSnapshotUsesOnlyTheSingleActiveDepartmentQuery() {
        User admin = user("bulk-admin", own);
        admin.makeSystemAdmin();
        users.saveAndFlush(admin);
        departments.create(systemA.getId(), null, "ADMIN_OTHER", "Admin Other");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        entityManager.flush();
        statistics.clear();

        RecordAccessService.Snapshot snapshot = access.snapshot(admin);

        assertThat(snapshot.readableDepartmentIds()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private User user(String username, Department department) {
        return users.saveAndFlush(User.create(systemA, department, username, "hash", username));
    }

    private void grant(User user, Department department, UserDepartmentScope.ScopeMode mode,
                       String... permissionCodes) {
        Role role = persist(Role.create(systemA, "ROLE_" + user.getUsername(), user.getUsername()));
        for (String code : permissionCodes) {
            Permission permission = entityManager.createQuery(
                    "select p from Permission p where p.code = :code", Permission.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElseGet(() -> persist(Permission.create(code, code)));
            persist(RolePermission.grant(role, permission));
        }
        persist(UserRole.assign(systemA, user, role));
        persist(UserDepartmentScope.grant(systemA, user, department, mode, true, true));
    }

    private <T> T persist(T value) {
        entityManager.persist(value);
        entityManager.flush();
        return value;
    }
}
