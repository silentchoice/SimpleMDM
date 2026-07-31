package com.simplemdm.repository.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:push-atomic;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=true"
})
class PushLogRepositoryIntegrationTest {
    @Autowired PushLogRepository logs;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void duplicateInsertReturnsZeroAndDoesNotPoisonAnotherSubscription() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");

        assertThat(logs.insertPendingIfAbsent(7L, 901L, 41L, "record:41:version:3", "{}")).isEqualTo(1);
        assertThat(logs.insertPendingIfAbsent(7L, 901L, 41L, "record:41:version:3", "{}")).isZero();
        assertThat(logs.insertPendingIfAbsent(7L, 902L, 41L, "record:41:version:3", "{}")).isEqualTo(1);
        logs.flush();

        Integer count = jdbc.queryForObject(
            "select count(*) from sys_push_log where event_id='record:41:version:3'", Integer.class);
        assertThat(count).isEqualTo(2);
    }
}
