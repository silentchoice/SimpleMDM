package com.example.mdm.metadata;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sub-field")
public class SubFieldController {
  private final MetadataService service;

  public SubFieldController(MetadataService service) {
    this.service = service;
  }

  @GetMapping("/{subTypeId}")
  public ApiResponse<List<FieldDefinition>> list(@PathVariable long subTypeId,
      HttpServletRequest request) {
    return success(service.subFields(subTypeId), request);
  }

  @PostMapping("/{subTypeId}")
  public ApiResponse<SubmissionResponse> submit(@PathVariable long subTypeId,
      @Valid @RequestBody List<@Valid FieldRequest> body,
      HttpServletRequest request) {
    var fields = body.stream().map(field -> field.definition(subTypeId, true)).toList();
    return success(new SubmissionResponse(service.submitSubFields(subTypeId, fields)), request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record FieldRequest(@NotBlank String code, @NotBlank @Size(max = 128) String displayName,
      @NotNull FieldType fieldType, boolean required, List<String> options, boolean shared,
      int sortOrder) {
    FieldDefinition definition(long ownerTypeId, boolean allowShared) {
      return new FieldDefinition(0, ownerTypeId, code, displayName, fieldType, required,
          options == null ? List.of() : options, allowShared && shared, sortOrder,
          MetadataStatus.ACTIVE);
    }
  }

  public record SubmissionResponse(long approvalTaskId) {}
}
