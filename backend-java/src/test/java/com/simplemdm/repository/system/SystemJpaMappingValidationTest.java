package com.simplemdm.repository.system;

import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemJpaMappingValidationTest {

    @Test
    void validatesSystemMappingsAndConstructsRepositoriesAgainstV1() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:system-mapping;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(SystemEntity.class.getPackageName());
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.H2Dialect");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();

        EntityManagerFactory entityManagerFactory = factory.getObject();
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(SystemEntity.class));
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(Department.class));
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(User.class));
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            JpaRepositoryFactory repositories = new JpaRepositoryFactory(entityManager);

            assertThat(repositories.getRepository(SystemRepository.class)).isNotNull();
            assertThat(repositories.getRepository(DepartmentRepository.class)).isNotNull();
            assertThat(repositories.getRepository(UserRepository.class)).isNotNull();
        } finally {
            factory.destroy();
        }
    }
}
