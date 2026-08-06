package com.example.mdm.record;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordApprovalController {
  private final RecordApprovalService service;

  public RecordApprovalController(RecordApprovalService service) {
    this.service = service;
  }

  @PostMapping("/api/master-record-draft/{draftId}/submit")
  @PreAuthorize("hasRole('DEPT_EDITOR')")
  public ApiResponse<SubmissionResponse> submit(@PathVariable long draftId,
      @Valid @RequestBody SubmitRequest body, HttpServletRequest request) {
    return success(new SubmissionResponse(service.submit(draftId, body.token())), request);
  }

  @PostMapping("/api/record-approval/{taskId}/approve")
  @PreAuthorize("hasRole('DEPT_APPROVER')")
  public ApiResponse<Void> approve(@PathVariable long taskId,
      @Valid @RequestBody ApproveRequest body, HttpServletRequest request) {
    service.approve(taskId, body.comment());
    return success(null, request);
  }

  @PostMapping("/api/record-approval/{taskId}/reject")
  @PreAuthorize("hasRole('DEPT_APPROVER')")
  public ApiResponse<Void> reject(@PathVariable long taskId,
      @Valid @RequestBody RejectRequest body, HttpServletRequest request) {
    service.reject(taskId, body.reason());
    return success(null, request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record SubmitRequest(String token) {}
  public record SubmissionResponse(long approvalTaskId) {}
  public record ApproveRequest(@Size(max = 1000) String comment) {}
  public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {}
}
