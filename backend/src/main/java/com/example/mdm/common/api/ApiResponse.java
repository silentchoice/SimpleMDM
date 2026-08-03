package com.example.mdm.common.api;

public record ApiResponse<T>(int code, String message, T data, String requestId) {

  public static <T> ApiResponse<T> success(T data, String requestId) {
    return new ApiResponse<>(0, "OK", data, requestId);
  }

  public static <T> ApiResponse<T> failure(int code, String message, String requestId) {
    return new ApiResponse<>(code, message, null, requestId);
  }
}
