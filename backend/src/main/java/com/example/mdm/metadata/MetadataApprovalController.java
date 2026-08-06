package com.example.mdm.metadata;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(required = false) String taskType, HttpServletRequest request) {
    return success(taskType == null ? query.list(status) : query.list(status, taskType), request);
  }

  @GetMapping("/{taskId}")
  public ApiResponse<MetadataApprovalRepository.ApprovalTaskView> detail(
      @PathVariable long taskId, @RequestParam(required = false) String taskType,
      HttpServletRequest request) {
    return success(taskType == null ? query.detail(taskId) : query.detail(taskId, taskType), request);
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

  public record ApproveRequest(@Size(max = 1000) String comment) {}

  public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {}
}
