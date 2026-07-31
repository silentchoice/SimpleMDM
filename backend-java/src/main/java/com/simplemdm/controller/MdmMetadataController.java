package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mdm")
public class MdmMetadataController {
    private final ObjectTypeRepository objectTypes;

    public MdmMetadataController(ObjectTypeRepository objectTypes) {
        this.objectTypes = objectTypes;
    }

    @GetMapping("/object-types")
    public ApiResponse objectTypes() {
        Long systemId = SystemController.currentUser().getSystemId();
        return ApiResponse.ok(objectTypes.findBySystemId(systemId).stream()
            .map(type -> java.util.Map.of("id", type.getId(), "code", type.getCode(), "name", type.getName()))
            .toList());
    }
}