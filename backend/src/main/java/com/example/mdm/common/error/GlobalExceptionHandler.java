package com.example.mdm.common.error;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception,
      HttpServletRequest request) {
    return ResponseEntity.status(exception.status())
        .body(ApiResponse.failure(exception.status().value(), exception.getMessage(), requestId(request)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.failure(400, "Validation failed", requestId(request)));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.failure(400, "Invalid request", requestId(request)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(HttpServletRequest request) {
    return ResponseEntity.internalServerError()
        .body(ApiResponse.failure(500, "Internal server error", requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(RequestId.ATTRIBUTE);
    return requestId == null ? null : requestId.toString();
  }
}
