package com.simplemdm.migration;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FullJpaSchemaValidationTest {

    @Test
    void everyRuntimeEntityValidatesAgainstTheFlywayV1Schema() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:full-runtime-mapping;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.simplemdm.model");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.H2Dialect");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));

        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject().getMetamodel().getEntities()).isNotEmpty();
        } finally {
            factory.destroy();
        }
    }
}
