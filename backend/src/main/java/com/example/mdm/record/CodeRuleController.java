package com.example.mdm.record;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master-type/{masterTypeId}/code-rule")
public class CodeRuleController {
  private final CodeRuleService service;

  public CodeRuleController(CodeRuleService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<CodeRuleResponse> get(@PathVariable long masterTypeId, HttpServletRequest request) {
    return success(response(service.find(masterTypeId)), request);
  }

  @PutMapping
  public ApiResponse<CodeRuleResponse> put(@PathVariable long masterTypeId,
      @Valid @RequestBody CodeRuleRequest body, HttpServletRequest request) {
    return success(response(service.save(masterTypeId, body.pattern())), request);
  }

  private CodeRuleResponse response(CodeRule rule) {
    return new CodeRuleResponse(rule.pattern(), rule.sequenceWidth(), service.preview(rule));
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record CodeRuleRequest(@NotBlank String pattern) {}
  public record CodeRuleResponse(String pattern, int sequenceWidth, String preview) {}
}
