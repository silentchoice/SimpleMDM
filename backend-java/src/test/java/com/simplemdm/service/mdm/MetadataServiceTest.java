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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

    @Mock private ObjectTypeRepository objectTypes;
    @Mock private FieldDefinitionRepository fields;

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
        given(fields.saveAndFlush(any(FieldDefinition.class))).willAnswer(invocation -> invocation.getArgument(0));

        FieldDefinition created = service.createField(100L, command("employee_code", FieldDataType.STRING));

        assertThat(created.getObjectTypeId()).isEqualTo(100L);
        assertThat(created.getFieldKey()).isEqualTo("employee_code");
        verify(fields).saveAndFlush(created);
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

    @Test
    void rejectsDecimalMetadataOutsideV1PhysicalStorageLimits() {
        assertThatThrownBy(() -> service.createField(100L, new CreateFieldCommand(
            "too_wide", "Too wide", FieldDataType.DECIMAL, false, false, false, false,
            null, 39, 10, null, null, null, 0)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("38");
        assertThatThrownBy(() -> service.createField(100L, new CreateFieldCommand(
            "too_precise", "Too precise", FieldDataType.DECIMAL, false, false, false, false,
            null, 38, 11, null, null, null, 0)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("10");
    }

    @Test
    void translatesADatabaseDuplicateRaceToConflict() {
        given(fields.existsByObjectTypeIdAndFieldKey(100L, "employee_code")).willReturn(false);
        given(fields.saveAndFlush(any(FieldDefinition.class)))
            .willThrow(new DataIntegrityViolationException("uk_field_definition_key"));

        assertThatThrownBy(() -> service.createField(100L, command("employee_code", FieldDataType.STRING)))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getCode())
            .isEqualTo(409);
    }

    @Test
    void requiresDecimalPrecisionAndScaleToBeConfiguredTogether() {
        assertThatThrownBy(() -> service.createField(100L, new CreateFieldCommand(
            "precision_only", "Precision only", FieldDataType.DECIMAL, false, false, false, false,
            null, 5, null, null, null, null, 0)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("together");
        assertThatThrownBy(() -> service.createField(100L, new CreateFieldCommand(
            "scale_only", "Scale only", FieldDataType.DECIMAL, false, false, false, false,
            null, null, 2, null, null, null, 0)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("together");
    }

    @Test
    void permitsDecimalDefinitionsWithBothDimensionsAbsentOrPresent() {
        given(fields.saveAndFlush(any(FieldDefinition.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createField(100L, new CreateFieldCommand(
            "default_decimal", "Default decimal", FieldDataType.DECIMAL, false, false, false, false,
            null, null, null, null, null, null, 0))).isNotNull();
        assertThat(service.createField(100L, new CreateFieldCommand(
            "configured_decimal", "Configured decimal", FieldDataType.DECIMAL, false, false, false, false,
            null, 5, 2, null, null, null, 0))).isNotNull();
    }
    private CreateFieldCommand command(String fieldKey, FieldDataType dataType) {
        return new CreateFieldCommand(fieldKey, "Employee code", dataType, false, true, true, false,
            32, null, null, null, null, null, 0);
    }
}
