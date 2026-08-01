package com.simplemdm.controller;

import com.simplemdm.exception.GlobalExceptionHandler;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.service.system.RecordAccessService;
import com.simplemdm.service.workflow.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowControllerValidationTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(
            mock(ApprovalRequestRepository.class), mock(ApprovalChangeRepository.class),
            mock(ApprovalChildChangeRepository.class), mock(ApprovalChildValueChangeRepository.class),
            mock(FieldDefinitionRepository.class), mock(ChildFieldDefinitionRepository.class),
            mock(ChildTypeRepository.class),
            mock(ApprovalService.class), mock(RecordAccessService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createRequiresRecordCodeAndUpdateRequiresTargetVersion() throws Exception {
        mvc.perform(post("/api/workflow/approvals/submit").contentType(APPLICATION_JSON).content("""
            {"operation":"CREATE","object_code":"person","department_id":3,"data":{"name":"Alice"},"children":[]}
            """)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/workflow/approvals/submit").contentType(APPLICATION_JSON).content("""
            {"operation":"UPDATE","object_code":"person","record_id":2,"department_id":3,"data":{},"children":[]}
            """)).andExpect(status().isBadRequest());
    }

    @Test
    void childUpdateRequiresIdAndVersion() throws Exception {
        mvc.perform(post("/api/workflow/approvals/submit").contentType(APPLICATION_JSON).content("""
            {"operation":"UPDATE","object_code":"person","record_id":2,"expected_version":1,
             "department_id":3,"data":{},"children":[{"child_code":"phone","rows":[
               {"operation":"UPDATE","id":9,"data":{"number":"123"}}
             ]}]}
            """)).andExpect(status().isBadRequest());
    }

    @Test
    void submitRejectsClaimedActorFieldsInsteadOfTrustingThem() throws Exception {
        mvc.perform(post("/api/workflow/approvals/submit").contentType(APPLICATION_JSON).content("""
            {"operation":"CREATE","object_code":"person","record_code":"EMP-1","department_id":3,
             "data":{"name":"Alice"},"children":[],"applicant_id":999}
            """)).andExpect(status().isBadRequest());
    }
}
