package com.simplemdm.repository;

import com.simplemdm.model.MdmFieldDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MdmFieldDefinitionRepositoryTest {

    @Autowired
    private MdmFieldDefinitionRepository repository;

    @Test
    void rejectsSameFieldKeyAcrossMasterAndSubWithinOneSystem() {
        repository.saveAndFlush(field("HR", "ALL", "master", "basic", "employee_code"));

        assertThrows(DataIntegrityViolationException.class, () ->
            repository.saveAndFlush(field("HR", "工程部", "sub", "project", "employee_code")));
    }

    @Test
    void permitsSameFieldKeyInDifferentSystems() {
        repository.saveAndFlush(field("HR", "ALL", "master", "basic", "employee_code"));
        repository.saveAndFlush(field("FIN", "ALL", "master", "basic", "employee_code"));

        assertEquals(2, repository.count());
    }

    private MdmFieldDefinition field(String systemCode, String department, String tableType,
                                     String subType, String fieldKey) {
        MdmFieldDefinition field = new MdmFieldDefinition();
        field.setSystemCode(systemCode);
        field.setDepartment(department);
        field.setTableType(tableType);
        field.setSubType(subType);
        field.setFieldKey(fieldKey);
        field.setFieldName(fieldKey);
        field.setFieldType("string");
        field.setRequired(false);
        field.setSortOrder(1);
        field.setSystemField(false);
        field.setShared(false);
        return field;
    }
}
