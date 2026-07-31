package com.simplemdm.dto.mdm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRecordRequest(
    @NotNull(groups = Update.class) Long id,
    @NotNull(groups = Create.class) @JsonProperty("department_id") Long departmentId,
    @NotBlank(groups = Create.class) @JsonProperty("record_code") String recordCode,
    @NotNull(groups = Update.class) Long version,
    @NotNull(groups = {Create.class, Update.class}) Map<String, Object> data
) {
    public interface Create { }
    public interface Update { }
}