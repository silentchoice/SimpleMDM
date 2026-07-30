package com.simplemdm.dto;

import java.util.Map;

public class ApiResponse {
    private int code;
    private String message;
    private Object data;

    public static ApiResponse ok(String message, Object data) {
        ApiResponse r = new ApiResponse();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static ApiResponse ok(Object data) { return ok("ok", data); }

    public static ApiResponse error(int code, String message) {
        ApiResponse r = new ApiResponse();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
