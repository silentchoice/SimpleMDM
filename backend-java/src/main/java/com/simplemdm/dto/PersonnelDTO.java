package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PersonnelDTO {
    public Long id;
    @NotBlank public String employeeCode;
    @NotBlank public String name;
    public String gender;
    @NotBlank public String department;
    public String position;
    public String phone;
    public String email;
    public String status;
    public Integer version;
}
