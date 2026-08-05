package com.example.mdm.metadata;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataService {
  private static final int SNAPSHOT_SCHEMA_VERSION = 1;
  private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
  private final MetadataRepository repository;
  private final MetadataApprovalRepository approvals;
  private final AuthorizationService authorization;
  private final FieldStructureValidator fieldValidator;
  private final ObjectMapper json;

  public MetadataService(MetadataRepository repository, MetadataApprovalRepository approvals,
      AuthorizationService authorization, FieldStructureValidator fieldValidator, ObjectMapper json) {
    this.repository = repository;
    this.approvals = approvals;
    this.authorization = authorization;
    this.fieldValidator = fieldValidator;
    this.json = json;
  }

  public MasterType createMasterType(String code, String name) {
    var actor = authorization.requireRole(Role.SUPER_ADMIN);
    return repository.createMasterType(normalize(code), name.trim(), actor.id());
  }

  public MasterType currentMasterType() {
    var actor = departmentReader();
    return repository.findAssignedMasterType(actor.department().id());
  }

  public void assignDepartment(long departmentId, long masterTypeId) {
    authorization.requireRole(Role.SUPER_ADMIN);
    repository.assignDepartment(departmentId, masterTypeId);
  }

  @Transactional
  public long submitMasterFields(List<FieldDefinition> fields) {
    var actor = editor();
    var submitted = immutableNonEmpty(fields, "Master fields are required");
    long templateId = commonFieldOwner(submitted);
    repository.lockTemplateAssignment(actor.department().id(), templateId);
    submitted.forEach(fieldValidator::validate);
    validateFieldList(submitted);
    var before = repository.findMasterFields(actor.department().id(), templateId);
    return submit(actor, templateId, "MASTER_FIELDS", templateId, before, submitted);
  }

  @Transactional
  public long submitSubTypes(List<SubType> types) {
    var actor = editor();
    var submitted = immutableNonEmpty(types, "Sub types are required");
    long templateId = commonTemplate(submitted);
    repository.lockTemplateAssignment(actor.department().id(), templateId);
    validateSubTypes(submitted);
    var before = repository.findSubTypes(actor.department().id(), templateId);
    return submit(actor, templateId, "SUB_TYPES", templateId, before, submitted);
  }

  @Transactional
  public long submitSubFields(long subTypeId, List<FieldDefinition> fields) {
    var actor = editor();
    var submitted = immutableNonEmpty(fields, "Sub fields are required");
    if (submitted.stream().anyMatch(field -> field.ownerTypeId() != subTypeId)) {
      throw BusinessException.badRequest("Sub field owner does not match sub type");
    }
    long templateId = approvals.requireSubTypeTemplate(actor.department().id(), subTypeId);
    repository.lockTemplateAssignment(actor.department().id(), templateId);
    long lockedTemplateId = approvals.requireSubTypeTemplate(actor.department().id(), subTypeId);
    if (lockedTemplateId != templateId) {
      throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT,
          "Metadata changed during submission");
    }
    submitted.forEach(fieldValidator::validate);
    validateFieldList(submitted);
    var before = repository.findSubFields(actor.department().id(), subTypeId);
    return submit(actor, templateId, "SUB_FIELDS", subTypeId, before, submitted);
  }

  private UserPrincipal editor() {
    var actor = authorization.requireRole(Role.DEPT_EDITOR);
    if (actor.department() == null) {
      throw BusinessException.forbidden();
    }
    authorization.requireDepartment(actor.department().id());
    return actor;
  }

  private long submit(UserPrincipal actor, long templateId, String kind, long entityId,
      List<?> before, List<?> after) {
    String baseFingerprint = fingerprint(before);
    var beforeEnvelope = new SnapshotEnvelope(SNAPSHOT_SCHEMA_VERSION, actor.department().id(),
        templateId, kind, baseFingerprint, List.copyOf(before));
    var afterEnvelope = new SnapshotEnvelope(SNAPSHOT_SCHEMA_VERSION, actor.department().id(),
        templateId, kind, baseFingerprint, List.copyOf(after));
    return approvals.submit(new MetadataChangeRequest(actor.department().id(), actor.id(), kind,
        entityId, serialize(beforeEnvelope), serialize(afterEnvelope)));
  }

  private String fingerprint(List<?> definitions) {
    try {
      byte[] canonical = json.writeValueAsString(List.copyOf(definitions))
          .getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Unable to fingerprint metadata snapshot", exception);
    }
  }

  private String serialize(SnapshotEnvelope envelope) {
    try {
      return json.writeValueAsString(envelope);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize metadata snapshot", exception);
    }
  }

  private long commonFieldOwner(List<FieldDefinition> fields) {
    long owner = fields.get(0).ownerTypeId();
    if (fields.stream().anyMatch(field -> field.ownerTypeId() != owner)) {
      throw BusinessException.badRequest("Fields must have one owner");
    }
    return owner;
  }

  private long commonTemplate(List<SubType> types) {
    long template = types.get(0).masterTypeId();
    if (types.stream().anyMatch(type -> type.masterTypeId() != template)) {
      throw BusinessException.badRequest("Sub types must have one master type");
    }
    return template;
  }

  private void validateSubTypes(List<SubType> types) {
    Set<String> codes = new HashSet<>();
    for (var type : types) {
      if (type.code() == null || !CODE_PATTERN.matcher(type.code()).matches()) {
        throw BusinessException.badRequest("Invalid sub type code");
      }
      if (type.name() == null || type.name().isBlank() || type.name().length() > 128) {
        throw BusinessException.badRequest("Invalid sub type " + type.code() + ": invalid name");
      }
      if (type.status() != MetadataStatus.ACTIVE) {
        throw BusinessException.badRequest("Invalid sub type " + type.code() + ": invalid status");
      }
      if (!codes.add(type.code().toLowerCase(Locale.ROOT))) {
        throw BusinessException.badRequest("Duplicate sub type code: " + type.code());
      }
    }
  }

  private void validateFieldList(List<FieldDefinition> fields) {
    Set<String> codes = new HashSet<>();
    Set<Integer> sortOrders = new HashSet<>();
    for (var field : fields) {
      if (field.status() != MetadataStatus.ACTIVE) {
        throw BusinessException.badRequest("Invalid field " + field.code() + ": invalid status");
      }
      if (!codes.add(field.code().toLowerCase(Locale.ROOT))) {
        throw BusinessException.badRequest("Duplicate field code: " + field.code());
      }
      if (field.sortOrder() < 0 || !sortOrders.add(field.sortOrder())) {
        throw BusinessException.badRequest("Invalid field ordering: " + field.code());
      }
    }
  }

  private <T> List<T> immutableNonEmpty(List<T> values, String message) {
    if (values == null || values.isEmpty() || values.stream().anyMatch(java.util.Objects::isNull)) {
      throw BusinessException.badRequest(message);
    }
    return List.copyOf(values);
  }

  public List<MasterType> masterTypes() {
    authorization.requireRole(Role.SUPER_ADMIN);
    return repository.findMasterTypes();
  }

  public List<FieldDefinition> masterFields(long id) {
    var actor = departmentReader();
    repository.requireTemplateAccess(actor.department().id(), id);
    return repository.findMasterFields(actor.department().id(), id);
  }

  public List<SubType> subTypes(long id) {
    var actor = departmentReader();
    repository.requireTemplateAccess(actor.department().id(), id);
    return repository.findSubTypes(actor.department().id(), id);
  }

  public List<FieldDefinition> subFields(long id) {
    var actor = departmentReader();
    long templateId = approvals.requireSubTypeTemplate(actor.department().id(), id);
    repository.requireTemplateAccess(actor.department().id(), templateId);
    return repository.findSubFields(actor.department().id(), id);
  }

  private UserPrincipal departmentReader() {
    var actor = authorization.requireRole(Role.DEPT_EDITOR, Role.DEPT_APPROVER, Role.DEPT_VIEWER);
    if (actor.department() == null) {
      throw BusinessException.forbidden();
    }
    authorization.requireDepartment(actor.department().id());
    return actor;
  }

  @Deprecated @Transactional public FieldDefinition createMasterField(FieldDefinition field) {
    submitMasterFields(List.of(field));
    return field;
  }

  @Deprecated @Transactional public SubType createSubType(long masterTypeId, String code, String name) {
    var type = new SubType(0, masterTypeId, normalize(code), name.trim(), MetadataStatus.ACTIVE);
    submitSubTypes(List.of(type));
    return type;
  }

  @Deprecated @Transactional public FieldDefinition createSubField(FieldDefinition field) {
    submitSubFields(field.ownerTypeId(), List.of(field));
    return field;
  }

  private String normalize(String code) { return code.trim().toUpperCase(Locale.ROOT); }

  private record SnapshotEnvelope(int schemaVersion, long departmentId, long templateId,
      String entityKind, String baseFingerprint, List<?> orderedDefinitions) {}
}
