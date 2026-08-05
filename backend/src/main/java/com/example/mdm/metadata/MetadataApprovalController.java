package com.example.mdm.metadata;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metadata-approval")
@PreAuthorize("hasRole('DEPT_APPROVER')")
public class MetadataApprovalController {
  private final MetadataApprovalQueryService query;
  private final MetadataApprovalApplicationService application;

  public MetadataApprovalController(MetadataApprovalQueryService query,
      MetadataApprovalApplicationService application) {
    this.query = query;
    this.application = application;
  }

  @GetMapping
  public ApiResponse<List<MetadataApprovalRepository.ApprovalTaskView>> list(
      @RequestParam(defaultValue = "PENDING") String status, HttpServletRequest request) {
    return success(query.list(status), request);
  }

  @GetMapping("/{taskId}")
  public ApiResponse<MetadataApprovalRepository.ApprovalTaskView> detail(
      @PathVariable long taskId, HttpServletRequest request) {
    return success(query.detail(taskId), request);
  }

  @PostMapping("/{taskId}/approve")
  public ApiResponse<Void> approve(@PathVariable long taskId,
      @Valid @RequestBody ApproveRequest body, HttpServletRequest request) {
    application.approve(taskId, body.comment());
    return success(null, request);
  }

  @PostMapping("/{taskId}/reject")
  public ApiResponse<Void> reject(@PathVariable long taskId,
      @Valid @RequestBody RejectRequest body, HttpServletRequest request) {
    application.reject(taskId, body.reason());
    return success(null, request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record ApproveRequest(String comment) {}

  public record RejectRequest(@NotBlank String reason) {}
}
