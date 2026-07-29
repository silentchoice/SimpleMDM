package com.simplemdm.dto;

import java.util.List;
import java.util.Map;

public class LoginResponse {
    public String token;
    public Map<String, Object> user;  // id, username, real_name, department, is_admin, status, permissions
    public List<Map<String, Object>> permissions;
}
