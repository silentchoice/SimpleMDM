package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginRequest {
    @NotBlank @JsonProperty("system_code") public String systemCode;
    @NotBlank public String username;
    @NotBlank public String password;
}
