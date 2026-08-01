package com.simplemdm.service.mdm;

import com.simplemdm.dto.mdm.MetadataCommands;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.*;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.*;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class MetadataManagementServiceTest {
    private ObjectTypeRepository objectTypes;
    private FieldDefinitionRepository masterFields;
    private RecordValueRepository masterValues;
    private ChildTypeRepository childTypes;
    private ChildFieldDefinitionRepository childFields;
    private ChildRecordValueRepository childValues;
    private MetadataAuditRepository audits;
    private MetadataManagementService service;
    private User user;
    private ObjectType person;

    @BeforeEach
    void setUp() {
        objectTypes = mock(ObjectTypeRepository.class);
        masterFields = mock(FieldDefinitionRepository.class);
        masterValues = mock(RecordValueRepository.class);
        childTypes = mock(ChildTypeRepository.class);
        childFields = mock(ChildFieldDefinitionRepository.class);
        childValues = mock(ChildRecordValueRepository.class);
        audits = mock(MetadataAuditRepository.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        service = new MetadataManagementService(objectTypes, masterFields, masterValues, childTypes, childFields,
            childValues, audits, authorization, new MetadataService(objectTypes, masterFields));
        user = mock(User.class);
        given(user.getId()).willReturn(7L);
        given(user.getSystemId()).willReturn(10L);
        given(user.getDepartmentId()).willReturn(20L);
        given(authorization.can(7L, "MDM_FIELD_MANAGE", 20L)).willReturn(true);
        person = objectType(100L, 10L);
        given(objectTypes.findBySystemIdAndCode(10L, "person")).willReturn(Optional.of(person));
        given(audits.save(any())).willAnswer(invocation -> {
            MetadataAudit audit = invocation.getArgument(0);
            ReflectionTestUtils.setField(audit, "id", 300L);
            return audit;
        });
    }

    @Test
    void createsChildTypeAndChildFieldWithSharedFlagAndAudit() {
        given(childTypes.findBySystemIdAndObjectTypeIdAndCode(10L, 100L, "job")).willReturn(Optional.empty());
        given(childTypes.save(any())).willAnswer(invocation -> {
            ChildType type = invocation.getArgument(0);
            ReflectionTestUtils.setField(type, "id", 200L);
            return type;
        });
        var type = service.createChildType(user, "person", new MetadataCommands.CreateChildType("job", "Job", 3));
        assertThat(type.childType().getCode()).isEqualTo("job");
        assertThat(type.auditId()).isEqualTo(300L);

        ChildType job = childType(200L, person, "job");
        given(childTypes.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(job));
        given(childFields.existsByChildTypeIdAndFieldKey(200L, "title")).willReturn(false);
        given(childFields.save(any())).willAnswer(invocation -> {
            ChildFieldDefinition field = invocation.getArgument(0);
            ReflectionTestUtils.setField(field, "id", 201L);
            return field;
        });
        var field = service.createChildField(user, "person", 200L, new MetadataCommands.CreateChildField(
            "title", "Title", FieldDataType.STRING, true, false, true, true,
            64, null, null, null, null, null, 4));
        assertThat(field.field().isShared()).isTrue();
        assertThat(field.auditId()).isEqualTo(300L);
        verify(audits, times(2)).save(any());
    }

    @Test
    void changesSharedFlagForUsedChildFieldButNotTypeOrUniqueConstraint() {
        ChildType job = childType(200L, person, "job");
        ChildFieldDefinition title = childField(201L, job, "title", FieldDataType.STRING, false, false);
        given(childTypes.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(job));
        given(childFields.findBySystemIdAndId(10L, 201L)).willReturn(Optional.of(title));
        given(childValues.findActiveByFieldDefinitionId(201L)).willReturn(List.of(mock(ChildRecordValue.class)));
        given(childFields.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateChildField(user, "person", 200L, 201L, new MetadataCommands.UpdateChildField(
            "Position title", FieldDataType.STRING, false, false, true, true,
            64, null, null, null, null, null, 2));
        assertThat(updated.field().isShared()).isTrue();
        assertThat(updated.field().getFieldName()).isEqualTo("Position title");

        assertThatThrownBy(() -> service.updateChildField(user, "person", 200L, 201L,
            new MetadataCommands.UpdateChildField("Position title", FieldDataType.INTEGER, false, false, true, true,
                null, null, null, null, null, null, 2)))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> service.updateChildField(user, "person", 200L, 201L,
            new MetadataCommands.UpdateChildField("Position title", FieldDataType.STRING, false, true, true, true,
                64, null, null, null, null, null, 2)))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(409);
    }

    @Test
    void rejectsEveryValueSemanticChangeForAUsedMasterField() {
        FieldDefinition name = masterField(101L, person, new CreateFieldCommand(
            "name", "Name", FieldDataType.STRING, false, false, true, false,
            64, null, null, null, null, "[A-Z]+", 1));
        given(masterFields.findBySystemIdAndId(10L, 101L)).willReturn(Optional.of(name));
        given(masterValues.findActiveByFieldDefinitionId(101L)).willReturn(List.of(mock(RecordValue.class)));
        given(masterFields.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        List<MetadataCommands.UpdateField> unsafe = List.of(
            updateMaster(true, false, 64, null, null, null, "[A-Z]+"),
            updateMaster(false, false, 32, null, null, null, "[A-Z]+"),
            updateMaster(false, false, 64, 10, 2, null, "[A-Z]+"),
            updateMaster(false, false, 64, null, null, 100L, "[A-Z]+"),
            updateMaster(false, false, 64, null, null, null, "[a-z]+")
        );

        for (MetadataCommands.UpdateField command : unsafe) {
            assertThatThrownBy(() -> service.updateMasterField(user, "person", 101L, command))
                .isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getCode()).isEqualTo(409));
        }
        verify(masterFields, never()).save(any());
    }

    @Test
    void rejectsWritesThroughInactiveObjectChildTypeAndField() {
        ReflectionTestUtils.setField(person, "status", "inactive");
        given(masterFields.save(any())).willAnswer(invocation -> {
            FieldDefinition value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 500L);
            return value;
        });
        assertThatThrownBy(() -> service.createMasterField(user, "person", new MetadataCommands.CreateField(
            "new_key", "New", FieldDataType.STRING, false, false, false,
            64, null, null, null, null, null, 0)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(404));

        ReflectionTestUtils.setField(person, "status", "active");
        ChildType inactiveType = childType(200L, person, "job");
        ReflectionTestUtils.setField(inactiveType, "status", "inactive");
        given(childTypes.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(inactiveType));
        assertThatThrownBy(() -> service.createChildField(user, "person", 200L,
            new MetadataCommands.CreateChildField("title", "Title", FieldDataType.STRING,
                false, false, false, false, 64, null, null, null, null, null, 0)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(404));

        ChildType activeType = childType(201L, person, "active-job");
        ChildFieldDefinition inactiveField = childField(202L, activeType, "title", FieldDataType.STRING, false, false);
        ReflectionTestUtils.setField(inactiveField, "status", "inactive");
        given(childTypes.findBySystemIdAndId(10L, 201L)).willReturn(Optional.of(activeType));
        given(childFields.findBySystemIdAndId(10L, 202L)).willReturn(Optional.of(inactiveField));
        assertThatThrownBy(() -> service.updateChildField(user, "person", 201L, 202L,
            new MetadataCommands.UpdateChildField("Title", FieldDataType.STRING, false, false,
                false, false, 64, null, null, null, null, null, 0)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(404));
    }

    @Test
    void deactivatesChildMetadataAndOnlyActiveFieldsAreReturnedByProjectionQuery() {
        ChildType job = childType(200L, person, "job");
        ChildFieldDefinition title = childField(201L, job, "title", FieldDataType.STRING, false, true);
        given(childTypes.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(job));
        given(childFields.findBySystemIdAndId(10L, 201L)).willReturn(Optional.of(title));
        given(childFields.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var deactivated = service.deactivateChildField(user, "person", 200L, 201L);
        assertThat(deactivated.field().getStatus()).isEqualTo("inactive");
    }

    @Test
    void reactivatesInactiveMetadataUsingTheExistingFieldManagerAuthorization() {
        ReflectionTestUtils.setField(person, "status", "inactive");
        given(objectTypes.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        var objectMutation = service.reactivateObjectType(user, "person");
        assertThat(objectMutation.objectType().getStatus()).isEqualTo("active");

        FieldDefinition name = masterField(101L, person, new CreateFieldCommand(
            "name", "Name", FieldDataType.STRING, false, false, true, false,
            64, null, null, null, null, null, 1));
        ReflectionTestUtils.setField(name, "status", "inactive");
        given(masterFields.findBySystemIdAndId(10L, 101L)).willReturn(Optional.of(name));
        given(masterFields.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.reactivateMasterField(user, "person", 101L).field().getStatus()).isEqualTo("active");

        ChildType job = childType(200L, person, "job");
        ReflectionTestUtils.setField(job, "status", "inactive");
        given(childTypes.findBySystemIdAndId(10L, 200L)).willReturn(Optional.of(job));
        given(childTypes.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.reactivateChildType(user, "person", 200L).childType().getStatus()).isEqualTo("active");

        ChildFieldDefinition title = childField(201L, job, "title", FieldDataType.STRING, false, false);
        ReflectionTestUtils.setField(title, "status", "inactive");
        given(childFields.findBySystemIdAndId(10L, 201L)).willReturn(Optional.of(title));
        given(childFields.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.reactivateChildField(user, "person", 200L, 201L).field().getStatus()).isEqualTo("active");
        verify(audits, times(4)).save(any());
    }

    private static ObjectType objectType(long id, long systemId) {
        ObjectType type = ObjectType.create(systemId, "person", "Person");
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    private static ChildType childType(long id, ObjectType objectType, String code) {
        ChildType type = ChildType.create(objectType.getId(), objectType, code, "Job");
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    private static ChildFieldDefinition childField(long id, ChildType childType, String key, FieldDataType type,
                                                    boolean shared, boolean unique) {
        ChildFieldDefinition field = ChildFieldDefinition.create(childType.getId(), childType,
            new CreateFieldCommand(key, "Title", type, false, unique, true, shared,
                64, null, null, null, null, null, 1), null);
        ReflectionTestUtils.setField(field, "id", id);
        return field;
    }

    private static FieldDefinition masterField(long id, ObjectType objectType, CreateFieldCommand command) {
        FieldDefinition field = FieldDefinition.create(objectType.getId(), objectType, command, null);
        ReflectionTestUtils.setField(field, "id", id);
        return field;
    }

    private static MetadataCommands.UpdateField updateMaster(boolean required, boolean unique, Integer maxLength,
                                                              Integer precision, Integer scale, Long reference,
                                                              String validation) {
        return new MetadataCommands.UpdateField("Name", FieldDataType.STRING, required, unique, true,
            maxLength, precision, scale, reference, null, validation, 2);
    }
}
