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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master-type")
public class MasterTypeController {
  private final MetadataService service;

  public MasterTypeController(MetadataService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<MasterType>> list(HttpServletRequest request) {
    return success(service.masterTypes(), request);
  }

  @GetMapping("/current")
  public ApiResponse<MasterType> current(HttpServletRequest request) {
    return success(service.currentMasterType(), request);
  }

  @PostMapping
  public ApiResponse<MasterType> create(@Valid @RequestBody TypeRequest body,
      HttpServletRequest request) {
    return success(service.createMasterType(body.code(), body.name()), request);
  }

  @PutMapping("/{masterTypeId}/departments/{departmentId}")
  public ApiResponse<Void> assign(@PathVariable long masterTypeId, @PathVariable long departmentId,
      HttpServletRequest request) {
    service.assignDepartment(departmentId, masterTypeId);
    return success(null, request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record TypeRequest(@NotBlank String code, @NotBlank String name) {}
}
