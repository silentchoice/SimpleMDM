package com.example.mdm.record;

import com.example.mdm.common.api.ApiResponse;
import com.example.mdm.common.api.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordController {
  private final RecordQueryService queries;
  private final RecordDraftService drafts;

  public RecordController(RecordQueryService queries, RecordDraftService drafts) {
    this.queries = queries;
    this.drafts = drafts;
  }

  @GetMapping("/api/master-record")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_EDITOR','DEPT_APPROVER','DEPT_VIEWER')")
  public ApiResponse<RecordQueryService.Paged<RecordView>> list(
      @RequestParam(required = false) Long masterTypeId,
      @RequestParam(required = false) String recordCode,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime updatedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime updatedTo,
      @RequestParam(defaultValue = "false") boolean includeDeleted,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDirection,
      HttpServletRequest request) {
    return success(queries.list(new RecordQueryService.RecordQuery(masterTypeId, recordCode,
        keyword, status, includeDeleted, page, size, sortBy, sortDirection, updatedFrom,
        updatedTo)), request);
  }

  @GetMapping("/api/master-record/{recordId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_EDITOR','DEPT_APPROVER','DEPT_VIEWER')")
  public ApiResponse<RecordView> detail(@PathVariable long recordId, HttpServletRequest request) {
    return success(queries.detail(recordId), request);
  }

  @GetMapping("/api/master-record/{recordId}/history")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_EDITOR','DEPT_APPROVER','DEPT_VIEWER')")
  public ApiResponse<List<RecordView>> history(@PathVariable long recordId,
      HttpServletRequest request) {
    return success(queries.history(recordId), request);
  }

  @PostMapping("/api/master-record-draft")
  @PreAuthorize("hasRole('DEPT_EDITOR')")
  public ApiResponse<RecordDraft> create(@Valid @RequestBody RecordDraftCommand command,
      HttpServletRequest request) {
    return success(drafts.create(command), request);
  }

  @PutMapping("/api/master-record-draft/{draftId}")
  @PreAuthorize("hasRole('DEPT_EDITOR')")
  public ApiResponse<RecordDraft> update(@PathVariable long draftId,
      @Valid @RequestBody RecordDraftCommand command, HttpServletRequest request) {
    return success(drafts.update(draftId, command), request);
  }

  @GetMapping("/api/master-record-draft/{draftId}")
  @PreAuthorize("hasRole('DEPT_EDITOR')")
  public ApiResponse<RecordDraft> draft(@PathVariable long draftId, HttpServletRequest request) {
    return success(drafts.getDraft(draftId), request);
  }

  @PostMapping("/api/master-record-draft/{draftId}/copy")
  @PreAuthorize("hasRole('DEPT_EDITOR')")
  public ApiResponse<RecordDraft> copy(@PathVariable long draftId, HttpServletRequest request) {
    return success(drafts.copyRejected(draftId), request);
  }

  @PostMapping("/api/master-record/{recordId}/delete-request")
  @PreAuthorize("hasRole('DEPT_EDITOR')")
  public ApiResponse<RecordDraft> logicalDelete(@PathVariable long recordId,
      @Valid @RequestBody DeleteRequest body, HttpServletRequest request) {
    return success(drafts.logicalDelete(recordId, body.reason()), request);
  }

  private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return ApiResponse.success(data, (String) request.getAttribute(RequestId.ATTRIBUTE));
  }

  public record DeleteRequest(@NotBlank @Size(max = 1000) String reason) {}
}
