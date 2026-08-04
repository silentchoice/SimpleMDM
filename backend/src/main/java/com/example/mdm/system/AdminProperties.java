package com.example.mdm.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.initial-admin")
public record AdminProperties(String username, String password, String displayName) {}
