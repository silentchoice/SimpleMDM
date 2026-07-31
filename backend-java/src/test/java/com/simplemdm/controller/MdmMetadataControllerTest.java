package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.security.JwtInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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

        ApiResponse response = new MdmMetadataController(repository).objectTypes();

        assertEquals(1, ((List<?>) response.getData()).size());
        verify(repository).findBySystemId(10L);
        verify(repository, never()).findAll();
    }
}