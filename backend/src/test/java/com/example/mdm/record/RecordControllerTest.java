package com.example.mdm.record;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.common.api.RequestId;
import com.example.mdm.common.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecordControllerTest {
  private final RecordQueryService queries = Mockito.mock(RecordQueryService.class);
  private final RecordDraftService drafts = Mockito.mock(RecordDraftService.class);
  private MockMvc mvc;

  @BeforeEach void setUp() {
    Mockito.reset(queries, drafts);
    mvc = MockMvcBuilders.standaloneSetup(new RecordController(queries, drafts))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test void listUsesThePagedFilterContractAndPreservesTheRequestEnvelope() throws Exception {
    when(queries.list(Mockito.any())).thenReturn(new RecordQueryService.Paged<>(List.of(view()),
        0, 20, 1, 1));

    mvc.perform(get("/api/master-record").param("masterTypeId", "9")
            .param("recordCode", "CUS").param("keyword", "North").param("status", "ACTIVE")
            .param("updatedFrom", "2026-08-01T00:00:00")
            .param("updatedTo", "2026-08-31T23:59:59")
            .param("includeDeleted", "false").param("page", "0").param("size", "20")
            .param("sortBy", "recordCode").param("sortDirection", "asc")
            .requestAttr(RequestId.ATTRIBUTE, "req-list"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.content[0].recordCode").value("CUS-20260805-0001"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.requestId").value("req-list"));

    verify(queries).list(argThat(query -> query.masterTypeId() == 9
        && query.page() == 0 && query.size() == 20 && "North".equals(query.keyword())
        && "recordCode".equals(query.sortBy())
        && java.time.LocalDateTime.of(2026, 8, 1, 0, 0).equals(query.updatedFrom())
        && java.time.LocalDateTime.of(2026, 8, 31, 23, 59, 59).equals(query.updatedTo())));
  }

  @Test void detailAndHistoryReturnFilteredDtosAndNoMoreThanThreeVersions() throws Exception {
    when(queries.detail(81)).thenReturn(view());
    when(queries.history(81)).thenReturn(List.of(view(), view(), view()));

    mvc.perform(get("/api/master-record/81").requestAttr(RequestId.ATTRIBUTE, "req-detail"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(81))
        .andExpect(jsonPath("$.requestId").value("req-detail"));
    mvc.perform(get("/api/master-record/81/history")
            .requestAttr(RequestId.ATTRIBUTE, "req-history"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
  }

  @Test void draftCreateUpdateCopyAndLogicalDeleteUseOnlyAuthoritativePaths() throws Exception {
    when(drafts.create(Mockito.any())).thenReturn(draft(91));
    when(drafts.update(Mockito.eq(91L), Mockito.any())).thenReturn(draft(91));
    when(drafts.copyRejected(91)).thenReturn(draft(92));
    when(drafts.logicalDelete(81, "Duplicate supplier")).thenReturn(draft(93));
    String command = "{\"recordId\":null,\"masterTypeId\":9,\"baseVersion\":0,"
        + "\"action\":\"CREATE\",\"masterValues\":{\"name\":\"North\"},\"children\":[]}";

    mvc.perform(post("/api/master-record-draft").contentType(MediaType.APPLICATION_JSON)
            .content(command).requestAttr(RequestId.ATTRIBUTE, "req-create"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(91));
    mvc.perform(put("/api/master-record-draft/91").contentType(MediaType.APPLICATION_JSON)
            .content(command).requestAttr(RequestId.ATTRIBUTE, "req-update"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(91));
    mvc.perform(post("/api/master-record-draft/91/copy")
            .requestAttr(RequestId.ATTRIBUTE, "req-copy"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(92));
    mvc.perform(post("/api/master-record/81/delete-request")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Duplicate supplier\"}")
            .requestAttr(RequestId.ATTRIBUTE, "req-delete"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(93));

    verify(drafts).update(Mockito.eq(91L), argThat(body -> body.recordId() == null));
    verify(drafts).logicalDelete(81, "Duplicate supplier");
  }

  @Test void draftDetailUsesThePathIdAndStandardRequestEnvelope() throws Exception {
    when(drafts.getDraft(91)).thenReturn(draft(91));

    mvc.perform(get("/api/master-record-draft/91")
            .requestAttr(RequestId.ATTRIBUTE, "req-draft"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(91))
        .andExpect(jsonPath("$.requestId").value("req-draft"));
    verify(drafts).getDraft(91);
  }

  @Test void noPhysicalDeleteEndpointExists() throws Exception {
    mvc.perform(delete("/api/master-record/81")
            .requestAttr(RequestId.ATTRIBUTE, "req-no-delete"))
        .andExpect(status().isMethodNotAllowed());
  }

  private RecordView view() {
    return new RecordView(81, 9, 7, "CUS-20260805-0001", Map.of("name", "North"),
        List.of(), 3, "ACTIVE");
  }

  private RecordDraft draft(long id) {
    return new RecordDraft(id, null, 9, 7, "CUS-20260805-0001", RecordAction.CREATE, 0,
        Map.of("name", "North"), List.of(), RecordStatus.DRAFT, 12, null);
  }
}
