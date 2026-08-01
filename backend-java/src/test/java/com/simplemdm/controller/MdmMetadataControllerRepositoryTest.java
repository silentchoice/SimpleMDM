package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.mdm.CreateFieldCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.url=jdbc:h2:mem:metadata-controller;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.simplemdm.model")
@EnableJpaRepositories(basePackages = "com.simplemdm.repository")
class MdmMetadataControllerRepositoryTest {
    @Autowired private SystemRepository systems;
    @Autowired private ObjectTypeRepository objectTypes;
    @Autowired private FieldDefinitionRepository fields;
    @Autowired private ChildTypeRepository childTypes;
    @Autowired private ChildFieldDefinitionRepository childFields;

    @AfterEach
    void clearUser() {
        JwtInterceptor.CURRENT_USER.remove();
    }

    @Test
    void returnsOnlyActiveSameSystemChildrenAndFieldsInStableOrderWithEmptyArrays() {
        SystemEntity ownSystem = systems.saveAndFlush(SystemEntity.create("META_OWN", "Own"));
        SystemEntity foreignSystem = systems.saveAndFlush(SystemEntity.create("META_FOREIGN", "Foreign"));
        ObjectType person = objectTypes.saveAndFlush(ObjectType.create(ownSystem, "person", "人员"));
        objectTypes.saveAndFlush(ObjectType.create(ownSystem, "organization", "组织"));
        ObjectType foreignPerson = objectTypes.saveAndFlush(
            ObjectType.create(foreignSystem, "person", "Foreign Person"));

        ChildType later = childType(person, "address", "地址", 2, "active");
        ChildType first = childType(person, "phone", "电话", 1, "active");
        childType(person, "secret", "停用子表", 0, "inactive");
        childType(foreignPerson, "foreign", "外部子表", 0, "active");
        childField(first, "secondary", "次要号码", 2, "active");
        childField(first, "primary", "主要号码", 1, "active");
        childField(first, "retired", "停用号码", 0, "inactive");
        childField(later, "city", "城市", 1, "active");

        User user = mock(User.class);
        when(user.getSystemId()).thenReturn(ownSystem.getId());
        JwtInterceptor.CURRENT_USER.set(user);
        ApiResponse response = new MdmMetadataController(objectTypes, fields, childTypes, childFields)
            .objectTypes();

        Map<?, ?> personView = byCode(response, "person");
        List<?> children = (List<?>) personView.get("child_types");
        assertThat(children.stream().map(value -> String.valueOf(((Map<?, ?>) value).get("code"))).toList())
            .containsExactly("phone", "address");
        List<?> phoneFields = (List<?>) ((Map<?, ?>) children.get(0)).get("fields");
        assertThat(phoneFields.stream().map(value -> String.valueOf(((Map<?, ?>) value).get("field_key"))).toList())
            .containsExactly("primary", "secondary");
        assertThat(byCode(response, "organization").get("child_types")).isEqualTo(List.of());
        assertThat(((List<?>) response.getData()).stream()
            .map(value -> String.valueOf(((Map<?, ?>) value).get("name"))).toList())
            .doesNotContain("Foreign Person");
    }

    private ChildType childType(ObjectType objectType, String code, String name, int order, String status) {
        ChildType value = ChildType.create(objectType.getId(), objectType, code, name);
        ReflectionTestUtils.setField(value, "sortOrder", order);
        ReflectionTestUtils.setField(value, "status", status);
        return childTypes.saveAndFlush(value);
    }

    private void childField(ChildType childType, String key, String name, int order, String status) {
        ChildFieldDefinition value = ChildFieldDefinition.create(childType.getId(), childType,
            new CreateFieldCommand(key, name, FieldDataType.STRING, false, false, true, true,
                64, null, null, null, null, null, order), null);
        ReflectionTestUtils.setField(value, "status", status);
        childFields.saveAndFlush(value);
    }

    private Map<?, ?> byCode(ApiResponse response, String code) {
        return ((List<?>) response.getData()).stream().map(Map.class::cast)
            .filter(value -> code.equals(value.get("code"))).findFirst().orElseThrow();
    }
}
