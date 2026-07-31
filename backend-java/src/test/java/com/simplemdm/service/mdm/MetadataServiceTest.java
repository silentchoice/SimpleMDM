package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

    @Mock
    private ObjectTypeRepository objectTypes;
    @Mock
    private FieldDefinitionRepository fields;

    private MetadataService service;
    private ObjectType person;

    @BeforeEach
    void setUp() {
        service = new MetadataService(objectTypes, fields);
        person = ObjectType.create(10L, "PERSON", "Person");
        given(objectTypes.findById(100L)).willReturn(Optional.of(person));
    }

    @Test
    void createsAFieldForThePersistedObjectType() {
        given(fields.existsByObjectTypeIdAndFieldKey(100L, "employee_code")).willReturn(false);
        given(fields.save(any(FieldDefinition.class))).willAnswer(invocation -> invocation.getArgument(0));

        FieldDefinition created = service.createField(100L, command("employee_code", FieldDataType.STRING));

        assertThat(created.getObjectTypeId()).isEqualTo(100L);
        assertThat(created.getFieldKey()).isEqualTo("employee_code");
        verify(fields).save(created);
    }

    @Test
    void rejectsDuplicateFieldKeyWithinObject() {
        given(fields.existsByObjectTypeIdAndFieldKey(100L, "employee_code")).willReturn(true);

        assertThatThrownBy(() -> service.createField(100L, command("employee_code", FieldDataType.STRING)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("field key");
    }

    @Test
    void rejectsReferenceTypesFromAnotherSystem() {
        ObjectType foreignReference = ObjectType.create(20L, "ORG", "Organization");
        given(fields.existsByObjectTypeIdAndFieldKey(100L, "organization")).willReturn(false);
        given(objectTypes.findById(200L)).willReturn(Optional.of(foreignReference));

        assertThatThrownBy(() -> service.createField(100L, new CreateFieldCommand(
            "organization", "Organization", FieldDataType.REFERENCE, false, false, false, false,
            null, null, null, 200L, null, null, 0)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("same system");
    }

    @Test
    void rejectsConstraintsThatDoNotApplyToTheDeclaredType() {


        assertThatThrownBy(() -> service.createField(100L, new CreateFieldCommand(
            "start_date", "Start date", FieldDataType.DATE, false, false, false, false,
            10, null, null, null, null, null, 0)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Max length");
    }
    private CreateFieldCommand command(String fieldKey, FieldDataType dataType) {
        return new CreateFieldCommand(fieldKey, "Employee code", dataType, false, true, true, false,
            32, null, null, null, null, null, 0);
    }
}
