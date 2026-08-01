package com.simplemdm.controller;

import com.simplemdm.dto.mdm.MetadataCommands;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.exception.GlobalExceptionHandler;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MetadataAuditRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.mdm.MetadataManagementService;
import com.simplemdm.service.mdm.MetadataService;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetadataManagementControllerTest {
    private ObjectTypeRepository objectTypes;
    private FieldDefinitionRepository fields;
    private RecordValueRepository recordValues;
    private MetadataAuditRepository audits;
    private AuthorizationService authorization;
    private MetadataManagementService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        objectTypes = mock(ObjectTypeRepository.class);
        fields = mock(FieldDefinitionRepository.class);
        recordValues = mock(RecordValueRepository.class);
        audits = mock(MetadataAuditRepository.class);
        authorization = mock(AuthorizationService.class);
        service = new MetadataManagementService(objectTypes, fields, recordValues, audits, authorization,
            new MetadataService(objectTypes, fields));
        mvc = MockMvcBuilders.standaloneSetup(new MetadataManagementController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        User user = mock(User.class);
        given(user.getId()).willReturn(7L);
        given(user.getSystemId()).willReturn(10L);
        given(user.getDepartmentId()).willReturn(20L);
        JwtInterceptor.CURRENT_USER.set(user);
        given(authorization.can(7L, "MDM_FIELD_MANAGE", 20L)).willReturn(true);
    }

    @AfterEach
    void clearUser() { JwtInterceptor.CURRENT_USER.remove(); }

    @Test
    void fieldManagerCreatesUpdatesAndDeactivatesMasterFieldWithAuditIds() throws Exception {
        ObjectType person = objectType(100L, 10L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(fields.existsByObjectTypeIdAndFieldKey(100L, "employee_code")).willReturn(false);
        given(fields.save(any(FieldDefinition.class))).willAnswer(invocation -> {
            FieldDefinition value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 200L);
            return value;
        });
        given(audits.save(any())).willAnswer(invocation -> {
            Object audit = invocation.getArgument(0);
            ReflectionTestUtils.setField(audit, "id", 300L);
            return audit;
        });

        mvc.perform(post("/api/mdm/object-types/person/fields").contentType(APPLICATION_JSON).content("""
                {"field_key":"employee_code","field_name":"Employee code","data_type":"STRING",
                 "required":true,"unique_value":true,"searchable":true,"sort_order":1}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.field.id").value(200))
            .andExpect(jsonPath("$.data.field.data_type").value("STRING"))
            .andExpect(jsonPath("$.data.audit_id").value(300));

        FieldDefinition field = FieldDefinition.create(100L, person,
            new com.simplemdm.service.mdm.CreateFieldCommand("employee_code", "Employee code", FieldDataType.STRING,
                true, true, true, false, 64, null, null, null, null, null, 1), null);
        ReflectionTestUtils.setField(field, "id", 200L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(fields.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(field));

        mvc.perform(patch("/api/mdm/object-types/person/fields/200").contentType(APPLICATION_JSON).content("""
                {"field_name":"Employee number","data_type":"STRING","required":true,
                 "unique_value":true,"searchable":true,"sort_order":2}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.field.field_name").value("Employee number"));
        mvc.perform(post("/api/mdm/object-types/person/fields/200/deactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.field.status").value("inactive"));
        mvc.perform(post("/api/mdm/object-types/person/fields/200/reactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.field.status").value("active"));
        verify(audits, org.mockito.Mockito.times(4)).save(any());
    }

    @Test
    void hidesCrossSystemObjectAndField() throws Exception {
        given(objectTypes.findBySystemIdAndCode(10L, "foreign")).willReturn(Optional.empty());
        mvc.perform(post("/api/mdm/object-types/foreign/fields").contentType(APPLICATION_JSON)
                .content("{\"field_key\":\"x\",\"field_name\":\"X\",\"data_type\":\"STRING\"}"))
            .andExpect(status().isNotFound());
        given(fields.findBySystemIdAndId(10L, 999L)).willReturn(Optional.empty());
        mvc.perform(post("/api/mdm/object-types/person/fields/999/deactivate"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deniesUsersWithoutFieldManagementPermission() throws Exception {
        given(authorization.can(7L, "MDM_FIELD_MANAGE", 20L)).willReturn(false);
        mvc.perform(post("/api/mdm/object-types/person/fields").contentType(APPLICATION_JSON)
                .content("{\"field_key\":\"x\",\"field_name\":\"X\",\"data_type\":\"STRING\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void fieldManagerUpdatesAndDeactivatesCurrentSystemObjectTypeWithAudit() throws Exception {
        ObjectType person = objectType(100L, 10L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(objectTypes.save(any(ObjectType.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(audits.save(any())).willAnswer(invocation -> {
            Object audit = invocation.getArgument(0);
            ReflectionTestUtils.setField(audit, "id", 301L);
            return audit;
        });

        mvc.perform(patch("/api/mdm/object-types/person").contentType(APPLICATION_JSON).content("""
                {"name":"人员主数据","approval_required":true,"department_scoped":false}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.object_type.code").value("person"))
            .andExpect(jsonPath("$.data.object_type.name").value("人员主数据"))
            .andExpect(jsonPath("$.data.object_type.approval_required").value(true))
            .andExpect(jsonPath("$.data.object_type.department_scoped").value(false))
            .andExpect(jsonPath("$.data.audit_id").value(301));

        mvc.perform(post("/api/mdm/object-types/person/deactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.object_type.status").value("inactive"));
        verify(audits, times(2)).save(any());
    }

    @Test
    void objectTypeManagementDoesNotRevealAnotherSystemObject() throws Exception {
        given(objectTypes.findBySystemIdAndCode(10L, "foreign")).willReturn(Optional.empty());
        mvc.perform(patch("/api/mdm/object-types/foreign").contentType(APPLICATION_JSON).content("""
                {"name":"外部对象","approval_required":false,"department_scoped":true}
                """))
            .andExpect(status().isNotFound());
    }

    @Test
    void usedFieldCannotChangeDataTypeOrUniqueConstraint() {
        ObjectType person = objectType(100L, 10L);
        FieldDefinition field = FieldDefinition.create(100L, person,
            new com.simplemdm.service.mdm.CreateFieldCommand("employee_code", "Employee code", FieldDataType.STRING,
                false, false, false, false, null, null, null, null, null, null, 0), null);
        ReflectionTestUtils.setField(field, "id", 200L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(fields.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(field));
        given(recordValues.findActiveByFieldDefinitionId(200L))
            .willReturn(List.of(mock(com.simplemdm.model.mdm.RecordValue.class)));
        MetadataCommands.UpdateField typeChange = new MetadataCommands.UpdateField(
            "Employee code", FieldDataType.INTEGER, false, false, false, null, null, null, null, null, null, 0);
        MetadataCommands.UpdateField uniqueChange = new MetadataCommands.UpdateField(
            "Employee code", FieldDataType.STRING, false, true, false, null, null, null, null, null, null, 0);

        assertThatThrownBy(() -> service.updateMasterField(currentUser(), "person", 200L, typeChange))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> service.updateMasterField(currentUser(), "person", 200L, uniqueChange))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(409);
    }

    @Test
    void rejectsInvalidTypeSpecificMetadataAndForeignReference() {
        ObjectType person = objectType(100L, 10L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        assertThatThrownBy(() -> service.createMasterField(currentUser(), "person", new MetadataCommands.CreateField(
            "organization", "Organization", FieldDataType.REFERENCE, false, false, false,
            null, null, null, null, null, null, 0)))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(400);
        assertThatThrownBy(() -> service.createMasterField(currentUser(), "person", new MetadataCommands.CreateField(
            "start_date", "Start date", FieldDataType.DATE, false, false, false,
            12, null, null, null, null, null, 0)))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(400);
        given(objectTypes.findBySystemIdAndId(10L, 900L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.createMasterField(currentUser(), "person", new MetadataCommands.CreateField(
            "foreign_reference", "Foreign reference", FieldDataType.REFERENCE, false, false, false,
            null, null, null, 900L, null, null, 0)))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(404);
    }

    @Test
    void auditSnapshotContainsEveryMutableMasterFieldProperty() {
        ObjectType person = objectType(100L, 10L);
        FieldDefinition field = FieldDefinition.create(100L, person,
            new com.simplemdm.service.mdm.CreateFieldCommand("amount", "Old name", FieldDataType.DECIMAL,
                true, false, true, false, null, 12, 2, null, "old-default", "old-rule", 1), null);
        ReflectionTestUtils.setField(field, "id", 200L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(fields.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(field));
        given(recordValues.findActiveByFieldDefinitionId(200L)).willReturn(List.of());
        given(fields.save(any(FieldDefinition.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(audits.save(any())).willAnswer(invocation -> {
            Object audit = invocation.getArgument(0); ReflectionTestUtils.setField(audit, "id", 301L); return audit;
        });
        service.updateMasterField(currentUser(), "person", 200L, new MetadataCommands.UpdateField(
            "New name", FieldDataType.DECIMAL, false, false, false, null, 18, 4, null,
            "new-default", "new-rule", 9));
        var audit = org.mockito.ArgumentCaptor.forClass(com.simplemdm.model.mdm.MetadataAudit.class);
        verify(audits, times(1)).save(audit.capture());
        String before = audit.getValue().getBeforeSnapshot();
        String after = audit.getValue().getAfterSnapshot();
        for (String property : List.of("field_key", "field_name", "data_type", "required", "unique_value",
            "searchable", "max_length", "precision_value", "scale_value", "reference_object_type_id",
            "default_value", "validation_rule", "sort_order", "status")) {
            org.assertj.core.api.Assertions.assertThat(before).contains(property);
            org.assertj.core.api.Assertions.assertThat(after).contains(property);
        }
        org.assertj.core.api.Assertions.assertThat(after).contains("New name", "new-default", "new-rule", "18", "4", "9");
    }

    @Test
    void auditSnapshotIsReversibleJsonForSpecialCharacterValues() throws Exception {
        ObjectType person = objectType(100L, 10L);
        FieldDefinition field = FieldDefinition.create(100L, person,
            new com.simplemdm.service.mdm.CreateFieldCommand("note", "Old,=\nname", FieldDataType.STRING,
                false, false, true, false, 20, null, null, null, "old,=\nvalue", "old,=\nrule", 1), null);
        ReflectionTestUtils.setField(field, "id", 201L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(fields.findBySystemIdAndId(10L, 201L)).willReturn(Optional.of(field));
        given(recordValues.findActiveByFieldDefinitionId(201L)).willReturn(List.of());
        given(fields.save(any(FieldDefinition.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(audits.save(any())).willAnswer(invocation -> {
            Object audit = invocation.getArgument(0); ReflectionTestUtils.setField(audit, "id", 302L); return audit;
        });
        service.updateMasterField(currentUser(), "person", 201L, new MetadataCommands.UpdateField(
            "New,=\nname", FieldDataType.STRING, false, false, true, 30, null, null, null,
            "new,=\nvalue", "new,=\nrule", 2));
        var audit = org.mockito.ArgumentCaptor.forClass(com.simplemdm.model.mdm.MetadataAudit.class);
        verify(audits).save(audit.capture());
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var before = json.readTree(audit.getValue().getBeforeSnapshot());
        var after = json.readTree(audit.getValue().getAfterSnapshot());
        org.assertj.core.api.Assertions.assertThat(before.get("field_name").asText()).isEqualTo("Old,=\nname");
        org.assertj.core.api.Assertions.assertThat(before.get("default_value").asText()).isEqualTo("old,=\nvalue");
        org.assertj.core.api.Assertions.assertThat(before.get("validation_rule").asText()).isEqualTo("old,=\nrule");
        org.assertj.core.api.Assertions.assertThat(after.get("field_name").asText()).isEqualTo("New,=\nname");
        org.assertj.core.api.Assertions.assertThat(after.get("default_value").asText()).isEqualTo("new,=\nvalue");
        org.assertj.core.api.Assertions.assertThat(after.get("validation_rule").asText()).isEqualTo("new,=\nrule");
    }

    private User currentUser() { return JwtInterceptor.CURRENT_USER.get(); }

    private static ObjectType objectType(long id, long systemId) {
        ObjectType type = ObjectType.create(systemId, "person", "Person");
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }
}
