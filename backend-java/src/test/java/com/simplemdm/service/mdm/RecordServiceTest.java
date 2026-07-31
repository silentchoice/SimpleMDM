package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordServiceTest {

    @Mock private MdmRecordRepository records;
    @Mock private RecordValueRepository values;
    @Mock private ObjectTypeRepository objectTypes;
    @Mock private FieldDefinitionRepository fields;
    @Mock private DepartmentRepository departments;
    @Mock private AuthorizationService authorization;
    @Mock private CurrentUserProvider currentUser;
    @Mock private ChildRecordRepository childRecords;
    @Mock private ChildRecordValueRepository childValues;
    @Mock private ChildTypeRepository childTypes;
    @Mock private ChildFieldDefinitionRepository childFields;

    private RecordService service;
    private ObjectType person;
    private Department department;

    @BeforeEach
    void setUp() {
        service = new RecordService(records, values, objectTypes, fields, departments, authorization,
            new TypedValueConverter(), currentUser, childRecords, childValues, childTypes, childFields);
        person = ObjectType.create(10L, "PERSON", "Person");
        department = department(10L, 51L);
        given(objectTypes.findById(100L)).willReturn(Optional.of(person));
        given(departments.findById(51L)).willReturn(Optional.of(department));
        given(fields.findByObjectTypeId(100L)).willReturn(List.of(
            field("employee_code", FieldDataType.STRING, true),
            field("age", FieldDataType.INTEGER, false),
            field("active", FieldDataType.BOOLEAN, false)));
        given(records.saveAndFlush(any(MdmRecord.class))).willAnswer(invocation -> {
            MdmRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 900L);
            return record;
        });
        given(authorization.can(7L, "MDM_RECORD_EDIT", 51L)).willReturn(true);
    }

    @Test
    void persistsOneTypedRowPerDefinedFieldAfterAuthorization() {
        RecordView created = service.createAs(7L, validPersonCommand());

        ArgumentCaptor<List<RecordValue>> rows = ArgumentCaptor.forClass(List.class);
        verify(values).saveAll(rows.capture());
        assertThat(created.id()).isEqualTo(900L);
        assertThat(rows.getValue()).hasSize(3).allSatisfy(value ->
            assertThat(value.nonNullValueCount()).isEqualTo(1));
        verify(authorization).can(7L, "MDM_RECORD_EDIT", 51L);
    }

    @Test
    void rejectsDepartmentFromAnotherSystemBeforeWritingRows() {
        Department foreignDepartment = department(20L, 51L);
        given(departments.findById(51L)).willReturn(Optional.of(foreignDepartment));

        assertThatThrownBy(() -> service.createAs(7L, validPersonCommand()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("same system");

        verify(records, never()).saveAndFlush(any());
        verify(values, never()).saveAll(any());
    }

    @Test
    void rejectsDeniedEditorWithoutWritingRows() {
        given(authorization.can(7L, "MDM_RECORD_EDIT", 51L)).willReturn(false);

        assertThatThrownBy(() -> service.createAs(7L, validPersonCommand()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("authorized");

        verify(records, never()).saveAndFlush(any());
        verify(values, never()).saveAll(any());
    }

    @Test
    void publicCreateFailsClosedWithoutAuthenticatedSystemUser() {
        given(currentUser.currentSystemUserId()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(validPersonCommand()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("authenticated system user");

        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStaleExpectedVersionBeforeChangingValues() {
        MdmRecord existing = MdmRecord.create(10L, person, 100L, department, "P-1", 7L);
        ReflectionTestUtils.setField(existing, "id", 900L);
        ReflectionTestUtils.setField(existing, "version", 4L);
        given(records.findById(900L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateAs(7L, 900L, 3L, Map.of("age", 35)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("version");

        verify(values, never()).saveAll(any());
    }

    @Test
    void childWriteUsesParentDepartmentAuthorizationAndWritesEveryChildField() {
        MdmRecord parent = MdmRecord.create(10L, person, 100L, department, "P-1", 7L);
        ReflectionTestUtils.setField(parent, "id", 900L);
        ChildType childType = ChildType.create(100L, person, "PHONE", "Phone");
        ReflectionTestUtils.setField(childType, "id", 200L);
        ChildFieldDefinition childField = ChildFieldDefinition.create(200L, childType,
            new CreateFieldCommand("number", "Number", FieldDataType.STRING, true, false, false, false,
                64, null, null, null, null, null, 0), null);
        ReflectionTestUtils.setField(childField, "id", 300L);
        given(records.findById(900L)).willReturn(Optional.of(parent));
        given(childTypes.findById(200L)).willReturn(Optional.of(childType));
        given(childFields.findByChildTypeId(200L)).willReturn(List.of(childField));
        given(childRecords.saveAndFlush(any(ChildRecord.class))).willAnswer(invocation -> {
            ChildRecord child = invocation.getArgument(0);
            ReflectionTestUtils.setField(child, "id", 901L);
            return child;
        });

        ChildRecordView created = service.createChildAs(7L,
            new CreateChildRecordCommand(900L, 200L, Map.of("number", "123")));

        ArgumentCaptor<List<ChildRecordValue>> rows = ArgumentCaptor.forClass(List.class);
        verify(childValues).saveAll(rows.capture());
        assertThat(created.systemId()).isEqualTo(10L);
        assertThat(created.departmentId()).isEqualTo(51L);
        assertThat(rows.getValue()).hasSize(1).allSatisfy(value -> assertThat(value.nonNullValueCount()).isEqualTo(1));
        verify(authorization).can(7L, "MDM_RECORD_EDIT", 51L);
    }
    @Test
    void childWriteFailsClosedWithoutAuthenticatedSystemUser() {
        given(currentUser.currentSystemUserId()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createChild(new CreateChildRecordCommand(900L, 200L, Map.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("authenticated system user");

        verify(childRecords, never()).saveAndFlush(any());
    }

    @Test
    void childWriteRejectsDeniedEditorBeforePersisting() {
        MdmRecord parent = parentRecord();
        ChildType childType = childType(person);
        given(records.findById(900L)).willReturn(Optional.of(parent));
        given(childTypes.findById(200L)).willReturn(Optional.of(childType));
        given(authorization.can(7L, "MDM_RECORD_EDIT", 51L)).willReturn(false);

        assertThatThrownBy(() -> service.createChildAs(7L, new CreateChildRecordCommand(900L, 200L, Map.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("authorized");

        verify(childRecords, never()).saveAndFlush(any());
        verify(childValues, never()).saveAll(any());
    }

    @Test
    void childWriteRollsBackBeforePersistingWhenAChildValueIsInvalid() {
        MdmRecord parent = parentRecord();
        ChildType childType = childType(person);
        ChildFieldDefinition childField = ChildFieldDefinition.create(200L, childType,
            new CreateFieldCommand("number", "Number", FieldDataType.STRING, true, false, false, false,
                64, null, null, null, null, null, 0), null);
        given(records.findById(900L)).willReturn(Optional.of(parent));
        given(childTypes.findById(200L)).willReturn(Optional.of(childType));
        given(childFields.findByChildTypeId(200L)).willReturn(List.of(childField));

        assertThatThrownBy(() -> service.createChildAs(7L, new CreateChildRecordCommand(900L, 200L, Map.of("number", 123))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("data type");

        verify(childRecords, never()).saveAndFlush(any());
        verify(childValues, never()).saveAll(any());
    }

    @Test
    void childWriteRejectsCrossSystemChildTypeBeforePersisting() {
        MdmRecord parent = parentRecord();
        ObjectType foreignObject = ObjectType.create(20L, "OTHER", "Other");
        ChildType foreignChildType = childType(foreignObject);
        given(records.findById(900L)).willReturn(Optional.of(parent));
        given(childTypes.findById(200L)).willReturn(Optional.of(foreignChildType));

        assertThatThrownBy(() -> service.createChildAs(7L, new CreateChildRecordCommand(900L, 200L, Map.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("parent record context");

        verify(childRecords, never()).saveAndFlush(any());
        verify(childValues, never()).saveAll(any());
    }

    private MdmRecord parentRecord() {
        MdmRecord parent = MdmRecord.create(10L, person, 100L, department, "P-1", 7L);
        ReflectionTestUtils.setField(parent, "id", 900L);
        return parent;
    }

    private ChildType childType(ObjectType objectType) {
        ChildType childType = ChildType.create(100L, objectType, "PHONE", "Phone");
        ReflectionTestUtils.setField(childType, "id", 200L);
        return childType;
    }
    private CreateRecordCommand validPersonCommand() {
        return new CreateRecordCommand(10L, 100L, 51L, "P-1", Map.of(
            "employee_code", "E-001", "age", 35, "active", true));
    }

    private FieldDefinition field(String key, FieldDataType type, boolean required) {
        return FieldDefinition.create(100L, person, new CreateFieldCommand(key, key, type, required,
            false, false, false, 64, null, null, null, null, null, 0), null);
    }

    private Department department(Long systemId, Long departmentId) {
        SystemEntity system = org.mockito.Mockito.mock(SystemEntity.class);
        given(system.getId()).willReturn(systemId);
        Department value = org.mockito.Mockito.mock(Department.class);
        given(value.getId()).willReturn(departmentId);
        given(value.getSystem()).willReturn(system);
        return value;
    }
}
