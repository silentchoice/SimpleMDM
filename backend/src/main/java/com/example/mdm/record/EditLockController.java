package com.example.mdm.record;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master-record/{recordId}/lock")
public class EditLockController {
  private final EditLockService service;

  public EditLockController(EditLockService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<EditLock> acquire(@PathVariable long recordId, HttpServletRequest request) {
    return success(service.acquire(recordId), request);
  }

  @PutMapping
  public ApiResponse<EditLock> renew(@PathVariable long recordId,
      @Valid @RequestBody LockTokenRequest body, HttpServletRequest request) {
    return success(service.renew(recordId, body.token()), request);
  }

  @DeleteMapping
  public ApiResponse<Void> release(@PathVariable long recordId,
      @Valid @RequestBody LockTokenRequest body, HttpServletRequest request) {
    service.release(recordId, body.token());
    return success(null, request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record LockTokenRequest(@NotBlank String token) {}
}
