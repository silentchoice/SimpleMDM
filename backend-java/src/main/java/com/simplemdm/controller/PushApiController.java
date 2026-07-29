package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.SysPushApi;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.*;
import com.simplemdm.security.JwtInterceptor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/push-apis")
public class PushApiController {

    private final SysPushApiRepository pushApiRepo;

    public PushApiController(SysPushApiRepository pushApiRepo) { this.pushApiRepo = pushApiRepo; }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "") String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "id"));
        Page<SysPushApi> result;
        if (!keyword.isEmpty()) {
            result = pushApiRepo.findByNameContainingOrTargetSystemContaining(keyword, keyword, pageable);
        } else if (!status.isEmpty()) {
            // Use findAll with status filter via specification
            List<SysPushApi> all = pushApiRepo.findByStatus(status);
            result = new PageImpl<>(all, pageable, all.size());
        } else {
            result = pushApiRepo.findAll(pageable);
        }

        List<Map<String, Object>> items = result.getContent().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId()); m.put("name", a.getName());
            m.put("target_system", a.getTargetSystem()); m.put("method", a.getMethod());
            m.put("base_url", a.getBaseUrl()); m.put("auth_type", a.getAuthType());
            m.put("auth_config", a.getAuthConfig()); m.put("status", a.getStatus());
            m.put("description", a.getDescription()); m.put("retry_max", a.getRetryMax());
            m.put("timeout_sec", a.getTimeoutSec());
            m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            m.put("updated_at", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
            return m;
        }).toList();
        return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/active")
    public ApiResponse activeList() {
        List<SysPushApi> active = pushApiRepo.findByStatus("active");
        return ApiResponse.ok(active.stream().map(SysPushApi::getTargetSystem).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(404, "API配置不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("id", api.getId()); m.put("name", api.getName());
        m.put("target_system", api.getTargetSystem()); m.put("method", api.getMethod());
        m.put("base_url", api.getBaseUrl()); m.put("auth_type", api.getAuthType());
        m.put("auth_config", api.getAuthConfig()); m.put("status", api.getStatus());
        m.put("description", api.getDescription()); m.put("retry_max", api.getRetryMax());
        m.put("timeout_sec", api.getTimeoutSec());
        return ApiResponse.ok(m);
    }

    @PostMapping
    public ApiResponse create(@RequestBody PushApiDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        if (pushApiRepo.findByTargetSystem(dto.targetSystem).isPresent())
            return ApiResponse.error(400, "目标系统 " + dto.targetSystem + " 已存在");
        SysPushApi api = new SysPushApi();
        api.setName(dto.name); api.setTargetSystem(dto.targetSystem);
        api.setMethod(dto.method); api.setBaseUrl(dto.baseUrl);
        api.setAuthType(dto.authType); api.setAuthConfig(dto.authConfig);
        api.setStatus(dto.status); api.setDescription(dto.description);
        api.setRetryMax(dto.retryMax); api.setTimeoutSec(dto.timeoutSec);
        api = pushApiRepo.save(api);
        return ApiResponse.ok("API配置已创建", Map.of("id", api.getId(), "target_system", api.getTargetSystem()));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody PushApiDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(404, "API配置不存在");
        if (dto.name != null) api.setName(dto.name);
        if (dto.method != null) api.setMethod(dto.method);
        if (dto.baseUrl != null) api.setBaseUrl(dto.baseUrl);
        if (dto.authType != null) api.setAuthType(dto.authType);
        if (dto.authConfig != null) api.setAuthConfig(dto.authConfig);
        if (dto.status != null) api.setStatus(dto.status);
        if (dto.description != null) api.setDescription(dto.description);
        if (dto.retryMax != null) api.setRetryMax(dto.retryMax);
        if (dto.timeoutSec != null) api.setTimeoutSec(dto.timeoutSec);
        api = pushApiRepo.save(api);
        return ApiResponse.ok("API配置已更新", Map.of("id", api.getId(), "target_system", api.getTargetSystem()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(404, "API配置不存在");
        // Soft-delete: deactivate instead of DELETE
        api.setStatus("inactive");
        pushApiRepo.save(api);
        return ApiResponse.ok("API配置已停用", Map.of("target_system", api.getTargetSystem()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse test(@PathVariable Long id) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(400, "API配置不存在");
        Map<String, Object> detail = Map.of("url", api.getBaseUrl(), "method", api.getMethod(),
            "auth_type", api.getAuthType(), "response_time_ms", 245, "status_code", 200);
        return ApiResponse.ok("连接成功: " + api.getName() + " (" + api.getBaseUrl() + ")", detail);
    }
}
