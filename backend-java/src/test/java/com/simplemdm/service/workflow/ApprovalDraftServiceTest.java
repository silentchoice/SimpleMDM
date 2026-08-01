package com.simplemdm.service.workflow;

import com.simplemdm.dto.mdm.MasterChildChangeRequest;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalChildChange;
import com.simplemdm.model.workflow.ApprovalChildValueChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.ApprovalActionRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.service.mdm.CurrentUserProvider;
import com.simplemdm.service.mdm.TypedValueConverter;
import com.simplemdm.service.system.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalDraftServiceTest {
    private final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    private final ApprovalChangeRepository changes = mock(ApprovalChangeRepository.class);
    private final ApprovalChildChangeRepository childChanges = mock(ApprovalChildChangeRepository.class);
    private final ApprovalChildValueChangeRepository childValueChanges = mock(ApprovalChildValueChangeRepository.class);
    private final ApprovalActionRepository actions = mock(ApprovalActionRepository.class);
    private final ObjectTypeRepository objectTypes = mock(ObjectTypeRepository.class);
    private final ChildTypeRepository childTypes = mock(ChildTypeRepository.class);
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final ChildFieldDefinitionRepository childFields = mock(ChildFieldDefinitionRepository.class);
    private final MdmRecordRepository records = mock(MdmRecordRepository.class);
    private final RecordValueRepository values = mock(RecordValueRepository.class);
    private final ChildRecordRepository childRecords = mock(ChildRecordRepository.class);
    private final ChildRecordValueRepository childValues = mock(ChildRecordValueRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private ApprovalDraftService service;

    private final ObjectType person = mock(ObjectType.class);
    private final Department department = mock(Department.class);
    private final User editor = mock(User.class);

    @BeforeEach
    void setUp() {
        service = new ApprovalDraftService(requests, changes, childChanges, childValueChanges, actions,
            objectTypes, childTypes, fields, childFields, records, values, childRecords, childValues,
            departments, authorization, new TypedValueConverter(), currentUser, users);
        when(currentUser.currentSystemUserId()).thenReturn(Optional.of(12L));
        when(users.findById(12L)).thenReturn(Optional.of(editor));
        when(editor.isActive()).thenReturn(true);
        when(editor.isSystemActive()).thenReturn(true);
        when(editor.getSystemId()).thenReturn(7L);
        when(person.getId()).thenReturn(8L);
        when(person.getSystemId()).thenReturn(7L);
        when(person.isActive()).thenReturn(true);
        when(objectTypes.findBySystemIdAndCode(7L, "person")).thenReturn(Optional.of(person));
        when(departments.findActiveBySystemIdAndId(7L, 9L)).thenReturn(Optional.of(department));
        when(authorization.can(12L, "MDM_RECORD_EDIT", 9L)).thenReturn(true);
        when(requests.save(any())).thenAnswer(invocation -> {
            ApprovalRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 100L);
            return request;
        });
        AtomicLong childChangeIds = new AtomicLong(200L);
        when(childChanges.save(any())).thenAnswer(invocation -> {
            ApprovalChildChange change = invocation.getArgument(0);
            ReflectionTestUtils.setField(change, "id", childChangeIds.getAndIncrement());
            return change;
        });
    }

    @Test
    void createStoresTypedMasterAndChildDraftWithoutWritingEffectiveTables() {
        FieldDefinition name = masterField(51L, "name", FieldDataType.STRING, true);
        ChildType phone = childType(61L, "phone");
        ChildFieldDefinition number = childField(71L, phone, "number", FieldDataType.STRING, true);
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(name));
        when(childTypes.findBySystemIdAndObjectTypeIdAndCode(7L, 8L, "phone")).thenReturn(Optional.of(phone));
        when(childFields.findByChildTypeId(61L)).thenReturn(List.of(number));

        Long id = service.submit(new MasterChildChangeRequest(
            MasterChildChangeRequest.Operation.CREATE, "person", null, null, "EMP-001", 9L,
            Map.of("name", "Alice"), List.of(new MasterChildChangeRequest.ChildGroup("phone", List.of(
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.CREATE,
                    null, null, Map.of("number", "123")))))), 12L);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requests).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getOperation()).isEqualTo(ApprovalRequest.Operation.CREATE);
        assertThat(requestCaptor.getValue().getRecordId()).isNull();
        assertThat(requestCaptor.getValue().getDepartmentId()).isEqualTo(9L);
        ArgumentCaptor<List<ApprovalChange>> masterCaptor = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(masterCaptor.capture());
        assertThat(masterCaptor.getValue()).singleElement().satisfies(change -> {
            assertThat(change.oldValue()).isEqualTo(TypedValue.empty());
            assertThat(change.newValue().stringValue()).isEqualTo("Alice");
        });
        ArgumentCaptor<List<ApprovalChildValueChange>> childValueCaptor = ArgumentCaptor.forClass(List.class);
        verify(childValueChanges).saveAll(childValueCaptor.capture());
        assertThat(childValueCaptor.getValue()).singleElement().satisfies(change -> {
            assertThat(change.oldValue()).isEqualTo(TypedValue.empty());
            assertThat(change.newValue().stringValue()).isEqualTo("123");
        });
        verify(records, never()).save(any());
        verify(values, never()).saveAll(any());
        verify(childRecords, never()).save(any());
        verify(childValues, never()).saveAll(any());
    }

    @Test
    void softDeletedChildValueDoesNotBlockUniqueChildCreateDraftButActiveValueDoes() {
        FieldDefinition name = masterField(51L, "name", FieldDataType.STRING, true);
        ChildType phone = childType(61L, "phone");
        ChildFieldDefinition number = childField(71L, phone, "number", FieldDataType.STRING, true);
        when(number.isUniqueValue()).thenReturn(true);
        ChildRecordValue deletedValue = childValue(81L, 71L, typedString("123"));
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(name));
        when(childTypes.findBySystemIdAndObjectTypeIdAndCode(7L, 8L, "phone")).thenReturn(Optional.of(phone));
        when(childFields.findByChildTypeId(61L)).thenReturn(List.of(number));
        when(childValues.findByFieldDefinitionId(71L)).thenReturn(List.of(deletedValue));
        when(childValues.findActiveByFieldDefinitionId(71L)).thenReturn(List.of());

        service.submit(createWithPhone("EMP-DELETED-UNIQUE", "123"), 12L);

        verify(childValues).findActiveByFieldDefinitionId(71L);

        ChildRecordValue activeValue = childValue(82L, 71L, typedString("456"));
        when(childValues.findActiveByFieldDefinitionId(71L)).thenReturn(List.of(activeValue));
        assertThatThrownBy(() -> service.submit(createWithPhone("EMP-ACTIVE-UNIQUE", "456"), 12L))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void updateStoresChangedMasterAndChildCreateUpdateDeleteInOneRequest() {
        MdmRecord record = record(41L, 3L);
        FieldDefinition name = masterField(51L, "name", FieldDataType.STRING, true);
        RecordValue oldName = recordValue(51L, typedString("Alice"));
        ChildType phone = childType(61L, "phone");
        ChildFieldDefinition number = childField(71L, phone, "number", FieldDataType.STRING, true);
        ChildRecord updated = childRecord(81L, 41L, 61L, 2L);
        ChildRecord deleted = childRecord(82L, 41L, 61L, 4L);
        ChildRecordValue oldNumber = childValue(81L, 71L, typedString("123"));
        ChildRecordValue deletedNumber = childValue(82L, 71L, typedString("456"));
        when(records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(7L, 8L, 41L))
            .thenReturn(Optional.of(record));
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(name));
        when(values.findByRecordId(41L)).thenReturn(List.of(oldName));
        when(childTypes.findBySystemIdAndObjectTypeIdAndCode(7L, 8L, "phone")).thenReturn(Optional.of(phone));
        when(childFields.findByChildTypeId(61L)).thenReturn(List.of(number));
        when(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(7L, 41L, 61L, 81L))
            .thenReturn(Optional.of(updated));
        when(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(7L, 41L, 61L, 82L))
            .thenReturn(Optional.of(deleted));
        when(childValues.findByChildRecordIdIn(List.of(81L))).thenReturn(List.of(oldNumber));
        when(childValues.findByChildRecordIdIn(List.of(82L))).thenReturn(List.of(deletedNumber));

        service.submit(new MasterChildChangeRequest(
            MasterChildChangeRequest.Operation.UPDATE, "person", 41L, 3L, "EMP-001", 9L,
            Map.of("name", "Alicia"), List.of(new MasterChildChangeRequest.ChildGroup("phone", List.of(
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.CREATE,
                    null, null, Map.of("number", "456")),
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.UPDATE,
                    81L, 2L, Map.of("number", "789")),
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.DELETE,
                    82L, 4L, Map.of()))))), 12L);

        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requests).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getOperation()).isEqualTo(ApprovalRequest.Operation.UPDATE);
        assertThat(requestCaptor.getValue().getRecordId()).isEqualTo(41L);
        assertThat(requestCaptor.getValue().getDepartmentId()).isEqualTo(9L);
        ArgumentCaptor<ApprovalChildChange> childCaptor = ArgumentCaptor.forClass(ApprovalChildChange.class);
        verify(childChanges, org.mockito.Mockito.times(3)).save(childCaptor.capture());
        assertThat(childCaptor.getAllValues()).extracting(ApprovalChildChange::getOperation)
            .containsExactly(ApprovalChildChange.Operation.CREATE, ApprovalChildChange.Operation.UPDATE,
                ApprovalChildChange.Operation.DELETE);
        ArgumentCaptor<List<ApprovalChildValueChange>> valueCaptor = ArgumentCaptor.forClass(List.class);
        verify(childValueChanges, org.mockito.Mockito.times(3)).saveAll(valueCaptor.capture());
        assertThat(valueCaptor.getAllValues().get(2)).singleElement().satisfies(value -> {
            assertThat(value.oldValue()).isEqualTo(typedString("456"));
            assertThat(value.newValue()).isEqualTo(TypedValue.empty());
        });
    }

    @Test
    void updateMayPopulateAFieldAddedAfterTheRecordWithoutAnExistingValueRow() {
        MdmRecord record = record(41L, 3L);
        FieldDefinition name = masterField(51L, "name", FieldDataType.STRING, true);
        FieldDefinition newlyRequired = masterField(52L, "employee_level", FieldDataType.STRING, true);
        RecordValue existingName = recordValue(51L, typedString("Alice"));
        when(records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(7L, 8L, 41L))
            .thenReturn(Optional.of(record));
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(name, newlyRequired));
        when(values.findByRecordId(41L)).thenReturn(List.of(existingName));

        service.submit(updateRequest(3L, Map.of("employee_level", "L3"), List.of()), 12L);

        ArgumentCaptor<List<ApprovalChange>> captor = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(change -> {
            assertThat(change.getFieldDefinitionId()).isEqualTo(52L);
            assertThat(change.oldValue()).isEqualTo(TypedValue.empty());
            assertThat(change.newValue()).isEqualTo(typedString("L3"));
        });
    }

    @Test
    void rejectsUnknownMasterOrChildFieldsBeforeCreatingRequest() {
        FieldDefinition name = masterField(51L, "name", FieldDataType.STRING, true);
        ChildType phone = childType(61L, "phone");
        ChildFieldDefinition number = childField(71L, phone, "number", FieldDataType.STRING, true);
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(name));
        when(childTypes.findBySystemIdAndObjectTypeIdAndCode(7L, 8L, "phone")).thenReturn(Optional.of(phone));
        when(childFields.findByChildTypeId(61L)).thenReturn(List.of(number));

        assertThatThrownBy(() -> service.submit(new MasterChildChangeRequest(
            MasterChildChangeRequest.Operation.CREATE, "person", null, null, "EMP-001", 9L,
            Map.of("unknown", "value"), List.of()), 12L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Unknown");
        assertThatThrownBy(() -> service.submit(new MasterChildChangeRequest(
            MasterChildChangeRequest.Operation.CREATE, "person", null, null, "EMP-001", 9L,
            Map.of("name", "Alice"), List.of(new MasterChildChangeRequest.ChildGroup("phone", List.of(
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.CREATE,
                    null, null, Map.of("unknown", "value")))))), 12L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Unknown");

        verifyNoInteractions(requests);
    }

    @Test
    void rejectsUnchangedUpdateAndStaleMasterOrChildVersions() {
        MdmRecord record = record(41L, 3L);
        FieldDefinition name = masterField(51L, "name", FieldDataType.STRING, true);
        when(records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(7L, 8L, 41L))
            .thenReturn(Optional.of(record));
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(name));
        RecordValue oldName = recordValue(51L, typedString("Alice"));
        when(values.findByRecordId(41L)).thenReturn(List.of(oldName));

        assertThatThrownBy(() -> service.submit(updateRequest(3L, Map.of("name", "Alice"), List.of()), 12L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("changed");
        assertThatThrownBy(() -> service.submit(updateRequest(2L, Map.of("name", "Alicia"), List.of()), 12L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("version");

        ChildType phone = childType(61L, "phone");
        ChildRecord child = childRecord(81L, 41L, 61L, 4L);
        when(childTypes.findBySystemIdAndObjectTypeIdAndCode(7L, 8L, "phone")).thenReturn(Optional.of(phone));
        when(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(7L, 41L, 61L, 81L))
            .thenReturn(Optional.of(child));
        assertThatThrownBy(() -> service.submit(updateRequest(3L, Map.of(), List.of(
            new MasterChildChangeRequest.ChildGroup("phone", List.of(
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.DELETE,
                    81L, 3L, Map.of()))))), 12L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("version");

        verifyNoInteractions(requests);
    }

    @Test
    void rejectsSpoofedActorAndDepartmentDifferentFromExistingParent() {
        assertThatThrownBy(() -> service.submit(new MasterChildChangeRequest(
            MasterChildChangeRequest.Operation.CREATE, "person", null, null, "EMP-001", 9L,
            Map.of(), List.of()), 99L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("authenticated");
        MdmRecord record = record(41L, 3L);
        when(record.getDepartmentId()).thenReturn(10L);
        when(records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(7L, 8L, 41L))
            .thenReturn(Optional.of(record));
        assertThatThrownBy(() -> service.submit(updateRequest(3L, Map.of(), List.of()), 12L))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(404));
        verifyNoInteractions(requests);
    }

    @Test
    void rejectsDuplicateExistingChildTargetWithinOneRequest() {
        MdmRecord record = record(41L, 3L);
        ChildType phone = childType(61L, "phone");
        ChildFieldDefinition number = childField(71L, phone, "number", FieldDataType.STRING, true);
        ChildRecord child = childRecord(81L, 41L, 61L, 2L);
        ChildRecordValue oldNumber = childValue(81L, 71L, typedString("123"));
        when(records.findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(7L, 8L, 41L))
            .thenReturn(Optional.of(record));
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of());
        when(childTypes.findBySystemIdAndObjectTypeIdAndCode(7L, 8L, "phone")).thenReturn(Optional.of(phone));
        when(childFields.findByChildTypeId(61L)).thenReturn(List.of(number));
        when(childRecords.findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(7L, 41L, 61L, 81L))
            .thenReturn(Optional.of(child));
        when(childValues.findByChildRecordIdIn(List.of(81L))).thenReturn(List.of(oldNumber));

        assertThatThrownBy(() -> service.submit(updateRequest(3L, Map.of(), List.of(
            new MasterChildChangeRequest.ChildGroup("phone", List.of(
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.UPDATE,
                    81L, 2L, Map.of("number", "456")),
                new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.DELETE,
                    81L, 2L, Map.of()))))), 12L))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getCode()).isEqualTo(400);
                assertThat(error.getMessage()).containsIgnoringCase("duplicate");
            });

        verifyNoInteractions(requests);
    }

    @Test
    void crossSystemReferenceIsUniformNotFoundAndDoesNotExposeItsContext() {
        FieldDefinition manager = masterField(51L, "manager", FieldDataType.REFERENCE, false);
        when(manager.getReferenceObjectTypeId()).thenReturn(99L);
        when(fields.findByObjectTypeId(8L)).thenReturn(List.of(manager));
        MdmRecord foreign = mock(MdmRecord.class);
        when(foreign.getSystemId()).thenReturn(77L);
        when(foreign.getObjectTypeId()).thenReturn(99L);
        when(records.findById(500L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.submit(new MasterChildChangeRequest(
            MasterChildChangeRequest.Operation.CREATE, "person", null, null, "EMP-REF", 9L,
            Map.of("manager", new TypedValueConverter.ReferenceValue(500L, 99L, 7L)), List.of()), 12L))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(404));

        verifyNoInteractions(requests);
    }

    private MasterChildChangeRequest updateRequest(Long version, Map<String, Object> data,
                                                   List<MasterChildChangeRequest.ChildGroup> children) {
        return new MasterChildChangeRequest(MasterChildChangeRequest.Operation.UPDATE, "person", 41L,
            version, "EMP-001", 9L, data, children);
    }

    private MasterChildChangeRequest createWithPhone(String recordCode, String number) {
        return new MasterChildChangeRequest(MasterChildChangeRequest.Operation.CREATE, "person", null, null,
            recordCode, 9L, Map.of("name", "Alice"), List.of(new MasterChildChangeRequest.ChildGroup("phone",
            List.of(new MasterChildChangeRequest.ChildRow(MasterChildChangeRequest.ChildOperation.CREATE,
                null, null, Map.of("number", number))))));
    }

    private MdmRecord record(Long id, long version) {
        MdmRecord record = mock(MdmRecord.class);
        when(record.getId()).thenReturn(id);
        when(record.getSystemId()).thenReturn(7L);
        when(record.getObjectTypeId()).thenReturn(8L);
        when(record.getDepartmentId()).thenReturn(9L);
        when(record.getRecordCode()).thenReturn("EMP-001");
        when(record.getVersion()).thenReturn(version);
        return record;
    }

    private FieldDefinition masterField(Long id, String key, FieldDataType type, boolean required) {
        FieldDefinition field = mock(FieldDefinition.class);
        when(field.getId()).thenReturn(id);
        when(field.getSystemId()).thenReturn(7L);
        when(field.getObjectTypeId()).thenReturn(8L);
        when(field.getFieldKey()).thenReturn(key);
        when(field.getDataType()).thenReturn(type);
        when(field.isRequired()).thenReturn(required);
        when(field.getMaxLength()).thenReturn(128);
        when(field.getStatus()).thenReturn("active");
        return field;
    }

    private ChildType childType(Long id, String code) {
        ChildType type = mock(ChildType.class);
        when(type.getId()).thenReturn(id);
        when(type.getSystemId()).thenReturn(7L);
        when(type.getObjectTypeId()).thenReturn(8L);
        when(type.getCode()).thenReturn(code);
        when(type.getStatus()).thenReturn("active");
        return type;
    }

    private ChildFieldDefinition childField(Long id, ChildType type, String key, FieldDataType dataType,
                                            boolean required) {
        ChildFieldDefinition field = mock(ChildFieldDefinition.class);
        Long childTypeId = type.getId();
        when(field.getId()).thenReturn(id);
        when(field.getSystemId()).thenReturn(7L);
        when(field.getChildTypeId()).thenReturn(childTypeId);
        when(field.getFieldKey()).thenReturn(key);
        when(field.getDataType()).thenReturn(dataType);
        when(field.isRequired()).thenReturn(required);
        when(field.getMaxLength()).thenReturn(128);
        when(field.getStatus()).thenReturn("active");
        return field;
    }

    private RecordValue recordValue(Long fieldId, TypedValue value) {
        RecordValue row = mock(RecordValue.class);
        when(row.getFieldDefinitionId()).thenReturn(fieldId);
        when(row.typedValue()).thenReturn(value);
        return row;
    }

    private ChildRecord childRecord(Long id, Long parentId, Long typeId, long version) {
        ChildRecord child = mock(ChildRecord.class);
        when(child.getId()).thenReturn(id);
        when(child.getSystemId()).thenReturn(7L);
        when(child.getRecordId()).thenReturn(parentId);
        when(child.getChildTypeId()).thenReturn(typeId);
        when(child.getVersion()).thenReturn(version);
        return child;
    }

    private ChildRecordValue childValue(Long childId, Long fieldId, TypedValue value) {
        ChildRecordValue row = mock(ChildRecordValue.class);
        when(row.getChildRecordId()).thenReturn(childId);
        when(row.getFieldDefinitionId()).thenReturn(fieldId);
        when(row.typedValue()).thenReturn(value);
        return row;
    }

    private TypedValue typedString(String value) {
        return new TypedValue(value, null, null, null, null, null, null, null);
    }
}
