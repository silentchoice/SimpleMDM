package com.simplemdm.repository.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

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
@Import(PushLogOutboxWriter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushLogRepositoryIntegrationTest {
    @Autowired PushLogRepository logs;
    @Autowired PushLogOutboxWriter outbox;
    @Autowired JdbcTemplate jdbc;

    @Test
    void duplicateConstraintAloneIsIdempotentAndForeignKeyFailureIsNotSwallowed() {
        insertFixture();

        assertThat(outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{\"complete\":true}", "7:801:31:41:3")).isEqualTo(1);
        assertThat(outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{\"complete\":false}", "7:801:31:41:3")).isZero();
        assertThatThrownBy(() -> outbox.insertAutomatic(7L, 999L, 41L,
            "record:41:version:4", "{}", "7:801:31:41:4"))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("select count(*) from sys_push_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select request_snapshot from sys_push_log", String.class))
            .isEqualTo("{\"complete\":true}");
    }

    @Test
    @Transactional
    void manualRetryAtomicallyRequeuesSameRowAndPreservesLogicalEvent() {
        insertFixture();
        outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{\"complete\":true}", "7:801:31:41:3");
        Long logId = jdbc.queryForObject("select id from sys_push_log", Long.class);
        jdbc.update("update sys_push_log set status='FAILED',retry_count=3,"
            + "response_snapshot='timeout',last_attempt_at=current_timestamp where id=?", logId);

        assertThat(logs.requeueFailed(logId, 7L, 71L, "operator retry",
            LocalDateTime.of(2026, 8, 1, 8, 0))).isEqualTo(1);
        assertThat(logs.requeueFailed(logId, 7L, 71L, "second race",
            LocalDateTime.of(2026, 8, 1, 8, 1))).isZero();

        assertThat(jdbc.queryForObject("select count(*) from sys_push_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select event_id,request_snapshot,trigger_type,status,retry_count,"
            + "response_snapshot,last_attempt_at,last_retry_by,last_retry_reason from sys_push_log where id=?",
            logId)).containsEntry("EVENT_ID", "record:41:version:3")
            .containsEntry("REQUEST_SNAPSHOT", "{\"complete\":true}")
            .containsEntry("TRIGGER_TYPE", "AUTOMATIC")
            .containsEntry("STATUS", "PENDING")
            .containsEntry("RETRY_COUNT", 0)
            .containsEntry("LAST_RETRY_BY", 71L)
            .containsEntry("LAST_RETRY_REASON", "operator retry")
            .containsEntry("RESPONSE_SNAPSHOT", null)
            .containsEntry("LAST_ATTEMPT_AT", null);
    }

    @Test
    @Transactional
    void cancellationIsAtomicAuditedAndReleasesOnlyTheCancelledDeduplicationSlot() {
        insertFixture();
        String dedupKey = "7:801:31:41:3";
        assertThat(outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{\"first\":true}", dedupKey)).isEqualTo(1);
        Long cancelledId = jdbc.queryForObject("select id from sys_push_log", Long.class);

        assertThat(logs.cancelPending(cancelledId, 7L, 71L, "计划已变更",
            LocalDateTime.of(2026, 8, 1, 9, 30))).isEqualTo(1);
        assertThat(logs.cancelPending(cancelledId, 7L, 71L, "重复取消",
            LocalDateTime.of(2026, 8, 1, 9, 31))).isZero();
        assertThat(outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{\"second\":true}", dedupKey)).isEqualTo(1);

        assertThat(jdbc.queryForMap("select status,cancelled_by,cancellation_reason,active_dedup_key "
            + "from sys_push_log where id=?", cancelledId))
            .containsEntry("STATUS", "CANCELLED")
            .containsEntry("CANCELLED_BY", 71L)
            .containsEntry("CANCELLATION_REASON", "计划已变更")
            .containsEntry("ACTIVE_DEDUP_KEY", null);
        assertThat(jdbc.queryForObject("select count(*) from sys_push_log", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct idempotency_key) from sys_push_log", Integer.class))
            .isEqualTo(2);
    }

    @Test
    @Transactional
    void runningTaskCannotBeCancelledAndKeepsItsDeduplicationSlot() {
        insertFixture();
        String dedupKey = "7:801:31:41:3";
        outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{}", dedupKey);
        Long logId = jdbc.queryForObject("select id from sys_push_log", Long.class);
        jdbc.update("update sys_push_log set status='RUNNING' where id=?", logId);

        assertThat(logs.cancelPending(logId, 7L, 71L, "too late", LocalDateTime.now())).isZero();
        assertThat(outbox.insertAutomatic(7L, 901L, 41L,
            "record:41:version:3", "{}", dedupKey)).isZero();
        assertThat(jdbc.queryForObject("select active_dedup_key from sys_push_log where id=?",
            String.class, logId)).isEqualTo(dedupKey);
    }

    private void insertFixture() {
        jdbc.update("delete from sys_push_log");
        jdbc.update("delete from sys_push_subscription");
        jdbc.update("delete from sys_push_endpoint");
        jdbc.update("delete from mdm_record");
        jdbc.update("delete from mdm_object_type");
        jdbc.update("delete from sys_user");
        jdbc.update("delete from sys_department");
        jdbc.update("delete from sys_system");
        jdbc.update("insert into sys_system(id,code,name,status,created_at,updated_at,version) "
            + "values (7,'SYS','System','active',current_timestamp,current_timestamp,0)");
        jdbc.update("insert into sys_department(id,system_id,code,name,path,level,sort_order,status,"
            + "created_at,updated_at,version) values (21,7,'D','Department','/21/',1,0,'active',"
            + "current_timestamp,current_timestamp,0)");
        jdbc.update("insert into sys_user(id,system_id,department_id,username,password_hash,real_name,status,"
            + "is_system_admin,failed_login_count,created_at,updated_at,version) values "
            + "(71,7,21,'retry-actor','hash','Retry Actor','active',false,0,current_timestamp,current_timestamp,0)");
        jdbc.update("insert into mdm_object_type(id,system_id,code,name,department_scoped,approval_required,"
            + "status,created_at,updated_at,version) values (31,7,'PERSON','Person',true,true,'active',"
            + "current_timestamp,current_timestamp,0)");
        jdbc.update("insert into mdm_record(id,system_id,object_type_id,department_id,record_code,"
            + "approval_status,status,created_at,updated_at,version) values "
            + "(41,7,31,21,'EMP-41','APPROVED','active',current_timestamp,current_timestamp,3)");
        jdbc.update("insert into sys_push_endpoint(id,system_id,code,name,endpoint_url,authentication_type,"
            + "status,created_at,updated_at,version) values (801,7,'ERP','ERP','https://example.com/push',"
            + "'NONE','active',current_timestamp,current_timestamp,0)");
        jdbc.update("insert into sys_push_subscription(id,system_id,endpoint_id,object_type_id,event_type,"
            + "status,created_at,updated_at) values "
            + "(901,7,801,31,'RECORD_CHANGED','active',current_timestamp,current_timestamp)");
    }
}
