package com.simplemdm.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JsonTest
class DynamicPersonnelDTOJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bindsSnakeCaseOwnerDepartmentFromFrontendPayload() throws Exception {
        DynamicPersonnelDTO dto = objectMapper.readValue(
            """
            {
              "owner_dept": "工程部",
              "data": {}
            }
            """,
            DynamicPersonnelDTO.class
        );

        assertEquals("工程部", dto.ownerDept);
    }
}
