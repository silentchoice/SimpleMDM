package com.example.mdm.metadata;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataApprovalApplicationService {
  private static final int SCHEMA_VERSION = 1;
  private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
  private static final Set<String> KINDS = Set.of("MASTER_FIELDS", "SUB_TYPES", "SUB_FIELDS");
  private final MetadataApprovalRepository approvals;
  private final MetadataRepository metadata;
  private final AuthorizationService authorization;
  private final FieldStructureValidator fieldValidator;
  private final ObjectMapper json;

  public MetadataApprovalApplicationService(MetadataApprovalRepository approvals,
      MetadataRepository metadata, AuthorizationService authorization,
      FieldStructureValidator fieldValidator, ObjectMapper json) {
    this.approvals = approvals;
    this.metadata = metadata;
    this.authorization = authorization;
    this.fieldValidator = fieldValidator;
    this.json = json;
  }

  @Transactional
  public void approve(long taskId, String comment) {
    UserPrincipal actor = approver();
    var task = approvals.lock(actor.department().id(), taskId);
    requireOwnedPending(task, actor);
    Envelope before = decode(task.beforeSnapshot());
    Envelope after = decode(task.afterSnapshot());
    validateEnvelope(task, before);
    validateEnvelope(task, after);
    if (before.templateId() != after.templateId()
        || !before.baseFingerprint().equals(after.baseFingerprint())) {
      throw badSnapshot();
    }

    metadata.lockTemplateAssignment(task.departmentId(), after.templateId());
    requireSnapshotIntegrity(task, before);
    switch (task.entityKind()) {
      case "MASTER_FIELDS" -> approveMasterFields(task, after);
      case "SUB_TYPES" -> approveSubTypes(task, after);
      case "SUB_FIELDS" -> approveSubFields(task, after);
      default -> throw badSnapshot();
    }
    approvals.approve(actor.department().id(), taskId, actor.id(), trimToNull(comment));
  }

  @Transactional
  public void reject(long taskId, String reason) {
    UserPrincipal actor = approver();
    if (reason == null || reason.isBlank()) {
      throw BusinessException.badRequest("Rejection reason is required");
    }
    var task = approvals.lock(actor.department().id(), taskId);
    requireOwnedPending(task, actor);
    approvals.reject(actor.department().id(), taskId, actor.id(), reason.trim());
  }

  private void approveMasterFields(MetadataApprovalRepository.ApprovalTask task, Envelope after) {
    if (task.entityId() != after.templateId()) throw badSnapshot();
    List<FieldDefinition> definitions = fields(after, task.entityId());
    requireCurrent(after.baseFingerprint(), metadata.findMasterFields(task.departmentId(), task.entityId()));
    metadata.replaceMasterFields(task.departmentId(), task.entityId(), definitions);
  }

  private void approveSubTypes(MetadataApprovalRepository.ApprovalTask task, Envelope after) {
    if (task.entityId() != after.templateId()) throw badSnapshot();
    List<SubType> definitions = subTypes(after, task.entityId());
    requireCurrent(after.baseFingerprint(), metadata.findSubTypes(task.departmentId(), task.entityId()));
    metadata.replaceSubTypes(task.departmentId(), task.entityId(), definitions);
  }

  private List<SubType> subTypes(Envelope envelope, long templateId) {
    List<SubType> definitions = readDefinitions(envelope, SubType.class);
    Set<String> codes = new HashSet<>();
    for (var type : definitions) {
      if (type.masterTypeId() != templateId || type.code() == null || !CODE.matcher(type.code()).matches()
          || type.name() == null || type.name().isBlank() || type.name().length() > 128
          || type.status() != MetadataStatus.ACTIVE
          || !codes.add(type.code().toLowerCase(Locale.ROOT))) throw badSnapshot();
    }
    return definitions;
  }

  private void approveSubFields(MetadataApprovalRepository.ApprovalTask task, Envelope after) {
    long template = approvals.requireSubTypeTemplate(task.departmentId(), task.entityId());
    if (template != after.templateId()) throw new BusinessException(HttpStatus.CONFLICT, "Metadata snapshot is stale");
    List<FieldDefinition> definitions = fields(after, task.entityId());
    requireCurrent(after.baseFingerprint(), metadata.findSubFields(task.departmentId(), task.entityId()));
    metadata.replaceSubFields(task.departmentId(), task.entityId(), definitions);
  }

  private List<FieldDefinition> fields(Envelope envelope, long ownerId) {
    List<FieldDefinition> definitions = readDefinitions(envelope, FieldDefinition.class);
    Set<String> codes = new HashSet<>();
    Set<Integer> orders = new HashSet<>();
    for (var field : definitions) {
      if (field.ownerTypeId() != ownerId || field.status() != MetadataStatus.ACTIVE
          || field.code() == null || !codes.add(field.code().toLowerCase(Locale.ROOT)) || field.sortOrder() < 0
          || !orders.add(field.sortOrder())) throw badSnapshot();
      try { fieldValidator.validate(field); } catch (RuntimeException invalid) { throw badSnapshot(); }
    }
    return List.copyOf(definitions);
  }

  private void requireCurrent(String expected, List<?> current) {
    if (!expected.equals(fingerprint(current))) {
      throw new BusinessException(HttpStatus.CONFLICT, "Metadata snapshot is stale");
    }
  }

  private void requireSnapshotIntegrity(MetadataApprovalRepository.ApprovalTask task, Envelope before) {
    List<?> definitions = switch (task.entityKind()) {
      case "MASTER_FIELDS", "SUB_FIELDS" -> fields(before, task.entityId());
      case "SUB_TYPES" -> subTypes(before, task.entityId());
      default -> throw badSnapshot();
    };
    if (!before.baseFingerprint().equals(fingerprint(definitions))) throw badSnapshot();
  }

  private void validateEnvelope(MetadataApprovalRepository.ApprovalTask task, Envelope envelope) {
    if (envelope.schemaVersion() != SCHEMA_VERSION || envelope.departmentId() != task.departmentId()
        || envelope.entityKind() == null || !envelope.entityKind().equals(task.entityKind())
        || !KINDS.contains(envelope.entityKind()) || envelope.templateId() <= 0
        || envelope.baseFingerprint() == null || !FINGERPRINT.matcher(envelope.baseFingerprint()).matches()
        || envelope.orderedDefinitions() == null || !envelope.orderedDefinitions().isArray()) {
      throw badSnapshot();
    }
  }

  private Envelope decode(String value) {
    try { return json.readValue(value, Envelope.class); }
    catch (JsonProcessingException | RuntimeException exception) { throw badSnapshot(); }
  }

  private <T> List<T> readDefinitions(Envelope envelope, Class<T> type) {
    try {
      var listType = json.getTypeFactory().constructCollectionType(List.class, type);
      List<T> definitions = json.readerFor(listType).readValue(envelope.orderedDefinitions());
      if (definitions.isEmpty() || definitions.stream().anyMatch(java.util.Objects::isNull)) {
        throw badSnapshot();
      }
      return List.copyOf(definitions);
    } catch (IOException | RuntimeException exception) { throw badSnapshot(); }
  }

  private String fingerprint(List<?> definitions) {
    try {
      byte[] value = json.writeValueAsString(List.copyOf(definitions)).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Unable to fingerprint metadata", exception);
    }
  }

  private UserPrincipal approver() {
    var actor = authorization.requireRole(Role.DEPT_APPROVER);
    if (actor.department() == null) throw BusinessException.forbidden();
    authorization.requireDepartment(actor.department().id());
    return actor;
  }

  private void requireOwnedPending(MetadataApprovalRepository.ApprovalTask task, UserPrincipal actor) {
    if (task.departmentId() != actor.department().id()) throw BusinessException.forbidden();
    if (!"PENDING".equals(task.status())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Approval task is not pending");
    }
  }

  private BusinessException badSnapshot() {
    return BusinessException.badRequest("Invalid metadata snapshot");
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record Envelope(int schemaVersion, long departmentId, long templateId, String entityKind,
      String baseFingerprint, JsonNode orderedDefinitions) {}
}
