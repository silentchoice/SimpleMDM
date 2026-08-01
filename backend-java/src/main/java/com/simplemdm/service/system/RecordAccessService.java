package com.simplemdm.service.system;

import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class RecordAccessService {
    public enum Decision { FULL, SHARED, DENY }

    public static final class Snapshot {
        private final Map<Long, Decision> decisions;
        private final Set<Long> readableDepartmentIds;

        private Snapshot(Map<Long, Decision> decisions) {
            this.decisions = Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
            LinkedHashSet<Long> readable = new LinkedHashSet<>();
            decisions.forEach((departmentId, decision) -> {
                if (decision != Decision.DENY) readable.add(departmentId);
            });
            this.readableDepartmentIds = Collections.unmodifiableSet(readable);
        }

        public Decision decision(Long departmentId) {
            return departmentId == null ? Decision.DENY
                : decisions.getOrDefault(departmentId, Decision.DENY);
        }

        public Set<Long> readableDepartmentIds() {
            return readableDepartmentIds;
        }
    }

    private final DepartmentRepository departments;
    private final AuthorizationService authorization;

    public RecordAccessService(DepartmentRepository departments, AuthorizationService authorization) {
        this.departments = departments;
        this.authorization = authorization;
    }

    public Decision access(User user, Long departmentId) {
        if (departmentId == null) return Decision.DENY;
        return snapshot(user).decision(departmentId);
    }

    public Set<Long> readableDepartmentIds(User user) {
        return snapshot(user).readableDepartmentIds();
    }

    public Snapshot snapshot(User user) {
        if (!valid(user)) return new Snapshot(Map.of());
        var activeIds = departments.findActiveIdsBySystemId(user.getSystemId());
        LinkedHashMap<Long, Decision> decisions = new LinkedHashMap<>();
        if (user.isSystemAdmin()) {
            activeIds.forEach(departmentId -> decisions.put(departmentId, Decision.FULL));
            return new Snapshot(decisions);
        }
        AuthorizationService.RecordViewAuthorization grants = authorization.recordViewAuthorization(
            user.getId(), user.getSystemId());
        for (Long departmentId : activeIds) {
            if (grants.recordView() && grants.selfViewDepartmentIds().contains(departmentId)) {
                decisions.put(departmentId, Decision.FULL);
            } else if (grants.crossView() && !departmentId.equals(user.getDepartmentId())) {
                decisions.put(departmentId, Decision.SHARED);
            } else {
                decisions.put(departmentId, Decision.DENY);
            }
        }
        return new Snapshot(decisions);
    }

    private boolean valid(User user) {
        return user != null && user.isActive() && user.isSystemActive();
    }
}
