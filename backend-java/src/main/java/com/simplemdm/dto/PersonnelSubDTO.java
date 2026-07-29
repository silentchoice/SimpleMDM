package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PersonnelSubDTO {
    public Long id;
    public Long personnelId;
    @NotBlank public String subType;
    @NotBlank public String dataJson;
    @NotBlank public String ownerDept;
    public String visibility;
    public Integer version;
}
