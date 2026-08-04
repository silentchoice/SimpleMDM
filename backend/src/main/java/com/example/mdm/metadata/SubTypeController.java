package com.example.mdm.metadata;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sub-type")
public class SubTypeController {
  private final MetadataService service;

  public SubTypeController(MetadataService service) {
    this.service = service;
  }

  @GetMapping("/{masterTypeId}")
  public ApiResponse<List<SubType>> list(@PathVariable long masterTypeId,
      HttpServletRequest request) {
    return success(service.subTypes(masterTypeId), request);
  }

  @PostMapping("/{masterTypeId}")
  public ApiResponse<SubmissionResponse> submit(@PathVariable long masterTypeId,
      @Valid @RequestBody List<@Valid SubTypeRequest> body, HttpServletRequest request) {
    var types = body.stream().map(type -> new SubType(0, masterTypeId, type.code(), type.name(),
        MetadataStatus.ACTIVE)).toList();
    return success(new SubmissionResponse(service.submitSubTypes(types)), request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record SubTypeRequest(@NotBlank String code, @NotBlank String name) {}

  public record SubmissionResponse(long approvalTaskId) {}
}
