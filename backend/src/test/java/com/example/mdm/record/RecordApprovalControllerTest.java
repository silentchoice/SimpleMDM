package com.example.mdm.record;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.common.api.RequestId;
import com.example.mdm.common.error.BusinessException;
import com.example.mdm.common.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecordApprovalControllerTest {
  private final RecordApprovalService service = Mockito.mock(RecordApprovalService.class);
  private MockMvc mvc;

  @BeforeEach void setUp() {
    Mockito.reset(service);
    mvc = MockMvcBuilders.standaloneSetup(new RecordApprovalController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test void submitReturnsTheApprovalTaskIdAndRequestEnvelope() throws Exception {
    Mockito.when(service.submit(91, "held-token")).thenReturn(701L);

    mvc.perform(post("/api/master-record-draft/91/submit")
            .requestAttr(RequestId.ATTRIBUTE, "req-submit")
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"held-token\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.approvalTaskId").value(701))
        .andExpect(jsonPath("$.requestId").value("req-submit"));
    verify(service).submit(91, "held-token");
  }

  @Test void approveAndRejectUseOnlyPathTaskIdAndValidatedReviewText() throws Exception {
    Mockito.when(service.approve(701, "looks good")).thenReturn(new RecordView(81, 9, 7,
        "CUS-1", Map.of(), List.of(), 4, "ACTIVE"));

    mvc.perform(post("/api/record-approval/701/approve")
            .param("departmentId", "999").param("reviewerId", "888")
            .requestAttr(RequestId.ATTRIBUTE, "req-approve")
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"looks good\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    mvc.perform(post("/api/record-approval/702/reject")
            .requestAttr(RequestId.ATTRIBUTE, "req-reject")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"stale data\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

    verify(service).approve(701, "looks good");
    verify(service).reject(702, "stale data");
  }

  @Test void validationAndBackendMessagesKeepTheExistingErrorEnvelope() throws Exception {
    Mockito.doThrow(BusinessException.badRequest("Invalid record snapshot"))
        .when(service).approve(701, null);

    mvc.perform(post("/api/record-approval/701/approve")
            .requestAttr(RequestId.ATTRIBUTE, "req-invalid")
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":null}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("Invalid record snapshot"))
        .andExpect(jsonPath("$.requestId").value("req-invalid"));
    mvc.perform(post("/api/record-approval/701/reject")
            .requestAttr(RequestId.ATTRIBUTE, "req-blank")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  }
}
