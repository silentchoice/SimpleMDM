package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.PersonnelSubDTO;
import com.simplemdm.model.SysUser;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.PersonnelSubService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personnel/{personnelId}/sub")
public class PersonnelSubController {

    private final PersonnelSubService subService;

    public PersonnelSubController(PersonnelSubService subService) {
        this.subService = subService;
    }

    @GetMapping
    public ApiResponse list(@PathVariable Long personnelId) {
        return ApiResponse.ok(subService.list(personnelId, currentUser()));
    }

    @PostMapping
    public ApiResponse create(@PathVariable Long personnelId,
                              @Valid @RequestBody PersonnelSubDTO dto) {
        return ApiResponse.ok("子表数据已创建",
            subService.create(personnelId, dto, currentUser()));
    }

    @PutMapping("/{subId}")
    public ApiResponse update(@PathVariable Long personnelId, @PathVariable Long subId,
                              @Valid @RequestBody PersonnelSubDTO dto) {
        return ApiResponse.ok("子表数据已更新",
            subService.update(personnelId, subId, dto, currentUser()));
    }

    private SysUser currentUser() {
        return JwtInterceptor.LEGACY_CURRENT_USER.get();
    }
}
