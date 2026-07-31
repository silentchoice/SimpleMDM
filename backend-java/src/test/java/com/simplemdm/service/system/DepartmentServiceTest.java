package com.simplemdm.service.system;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.SystemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
class DepartmentServiceTest {

    @Autowired
    private DepartmentService service;

    @Autowired
    private SystemRepository systems;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private EntityManager entityManager;

    private SystemEntity systemA;
    private SystemEntity systemB;

    @BeforeEach
    void setUp() {
        systemA = systems.saveAndFlush(SystemEntity.create("SYS_A", "System A"));
        systemB = systems.saveAndFlush(SystemEntity.create("SYS_B", "System B"));
    }

    @Test
    void createsRootAndNestedDepartmentPaths() {
        Department root = service.create(systemA.getId(), null, "ROOT", "Root");
        Department child = service.create(systemA.getId(), root.getId(), "CHILD", "Child");

        assertThat(root.getLevel()).isEqualTo(1);
        assertThat(root.getPath()).isEqualTo("/" + root.getId() + "/");
        assertThat(child.getLevel()).isEqualTo(2);
        assertThat(child.getPath()).isEqualTo("/" + root.getId() + "/" + child.getId() + "/");
    }

    @Test
    void rejectsDepartmentCycleAndCrossSystemParent() {
        Department root = service.create(systemA.getId(), null, "ROOT", "Root");
        Department child = service.create(systemA.getId(), root.getId(), "CHILD", "Child");
        Department departmentInB = service.create(systemB.getId(), null, "OTHER", "Other");

        assertThatThrownBy(() -> service.move(root.getId(), departmentInB.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("same system");

        assertThatThrownBy(() -> service.move(root.getId(), child.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("cycle");
        assertThatThrownBy(() -> service.create(systemA.getId(), departmentInB.getId(), "OPS", "Operations"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("same system");
    }

    @Test
    void movesDepartmentAndRebuildsEveryDescendantPath() {
        Department root = service.create(systemA.getId(), null, "ROOT", "Root");
        Department child = service.create(systemA.getId(), root.getId(), "CHILD", "Child");
        Department grandchild = service.create(systemA.getId(), child.getId(), "GRANDCHILD", "Grandchild");
        Department newRoot = service.create(systemA.getId(), null, "NEW_ROOT", "New root");

        Department movedChildBeforeReload = service.move(child.getId(), newRoot.getId());
        assertThat(movedChildBeforeReload.getParent().getId()).isEqualTo(newRoot.getId());
        departments.flush();
        entityManager.clear();

        Department movedChild = departments.findById(child.getId()).orElseThrow();
        Department movedGrandchild = departments.findById(grandchild.getId()).orElseThrow();
        assertThat(movedChild.getParent().getId()).isEqualTo(newRoot.getId());
        assertThat(movedChild.getLevel()).isEqualTo(2);
        assertThat(movedChild.getPath()).isEqualTo("/" + newRoot.getId() + "/" + child.getId() + "/");
        assertThat(movedGrandchild.getLevel()).isEqualTo(3);
        assertThat(movedGrandchild.getPath())
            .isEqualTo("/" + newRoot.getId() + "/" + child.getId() + "/" + grandchild.getId() + "/");
    }
}
