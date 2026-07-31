package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.service.mdm.CreateFieldCommand;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.security.JwtInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MdmMetadataControllerTest {
    @AfterEach void clearUser() { JwtInterceptor.CURRENT_USER.remove(); }

    @Test
    void usesSystemScopedObjectTypeQuery() {
        ObjectTypeRepository repository = mock(ObjectTypeRepository.class);
        com.simplemdm.model.system.User user = mock(com.simplemdm.model.system.User.class);
        when(user.getId()).thenReturn(7L); when(user.getSystemId()).thenReturn(10L);
        JwtInterceptor.CURRENT_USER.set(user);
        ObjectType type = ObjectType.create(10L, "person", "Person");
        ReflectionTestUtils.setField(type, "id", 100L);
        when(repository.findBySystemId(10L)).thenReturn(List.of(type));

        ApiResponse response = new MdmMetadataController(repository, mock(FieldDefinitionRepository.class)).objectTypes();

        assertEquals(1, ((List<?>) response.getData()).size());
        verify(repository).findBySystemId(10L);
        verify(repository, never()).findAll();
    }
    @Test
    void returnsFieldDefinitionsInSnakeCaseWithOneBatchQuery() {
        ObjectTypeRepository objectTypes = mock(ObjectTypeRepository.class);
        FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
        com.simplemdm.model.system.User user = mock(com.simplemdm.model.system.User.class);
        when(user.getId()).thenReturn(7L); when(user.getSystemId()).thenReturn(10L);
        JwtInterceptor.CURRENT_USER.set(user);
        ObjectType type = ObjectType.create(10L, "person", "Person");
        ReflectionTestUtils.setField(type, "id", 100L);
        FieldDefinition field = FieldDefinition.create(100L, type,
            new CreateFieldCommand("salary", "Salary", FieldDataType.DECIMAL, true, false, true, false,
                null, 12, 2, null, null, null, 1), null);
        ReflectionTestUtils.setField(field, "id", 200L);
        when(objectTypes.findBySystemId(10L)).thenReturn(List.of(type));
        when(fields.findBySystemIdAndObjectTypeIdIn(10L, List.of(100L))).thenReturn(List.of(field));
        ApiResponse response = new MdmMetadataController(objectTypes, fields).objectTypes();
        Map<?, ?> item = (Map<?, ?>) ((List<?>) response.getData()).get(0);
        Map<?, ?> definition = (Map<?, ?>) ((List<?>) item.get("fields")).get(0);
        assertEquals("salary", definition.get("field_key"));
        assertEquals("DECIMAL", definition.get("data_type"));
        assertEquals(12, definition.get("precision_value"));
        assertEquals(2, definition.get("scale_value"));
        verify(fields).findBySystemIdAndObjectTypeIdIn(10L, List.of(100L));
        verify(fields, never()).findAll();
    }
}
