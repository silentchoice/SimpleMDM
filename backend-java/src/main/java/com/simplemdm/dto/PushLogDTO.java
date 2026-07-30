package com.simplemdm.dto;

public class PushLogDTO {
    public Long id;
    public Long approvalId;
    public Long personnelId;
    public String personnelName;
    public String targetSystem;
    public String status;
    public String requestBody;
    public String responseBody;
    public Integer responseCode;
    public Integer retryCount;
    public String errorMessage;
    public String pushedAt;
    public String createdAt;
}
