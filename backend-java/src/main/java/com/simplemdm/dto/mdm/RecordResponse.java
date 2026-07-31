package com.simplemdm.dto.mdm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record RecordResponse(
    Long id,
    @JsonProperty("object_type") String objectType,
    @JsonProperty("department_id") Long departmentId,
    @JsonProperty("record_code") String recordCode,
    String status,
    long version,
    Map<String, Object> data
) { }