package com.simplemdm.dto.mdm;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public record MasterChildChangeRequest(
    @NotNull Operation operation,
    @JsonProperty("object_code")
    @NotBlank String objectCode,
    @JsonProperty("record_id")
    Long recordId,
    @JsonProperty("expected_version")
    Long expectedVersion,
    @JsonProperty("record_code")
    @Size(max = 128) String recordCode,
    @JsonProperty("department_id")
    @NotNull Long departmentId,
    @NotNull Map<String, Object> data,
    @NotNull List<@Valid ChildGroup> children
) {
    public MasterChildChangeRequest {
        children = children == null ? null : List.copyOf(children);
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("Unknown request field: " + fieldName);
    }

    @AssertTrue(message = "Create requires record code and no existing target; update requires record ID and version")
    public boolean isTargetValid() {
        if (operation == null) return true;
        return operation == Operation.CREATE
            ? recordId == null && expectedVersion == null && recordCode != null && !recordCode.isBlank()
            : recordId != null && expectedVersion != null;
    }

    public enum Operation { CREATE, UPDATE }
    public enum ChildOperation { CREATE, UPDATE, DELETE }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ChildGroup(
        @JsonProperty("child_code") @NotBlank String childCode,
        @NotEmpty List<@Valid ChildRow> rows
    ) {
        public ChildGroup {
            rows = rows == null ? null : List.copyOf(rows);
        }

        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object ignored) {
            throw new IllegalArgumentException("Unknown child group field: " + fieldName);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ChildRow(@NotNull ChildOperation operation, Long id,
                           @JsonProperty("expected_version") Long expectedVersion,
                           Map<String, Object> data) {
        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object ignored) {
            throw new IllegalArgumentException("Unknown child row field: " + fieldName);
        }

        @AssertTrue(message = "Child create requires no target; child update/delete require ID and version")
        public boolean isTargetValid() {
            if (operation == null) return true;
            if (operation == ChildOperation.CREATE) return id == null && expectedVersion == null && data != null;
            return id != null && expectedVersion != null
                && (operation == ChildOperation.DELETE || data != null);
        }
    }
}
