package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class PersonnelSubDTO {
    public Long id;
    public Long personnelId;
    @NotBlank public String subType;
    @NotNull public Map<String, Object> data;
    public String visibility;
    public Integer version;
}
