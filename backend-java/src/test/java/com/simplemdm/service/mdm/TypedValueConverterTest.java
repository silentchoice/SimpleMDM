package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.TypedValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedValueConverterTest {

    private final TypedValueConverter converter = new TypedValueConverter();
    private final ObjectType person = ObjectType.create(10L, "PERSON", "Person");

    @Test
    void convertsOnlyTheDeclaredDecimalType() {
        FieldDefinition decimalField = field(FieldDataType.DECIMAL, false, null, 5, 2);

        var value = converter.convert(decimalField, "123.45");

        assertThat(value.decimalValue()).isEqualByComparingTo("123.45");
        assertThat(value.nonNullValueCount()).isEqualTo(1);
        assertThat(value.stringValue()).isNull();
    }

    @Test
    void rejectsBlankRequiredValuesAndDecimalPrecisionOverflow() {
        FieldDefinition requiredString = field(FieldDataType.STRING, true, 10, null, null);
        FieldDefinition decimalField = field(FieldDataType.DECIMAL, false, null, 5, 2);

        assertThatThrownBy(() -> converter.convert(requiredString, " "))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("required");
        assertThatThrownBy(() -> converter.convert(decimalField, new BigDecimal("1234.56")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("integer digits");
    }

    @Test
    void enforcesDecimalIntegerAndFractionalDigitBoundariesIncludingTrailingZeros() {
        FieldDefinition decimalField = field(FieldDataType.DECIMAL, false, null, 5, 2);

        assertThat(converter.convert(decimalField, "999.9900").decimalValue()).isEqualByComparingTo("999.9900");
        assertThat(converter.convert(decimalField, "9.999E2").decimalValue()).isEqualByComparingTo("999.90");
        assertThatThrownBy(() -> converter.convert(decimalField, "1000"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("integer digits");
        assertThatThrownBy(() -> converter.convert(decimalField, "1.234"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("fractional digits");
    }

    @Test
    void enforcesV1PhysicalDecimalLimitsEvenForManuallyConstructedMetadata() {
        FieldDefinition beyondPhysicalPrecision = field(FieldDataType.DECIMAL, false, null, 39, 10);
        FieldDefinition beyondPhysicalScale = field(FieldDataType.DECIMAL, false, null, 38, 11);

        assertThatThrownBy(() -> converter.convert(beyondPhysicalPrecision, "1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("physical");
        assertThatThrownBy(() -> converter.convert(beyondPhysicalScale, "1"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("physical");
    }

    @Test
    void rejectsReferenceOutsideTheConfiguredObjectTypeOrSystem() {
        ObjectType organization = ObjectType.create(10L, "ORG", "Organization");
        FieldDefinition referenceField = FieldDefinition.create(100L, person,
            new CreateFieldCommand("organization", "Organization", FieldDataType.REFERENCE, false,
                false, false, false, null, null, null, 200L, null, null, 0), organization);

        assertThatThrownBy(() -> converter.convert(referenceField,
            new TypedValueConverter.ReferenceValue(99L, 201L, 10L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("object type");
        assertThatThrownBy(() -> converter.convert(referenceField,
            new TypedValueConverter.ReferenceValue(99L, 200L, 11L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("system");
    }

    @Test
    void rejectsMalformedReferenceValuesAndMultipleTypedColumns() {
        ObjectType organization = ObjectType.create(10L, "ORG", "Organization");
        FieldDefinition referenceField = FieldDefinition.create(100L, person,
            new CreateFieldCommand("organization", "Organization", FieldDataType.REFERENCE, true,
                false, false, false, null, null, null, 200L, null, null, 0), organization);

        assertThatThrownBy(() -> converter.convert(referenceField,
            new TypedValueConverter.ReferenceValue(null, 200L, 10L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("record");
        assertThatThrownBy(() -> converter.convert(referenceField,
            new TypedValueConverter.ReferenceValue(99L, null, 10L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("object type");
        assertThatThrownBy(() -> converter.convert(referenceField,
            new TypedValueConverter.ReferenceValue(99L, 200L, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("system");
        assertThatThrownBy(() -> new TypedValue("x", null, 1L, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most one");
    }

    private FieldDefinition field(FieldDataType type, boolean required, Integer maxLength, Integer precision, Integer scale) {
        return FieldDefinition.create(100L, person,
            new CreateFieldCommand("amount", "Amount", type, required, false, false, false,
                maxLength, precision, scale, null, null, null, 0), null);
    }
}
