package com.example.mdm.record;

import java.time.Instant;

public record EditLock(long recordId, long departmentId, long userId, String displayName,
                       String token, Instant expiresAt) {}
