package com.simplemdm.controller;
import com.simplemdm.dto.ApiResponse;import com.simplemdm.exception.BusinessException;import com.simplemdm.model.integration.*;import com.simplemdm.model.system.User;import com.simplemdm.repository.integration.*;import com.simplemdm.security.JwtInterceptor;import com.simplemdm.service.system.AuthorizationService;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/integration")
public class IntegrationController {
 private final PushEndpointRepository endpoints;private final PushSubscriptionRepository subscriptions;private final PushLogRepository logs;private final AuthorizationService auth;
 public IntegrationController(PushEndpointRepository e,PushSubscriptionRepository s,PushLogRepository l,AuthorizationService a){endpoints=e;subscriptions=s;logs=l;auth=a;}
 @GetMapping("/endpoints") public ApiResponse endpoints(){User u=viewUser();return ApiResponse.ok(endpoints.findBySystemIdOrderByCode(u.getSystemId()));}
 @PostMapping("/endpoints") public ApiResponse endpoint(@RequestBody EndpointBody b){User u=manageUser();return ApiResponse.ok(endpoints.save(PushEndpoint.create(u.getSystemId(),b.code,b.name,b.endpoint_url,b.authentication_type)));}
 @GetMapping("/subscriptions") public ApiResponse subscriptions(){User u=viewUser();return ApiResponse.ok(subscriptions.findBySystemIdOrderByIdDesc(u.getSystemId()));}
 @PostMapping("/subscriptions") public ApiResponse subscription(@RequestBody SubscriptionBody b){User u=manageUser();endpoints.findBySystemIdAndId(u.getSystemId(),b.endpoint_id).orElseThrow(()->new BusinessException(404,"Endpoint not found"));return ApiResponse.ok(subscriptions.save(PushSubscription.active(null,u.getSystemId(),b.endpoint_id,b.object_type_id,b.event_type)));}
 @GetMapping("/logs") public ApiResponse logs(){User u=viewUser();return ApiResponse.ok(logs.findBySystemIdOrderByIdDesc(u.getSystemId()));}
 private User viewUser(){return require("MDM_RECORD_VIEW");}private User manageUser(){return require("MDM_FIELD_MANAGE");}
 private User require(String p){User u=JwtInterceptor.CURRENT_USER.get();if(u==null)throw new BusinessException(401,"System user required");if(!u.isSystemAdmin()&&!auth.can(u.getId(),p,u.getDepartmentId()))throw new BusinessException(403,"Permission required");return u;}
 public static class EndpointBody{public String code;public String name;public String endpoint_url;public String authentication_type;}
 public static class SubscriptionBody{public Long endpoint_id;public Long object_type_id;public String event_type="RECORD_CHANGED";}
}