package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.system.SystemEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJpaMappingValidationTest {

    @Test
    void validatesMetadataMappingsAndConstructsRepositoriesAgainstV1() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:metadata-mapping;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(SystemEntity.class.getPackageName(), ObjectType.class.getPackageName());
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.H2Dialect");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();

        EntityManagerFactory entityManagerFactory = factory.getObject();
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(ObjectType.class));
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(FieldDefinition.class));
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(ChildType.class));
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .anyMatch(entityType -> entityType.getJavaType().equals(ChildFieldDefinition.class));
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            JpaRepositoryFactory repositories = new JpaRepositoryFactory(entityManager);
            assertThat(repositories.getRepository(ObjectTypeRepository.class)).isNotNull();
            assertThat(repositories.getRepository(FieldDefinitionRepository.class)).isNotNull();
            assertThat(repositories.getRepository(ChildTypeRepository.class)).isNotNull();
            assertThat(repositories.getRepository(ChildFieldDefinitionRepository.class)).isNotNull();
        } finally {
            factory.destroy();
        }
    }
}
