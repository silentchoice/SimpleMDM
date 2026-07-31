package com.simplemdm.repository.system;

import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.service.system.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.DataIntegrityViolationException;

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
}
