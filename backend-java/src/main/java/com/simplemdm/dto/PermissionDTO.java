package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PermissionDTO {
    public Long id;
    @NotBlank public Long userId;
    @NotBlank public String permType;   // VIEW | EDIT
    @NotBlank public String scopeType;  // DEPT | POSITION | ALL
    public String scopeValue;
}
