package com.example.mdm.system;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/department")
public class DepartmentController {
  private final DepartmentService service;
  public DepartmentController(DepartmentService service) { this.service = service; }

  @GetMapping public ApiResponse<List<Department>> list(HttpServletRequest request) {
    return ApiResponse.success(service.list(), requestId(request));
  }
  @GetMapping("/{id}") public ApiResponse<Department> get(@PathVariable long id, HttpServletRequest request) {
    return ApiResponse.success(service.get(id), requestId(request));
  }
  @PostMapping public ApiResponse<Department> create(@Valid @RequestBody DepartmentRequest body, HttpServletRequest request) {
    return ApiResponse.success(service.create(body.code(), body.name()), requestId(request));
  }
  @PutMapping("/{id}") public ApiResponse<Department> update(@PathVariable long id,
      @Valid @RequestBody DepartmentRequest body, HttpServletRequest request) {
    return ApiResponse.success(service.update(id, body.code(), body.name()), requestId(request));
  }
  @PatchMapping("/{id}/status") public ApiResponse<Void> status(@PathVariable long id,
      @RequestParam EntityStatus status, HttpServletRequest request) {
    service.setStatus(id, status); return ApiResponse.success(null, requestId(request));
  }
  @DeleteMapping("/{id}") public ApiResponse<Void> disable(@PathVariable long id, HttpServletRequest request) {
    service.setStatus(id, EntityStatus.DISABLED);
    return ApiResponse.success(null, requestId(request));
  }
  private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestId.ATTRIBUTE); }
  public record DepartmentRequest(@NotBlank String code, @NotBlank String name) {}
}
