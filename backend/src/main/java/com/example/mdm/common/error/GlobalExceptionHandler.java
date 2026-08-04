package com.example.mdm.common.error;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import com.example.mdm.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
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

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ApiResponse<Void>> handleNotFound(HttpServletRequest request) {
    return failure(404, "Not found", request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpServletRequest request) {
    return failure(405, "Method not allowed", request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpServletRequest request) {
    return failure(415, "Unsupported media type", request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(HttpServletRequest request) {
    return failure(403, "Forbidden", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception,
      HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String operator = "anonymous";
    String departmentId = "none";
    if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
      operator = principal.username();
      if (principal.department() != null) {
        departmentId = Long.toString(principal.department().id());
      }
    }
    log.error("Unexpected error requestId={} operator={} departmentId={} exceptionType={}",
        requestId(request), operator, departmentId, exception.getClass().getName());
    return ResponseEntity.internalServerError()
        .body(ApiResponse.failure(500, "Internal server error", requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(RequestId.ATTRIBUTE);
    return requestId == null ? null : requestId.toString();
  }

  private ResponseEntity<ApiResponse<Void>> failure(int status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(ApiResponse.failure(status, message, requestId(request)));
  }
}
