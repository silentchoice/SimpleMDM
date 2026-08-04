package com.example.mdm.metadata;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.common.error.BusinessException;
import com.example.mdm.common.error.GlobalExceptionHandler;
import com.example.mdm.common.api.RequestId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MetadataControllerTest {
  private final MetadataService service = Mockito.mock(MetadataService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    Mockito.reset(service);
    mvc = MockMvcBuilders.standaloneSetup(
            new MasterTypeController(service), new MasterFieldController(service),
            new SubTypeController(service), new SubFieldController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test
  void superAdminCreatesTemplateOnExactMasterTypePath() throws Exception {
    when(service.createMasterType("asset", "Asset"))
        .thenReturn(new MasterType(41, "ASSET", "Asset", MetadataStatus.ACTIVE));

    mvc.perform(post("/api/master-type").header("X-Request-Id", "req-create")
            .requestAttr(RequestId.ATTRIBUTE, "req-create").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"asset\",\"name\":\"Asset\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(41))
        .andExpect(jsonPath("$.requestId").value("req-create"));
  }

  @Test
  void superAdminAssignsTemplateToDepartment() throws Exception {
    mvc.perform(put("/api/master-type/41/departments/7")
            .requestAttr(RequestId.ATTRIBUTE, "req-assign"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    verify(service).assignDepartment(7, 41);
  }

  @Test
  void viewerReadsOnlyActiveMasterFieldsThroughService() throws Exception {
    when(service.masterFields(41)).thenReturn(List.of(field(11, 41, "serial")));
    mvc.perform(get("/api/master-field/41").requestAttr(RequestId.ATTRIBUTE, "req-read"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("serial"));
  }

  @Test
  void editorSubmitsMasterFieldSnapshotAndReceivesTaskId() throws Exception {
    when(service.submitMasterFields(anyList())).thenReturn(701L);
    mvc.perform(post("/api/master-field/41").requestAttr(RequestId.ATTRIBUTE, "req-submit")
            .contentType(MediaType.APPLICATION_JSON).content("[" + fieldJson("serial") + "]"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalTaskId").value(701));
    verify(service).submitMasterFields(org.mockito.ArgumentMatchers.argThat(fields ->
        fields.size() == 1 && fields.get(0).ownerTypeId() == 41 && !fields.get(0).shared()));
  }

  @Test
  void editorSubmitsSubTypesAndReceivesTaskId() throws Exception {
    when(service.submitSubTypes(anyList())).thenReturn(702L);
    mvc.perform(post("/api/sub-type/41").requestAttr(RequestId.ATTRIBUTE, "req-subtype")
            .contentType(MediaType.APPLICATION_JSON)
            .content("[{\"id\":0,\"masterTypeId\":41,\"code\":\"device\","
                + "\"name\":\"Device\",\"status\":\"ACTIVE\"}]"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalTaskId").value(702));
  }

  @Test
  void viewerReadsSubTypesAndSubFieldsOnExactPaths() throws Exception {
    when(service.subTypes(41)).thenReturn(List.of(
        new SubType(55, 41, "device", "Device", MetadataStatus.ACTIVE)));
    when(service.subFields(55)).thenReturn(List.of(field(12, 55, "model")));
    mvc.perform(get("/api/sub-type/41").requestAttr(RequestId.ATTRIBUTE, "req-types"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(55));
    mvc.perform(get("/api/sub-field/55").requestAttr(RequestId.ATTRIBUTE, "req-fields"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("model"));
  }

  @Test
  void editorSubmitsSubFieldsAndReceivesTaskId() throws Exception {
    when(service.submitSubFields(org.mockito.ArgumentMatchers.eq(55L), anyList())).thenReturn(703L);
    mvc.perform(post("/api/sub-field/55").requestAttr(RequestId.ATTRIBUTE, "req-subfield")
            .contentType(MediaType.APPLICATION_JSON).content("[" + fieldJson("model") + "]"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalTaskId").value(703));
    verify(service).submitSubFields(org.mockito.ArgumentMatchers.eq(55L),
        org.mockito.ArgumentMatchers.argThat(fields ->
            fields.size() == 1 && fields.get(0).ownerTypeId() == 55));
  }

  @Test
  void crossDepartmentServiceRejectionIsForbidden() throws Exception {
    when(service.masterFields(88)).thenThrow(BusinessException.forbidden());
    mvc.perform(get("/api/master-field/88").requestAttr(RequestId.ATTRIBUTE, "req-forbidden"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  }

  @Test
  void malformedSchemaIsBadRequest() throws Exception {
    mvc.perform(post("/api/master-field/41").requestAttr(RequestId.ATTRIBUTE, "req-bad")
            .contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  }

  @Test
  void missingTemplateIsNotFound() throws Exception {
    when(service.subTypes(404)).thenThrow(BusinessException.notFound("Master type"));
    mvc.perform(get("/api/sub-type/404").requestAttr(RequestId.ATTRIBUTE, "req-missing"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void duplicatePendingSubmissionIsConflict() throws Exception {
    when(service.submitMasterFields(anyList()))
        .thenThrow(new BusinessException(org.springframework.http.HttpStatus.CONFLICT,
            "Pending metadata approval already exists"));
    mvc.perform(post("/api/master-field/41").requestAttr(RequestId.ATTRIBUTE, "req-conflict")
            .contentType(MediaType.APPLICATION_JSON).content("[" + fieldJson("serial") + "]"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  }

  @Test
  void monolithicMetadataRouteDoesNotExist() throws Exception {
    mvc.perform(get("/api/metadata/master-types").requestAttr(RequestId.ATTRIBUTE, "req-old"))
        .andExpect(status().isNotFound());
  }

  private FieldDefinition field(long id, long owner, String code) {
    return new FieldDefinition(id, owner, code, code, FieldType.TEXT, false, List.of(), false, 0,
        MetadataStatus.ACTIVE);
  }

  private String fieldJson(String code) {
    return "{\"id\":0,\"ownerTypeId\":41,\"code\":\"" + code
        + "\",\"displayName\":\"Serial\",\"fieldType\":\"TEXT\",\"required\":false,"
        + "\"options\":[],\"shared\":false,\"sortOrder\":0,\"status\":\"ACTIVE\"}";
  }
}
