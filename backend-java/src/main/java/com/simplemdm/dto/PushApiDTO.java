package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PushApiDTO {
    public Long id;
    @NotBlank public String name;
    @NotBlank public String targetSystem;
    @NotBlank public String method = "POST";
    @NotBlank public String baseUrl;
    public String authType = "token";
    public String authConfig;
    public String status = "active";
    public String description;
    public Integer retryMax = 3;
    public Integer timeoutSec = 30;
}
