package com.example.mdm.metadata;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.common.api.RequestId;
import com.example.mdm.common.error.BusinessException;
import com.example.mdm.common.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MetadataApprovalControllerTest {
  private final MetadataApprovalQueryService query = Mockito.mock(MetadataApprovalQueryService.class);
  private final MetadataApprovalApplicationService application =
      Mockito.mock(MetadataApprovalApplicationService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    Mockito.reset(query, application);
    mvc = MockMvcBuilders.standaloneSetup(new MetadataApprovalController(query, application))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setMessageConverters(new MappingJackson2HttpMessageConverter(
            new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
        .build();
  }

  @Test void listUsesPendingDefaultAndReturnsCompleteEnvelopeProjection() throws Exception {
    when(query.list("PENDING")).thenReturn(List.of(task(91, "PENDING")));

    mvc.perform(get("/api/metadata-approval")
            .requestAttr(RequestId.ATTRIBUTE, "req-list"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.message").value("OK"))
        .andExpect(jsonPath("$.requestId").value("req-list"))
        .andExpect(jsonPath("$.data[0].id").value(91))
        .andExpect(jsonPath("$.data[0].entityKind").value("MASTER_FIELDS"))
        .andExpect(jsonPath("$.data[0].entityId").value(41))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data[0].beforeSnapshot").value("{\"before\":true}"))
        .andExpect(jsonPath("$.data[0].afterSnapshot").value("{\"after\":true}"))
        .andExpect(jsonPath("$.data[0].submittedBy").value(12))
        .andExpect(jsonPath("$.data[0].reviewedBy").doesNotExist())
        .andExpect(jsonPath("$.data[0].submittedAt").value("2026-08-04T09:30:00"));
    verify(query).list("PENDING");
  }

  @Test void listPassesExplicitStatusFilter() throws Exception {
    when(query.list("APPROVED")).thenReturn(List.of());

    mvc.perform(get("/api/metadata-approval").param("status", "APPROVED")
            .requestAttr(RequestId.ATTRIBUTE, "req-filter"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    verify(query).list("APPROVED");
  }

  @Test void detailReturnsTaskWithoutAcceptingDepartmentOrReviewerIds() throws Exception {
    when(query.detail(91)).thenReturn(task(91, "PENDING"));

    mvc.perform(get("/api/metadata-approval/91")
            .param("departmentId", "999").param("reviewerId", "888")
            .requestAttr(RequestId.ATTRIBUTE, "req-detail"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(91))
        .andExpect(jsonPath("$.requestId").value("req-detail"));
    verify(query).detail(91);
  }

  @Test void approveDelegatesOnlyTaskIdAndNullableComment() throws Exception {
    mvc.perform(post("/api/metadata-approval/91/approve")
            .param("departmentId", "999").param("reviewerId", "888")
            .requestAttr(RequestId.ATTRIBUTE, "req-approve")
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":null}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.requestId").value("req-approve"));
    verify(application).approve(91, null);
  }

  @Test void rejectDelegatesTaskIdAndReason() throws Exception {
    mvc.perform(post("/api/metadata-approval/91/reject")
            .requestAttr(RequestId.ATTRIBUTE, "req-reject")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"stale fields\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    verify(application).reject(91, "stale fields");
  }

  @Test void blankRejectReasonIsValidationErrorBeforeApplicationService() throws Exception {
    mvc.perform(post("/api/metadata-approval/91/reject")
            .requestAttr(RequestId.ATTRIBUTE, "req-bad")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.requestId").value("req-bad"));
    verify(application, never()).reject(Mockito.anyLong(), Mockito.any());
  }

  @Test void approveCommentAllows1000CharactersAndRejects1001BeforeService() throws Exception {
    String accepted = "x".repeat(1000);
    String rejected = "x".repeat(1001);

    mvc.perform(post("/api/metadata-approval/91/approve")
            .requestAttr(RequestId.ATTRIBUTE, "req-comment-long")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"comment\":\"" + rejected + "\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    mvc.perform(post("/api/metadata-approval/91/approve")
            .requestAttr(RequestId.ATTRIBUTE, "req-comment-boundary")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"comment\":\"" + accepted + "\"}"))
        .andExpect(status().isOk());
    verify(application).approve(91, accepted);
  }

  @Test void rejectReasonAllows1000CharactersAndRejects1001BeforeService() throws Exception {
    String accepted = "x".repeat(1000);
    String rejected = "x".repeat(1001);

    mvc.perform(post("/api/metadata-approval/91/reject")
            .requestAttr(RequestId.ATTRIBUTE, "req-reason-long")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"" + rejected + "\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    mvc.perform(post("/api/metadata-approval/91/reject")
            .requestAttr(RequestId.ATTRIBUTE, "req-reason-boundary")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"" + accepted + "\"}"))
        .andExpect(status().isOk());
    verify(application).reject(91, accepted);
  }

  @Test void missingDetailPropagatesNotFoundEnvelope() throws Exception {
    when(query.detail(404)).thenThrow(BusinessException.notFound("Approval task"));

    mvc.perform(get("/api/metadata-approval/404")
            .requestAttr(RequestId.ATTRIBUTE, "req-missing"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Approval task not found"));
  }

  @Test void nonPendingApprovalPropagatesConflictEnvelope() throws Exception {
    Mockito.doThrow(new BusinessException(HttpStatus.CONFLICT, "Approval task is not pending"))
        .when(application).approve(91, "again");

    mvc.perform(post("/api/metadata-approval/91/approve")
            .requestAttr(RequestId.ATTRIBUTE, "req-conflict")
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"again\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value("Approval task is not pending"))
        .andExpect(jsonPath("$.requestId").value("req-conflict"));
  }

  private MetadataApprovalRepository.ApprovalTaskView task(long id, String status) {
    return new MetadataApprovalRepository.ApprovalTaskView(id, "MASTER_FIELDS", 41, status,
        "{\"before\":true}", "{\"after\":true}", 12, null, null,
        LocalDateTime.of(2026, 8, 4, 9, 30), null);
  }
}
