package com.simplemdm.dto.mdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class RecordResponseJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesTheGenericRecordContractInSnakeCase() throws Exception {
        String json = objectMapper.writeValueAsString(new RecordResponse(
            42L, "person", 10L, "EMP001", "active", 3L,
            Map.of("employee_name", "Alice", "hire_date", "2026-07-31")
        ));

        assertThat(json).contains("\"object_type\":\"person\"")
            .contains("\"department_id\":10")
            .contains("\"record_code\":\"EMP001\"")
            .contains("\"data\":{");
    }
}
