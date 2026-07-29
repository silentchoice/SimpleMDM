# Task 8 Report: Seed Data Initializer and Exception Handler

## Files Created

1. `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java` — `CommandLineRunner` that seeds 4 users, permissions, approver assignment (lisi -> HR), 8 personnel, 2 historical approvals with push logs, and 3 push API configs. Only runs when user table is empty.

2. `backend-java/src/main/java/com/simplemdm/exception/BusinessException.java` — `RuntimeException` subclass with `int code` field.

3. `backend-java/src/main/java/com/simplemdm/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` handling `BusinessException` -> 400 and generic `Exception` -> 500.

## Compilation

```
cd backend-java && mvn compile
BUILD SUCCESS — 53 source files compiled.
```

## Verification

- `ApiResponse.error(int, String)` method exists and matches handler signature.
- No compilation errors on any of the 3 new files.
