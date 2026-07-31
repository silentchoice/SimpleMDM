package com.simplemdm.service.mdm;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.ObjectType;
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
            .hasMessageContaining("precision");
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

    private FieldDefinition field(FieldDataType type, boolean required, Integer maxLength, Integer precision, Integer scale) {
        return FieldDefinition.create(100L, person,
            new CreateFieldCommand("amount", "Amount", type, required, false, false, false,
                maxLength, precision, scale, null, null, null, 0), null);
    }
}
