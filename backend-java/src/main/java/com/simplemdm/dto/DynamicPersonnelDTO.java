package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class DynamicPersonnelDTO {
    @NotBlank
    public String ownerDept;

    @NotNull
    public Map<String, Object> data;

    public Integer version;
}
