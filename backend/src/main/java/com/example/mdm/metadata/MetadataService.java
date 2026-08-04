package com.example.mdm.metadata;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {
  private static final int SNAPSHOT_SCHEMA_VERSION = 1;
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

  public void assignDepartment(long departmentId, long masterTypeId) {
    authorization.requireRole(Role.SUPER_ADMIN);
    repository.assignDepartment(departmentId, masterTypeId);
  }

  public long submitMasterFields(List<FieldDefinition> fields) {
    var actor = editor();
    var submitted = immutableNonEmpty(fields, "Master fields are required");
    long templateId = commonFieldOwner(submitted);
    repository.requireAssignment(actor.department().id(), templateId);
    submitted.forEach(fieldValidator::validate);
    var before = repository.findMasterFields(actor.department().id(), templateId);
    return submit(actor, templateId, "MASTER_FIELDS", templateId, before, submitted);
  }

  public long submitSubTypes(List<SubType> types) {
    var actor = editor();
    var submitted = immutableNonEmpty(types, "Sub types are required");
    long templateId = commonTemplate(submitted);
    repository.requireAssignment(actor.department().id(), templateId);
    var before = repository.findSubTypes(actor.department().id(), templateId);
    return submit(actor, templateId, "SUB_TYPES", templateId, before, submitted);
  }

  public long submitSubFields(long subTypeId, List<FieldDefinition> fields) {
    var actor = editor();
    var submitted = immutableNonEmpty(fields, "Sub fields are required");
    if (submitted.stream().anyMatch(field -> field.ownerTypeId() != subTypeId)) {
      throw BusinessException.badRequest("Sub field owner does not match sub type");
    }
    long templateId = approvals.requireSubTypeTemplate(actor.department().id(), subTypeId);
    repository.requireAssignment(actor.department().id(), templateId);
    submitted.forEach(fieldValidator::validate);
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
    var beforeEnvelope = new SnapshotEnvelope(SNAPSHOT_SCHEMA_VERSION, actor.department().id(),
        templateId, kind, List.copyOf(before));
    var afterEnvelope = new SnapshotEnvelope(SNAPSHOT_SCHEMA_VERSION, actor.department().id(),
        templateId, kind, List.copyOf(after));
    return approvals.submit(new MetadataChangeRequest(actor.department().id(), actor.id(), kind,
        entityId, serialize(beforeEnvelope), serialize(afterEnvelope)));
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

  public List<FieldDefinition> masterFields(long id) { return repository.findMasterFields(id); }
  public List<SubType> subTypes(long id) { return repository.findSubTypes(id); }
  public List<FieldDefinition> subFields(long id) { return repository.findSubFields(id); }

  @Deprecated public FieldDefinition createMasterField(FieldDefinition field) {
    submitMasterFields(List.of(field));
    return field;
  }

  @Deprecated public SubType createSubType(long masterTypeId, String code, String name) {
    var type = new SubType(0, masterTypeId, normalize(code), name.trim(), MetadataStatus.ACTIVE);
    submitSubTypes(List.of(type));
    return type;
  }

  @Deprecated public FieldDefinition createSubField(FieldDefinition field) {
    submitSubFields(field.ownerTypeId(), List.of(field));
    return field;
  }

  private String normalize(String code) { return code.trim().toUpperCase(Locale.ROOT); }

  private record SnapshotEnvelope(int schemaVersion, long departmentId, long templateId,
      String entityKind, List<?> orderedDefinitions) {}
}
