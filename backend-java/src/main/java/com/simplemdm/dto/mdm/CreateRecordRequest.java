package com.simplemdm.dto.mdm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record CreateRecordRequest(
    Long id,
    @JsonProperty("department_id") Long departmentId,
    @JsonProperty("record_code") String recordCode,
    long version,
    Map<String, Object> data
) { }