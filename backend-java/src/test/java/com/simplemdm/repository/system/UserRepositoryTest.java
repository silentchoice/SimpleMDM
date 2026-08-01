package com.simplemdm.repository.system;

import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.service.system.DepartmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@EntityScan(basePackageClasses = SystemEntity.class)
@EnableJpaRepositories(basePackageClasses = SystemRepository.class)
@Import(DepartmentService.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private SystemRepository systems;

    @Autowired
    private DepartmentService departments;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private SystemEntity systemA;
    private SystemEntity systemB;
    private Department departmentInA;
    private Department departmentInB;

    @BeforeEach
    void setUp() {
        systemA = systems.saveAndFlush(SystemEntity.create("SYS_A", "System A"));
        systemB = systems.saveAndFlush(SystemEntity.create("SYS_B", "System B"));
        departmentInA = departments.create(systemA.getId(), null, "A", "Department A");
        departmentInB = departments.create(systemB.getId(), null, "B", "Department B");
    }

    @Test
    void userRequiresDepartmentFromSameSystem() {
        User user = User.create(systemA, departmentInB, "alice", "hash", "Alice");

        assertThatThrownBy(() -> users.saveAndFlush(user))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsUsernameWithinItsSystemBoundary() {
        users.saveAndFlush(User.create(systemA, departmentInA, "alice", "hash-a", "Alice A"));
        users.saveAndFlush(User.create(systemB, departmentInB, "alice", "hash-b", "Alice B"));

        assertThat(users.findBySystemIdAndUsername(systemA.getId(), "alice"))
            .hasValueSatisfying(user -> assertThat(user.getDepartment().getId()).isEqualTo(departmentInA.getId()));
        assertThat(users.findBySystemIdAndUsername(systemB.getId(), "alice"))
            .hasValueSatisfying(user -> assertThat(user.getDepartment().getId()).isEqualTo(departmentInB.getId()));
    }

    @Test
    void authenticationLookupInitializesSystemAndDepartmentBeforeSessionCloses() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AuthenticationFixture fixture = transaction.execute(status -> {
            SystemEntity system = systems.saveAndFlush(SystemEntity.create("AUTH_CONTEXT", "Authentication Context"));
            Department department = departments.create(system.getId(), null, "AUTH", "Authentication Department");
            User user = users.saveAndFlush(User.create(system, department, "auth-user", "hash", "Auth User"));
            return new AuthenticationFixture(user.getId(), system.getId(), department.getId());
        });

        try {
            User authenticated = transaction.execute(status -> users.findWithContextById(fixture.userId()).orElseThrow());

            assertThat(authenticated.getSystem().getName()).isEqualTo("Authentication Context");
            assertThat(authenticated.getDepartment().getName()).isEqualTo("Authentication Department");
        } finally {
            transaction.executeWithoutResult(status -> {
                users.deleteById(fixture.userId());
                entityManager.flush();
                entityManager.createQuery("delete from Department d where d.id = :id")
                    .setParameter("id", fixture.departmentId())
                    .executeUpdate();
                systems.deleteById(fixture.systemId());
            });
        }
    }

    private record AuthenticationFixture(Long userId, Long systemId, Long departmentId) { }
}
