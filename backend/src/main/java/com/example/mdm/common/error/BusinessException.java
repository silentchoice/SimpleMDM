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

  public static BusinessException authenticationStateChanged() {
    return new BusinessException(HttpStatus.UNAUTHORIZED, "Authentication state changed");
  }

  public static BusinessException forbidden() {
    return new BusinessException(HttpStatus.FORBIDDEN, "Forbidden");
  }

  public static BusinessException notFound(String resource) {
    return new BusinessException(HttpStatus.NOT_FOUND, resource + " not found");
  }

  public static BusinessException badRequest(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, message);
  }
}
