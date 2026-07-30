package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.SysPushLog;
import com.simplemdm.model.SysUser;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.PushService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/push-logs")
public class PushLogController {

    private final PushService pushService;

    public PushLogController(PushService pushService) { this.pushService = pushService; }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String targetSystem,
                            @RequestParam(defaultValue = "") String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize) {
        Page<Map<String, Object>> result = pushService.listPushLogs(
            targetSystem.isEmpty() ? null : targetSystem,
            status.isEmpty() ? null : status, page, pageSize);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse retry(@PathVariable Long id) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        SysPushLog log = pushService.retryPush(id);
        if (log == null) return ApiResponse.error(400, "推送日志不存在或状态不是失败");
        Map<String, Object> data = new HashMap<>();
        data.put("id", log.getId());
        data.put("status", log.getStatus());
        return ApiResponse.ok("重试成功: 数据已同步到 " + log.getTargetSystem(), data);
    }
}
