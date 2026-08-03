package com.example.mdm.common.error;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  private final HttpStatus status;

  public BusinessException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static BusinessException unauthorized() {
    return new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
  }
}
