package com.example.mdm.record;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.error.BusinessException;
import com.example.mdm.metadata.FieldValueValidator;
import com.example.mdm.metadata.MetadataRepository;
import com.example.mdm.metadata.SubType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordDraftService {
  private final RecordRepository records;
  private final MetadataRepository metadata;
  private final FieldValueValidator validator;
  private final CodeRuleService codes;
  private final AuthorizationService authorization;
  private final Clock clock;

  public RecordDraftService(RecordRepository records, MetadataRepository metadata,
      FieldValueValidator validator, CodeRuleService codes, AuthorizationService authorization,
      Clock clock) {
    this.records = records;
    this.metadata = metadata;
    this.validator = validator;
    this.codes = codes;
    this.authorization = authorization;
    this.clock = clock;
  }

  @Autowired
  RecordDraftService(RecordRepository records, MetadataRepository metadata,
      FieldValueValidator validator, CodeRuleService codes, AuthorizationService authorization,
      ObjectProvider<Clock> clocks) {
    this(records, metadata, validator, codes, authorization,
        clocks.getIfAvailable(Clock::systemUTC));
  }

  @Transactional
  public RecordDraft create(RecordDraftCommand command) {
    UserPrincipal actor = editor();
    requireCommand(command);
    requireAssignedTemplate(actor, command.masterTypeId());
    RecordView formal = formalFor(command, actor.department().id());
    String code = command.action() == RecordAction.CREATE
        ? codes.allocate(command.masterTypeId(), LocalDate.now(clock)) : formal.recordCode();
    var draft = buildDraft(0L, code, RecordStatus.DRAFT, actor, command, formal);
    return records.saveDraft(actor.department().id(), actor.id(), draft);
  }

  @Transactional
  public RecordDraft update(long draftId, RecordDraftCommand command) {
    UserPrincipal actor = editor();
    RecordDraft existing = ownedDraft(actor, draftId);
    if (existing.status() != RecordStatus.DRAFT) throw notEditable();
    requireCommand(command);
    requireUnchangedPath(existing, command);
    requireAssignedTemplate(actor, existing.masterTypeId());
    RecordView formal = formalFor(command, actor.department().id());
    var replacement = buildDraft(existing.id(), existing.recordCode(), RecordStatus.DRAFT, actor,
        command, formal);
    return records.saveDraft(actor.department().id(), actor.id(), replacement);
  }

  public RecordDraft getDraft(long draftId) {
    UserPrincipal actor = editor();
    return ownedDraft(actor, draftId);
  }

  @Transactional
  public RecordDraft copyRejected(long draftId) {
    UserPrincipal actor = editor();
    RecordDraft rejected = ownedDraft(actor, draftId);
    if (rejected.status() != RecordStatus.REJECTED) {
      throw new BusinessException(HttpStatus.CONFLICT, "Only rejected drafts can be copied");
    }
    requireAssignedTemplate(actor, rejected.masterTypeId());
    var command = toCommand(rejected);
    RecordView formal = formalFor(command, actor.department().id());
    var copy = buildDraft(0L, rejected.recordCode(), RecordStatus.DRAFT, actor, command, formal);
    return records.saveDraft(actor.department().id(), actor.id(), copy);
  }

  @Transactional
  public RecordDraft logicalDelete(long recordId, String reason) {
    UserPrincipal actor = editor();
    String deleteReason = reason == null ? "" : reason.trim();
    if (deleteReason.isEmpty()) throw BusinessException.badRequest("Delete reason is required");
    if (deleteReason.length() > 1000) throw BusinessException.badRequest("Delete reason is too long");
    RecordView formal = records.findRecord(actor.department().id(), recordId);
    requireAssignedTemplate(actor, formal.masterTypeId());
    requireActive(formal);
    var children = formal.children().stream().map(group -> new RecordDraft.ChildRows(
        group.subTypeId(), group.rows().stream().map(row ->
            new RecordDraft.ChildRow(row.id(), row.rowOrder(), row.values())).toList())).toList();
    var draft = new RecordDraft(0L, formal.id(), formal.masterTypeId(), formal.departmentId(),
        formal.recordCode(), RecordAction.DELETE, formal.version(), formal.masterValues(), children,
        RecordStatus.DRAFT, actor.id(), deleteReason);
    return records.saveDraft(actor.department().id(), actor.id(), draft);
  }

  private RecordDraft buildDraft(long id, String recordCode, RecordStatus status,
      UserPrincipal actor, RecordDraftCommand command, RecordView formal) {
    validator.validate(metadata.findMasterFields(actor.department().id(), command.masterTypeId()),
        command.masterValues());
    var children = validatedChildren(actor.department().id(), command.masterTypeId(),
        command.children(), command.action(), formal);
    return new RecordDraft(id, command.recordId(), command.masterTypeId(), actor.department().id(),
        recordCode, command.action(), command.baseVersion(), command.masterValues(), children,
        status, actor.id(), normalizeReason(command.deleteReason()));
  }

  private List<RecordDraft.ChildRows> validatedChildren(long departmentId, long masterTypeId,
      List<RecordDraftCommand.ChildRows> submitted, RecordAction action, RecordView formal) {
    Map<Long, SubType> active = new LinkedHashMap<>();
    metadata.findSubTypes(departmentId, masterTypeId).forEach(type -> active.put(type.id(), type));
    Map<Long, RecordDraftCommand.ChildRows> groups = new HashMap<>();
    for (var group : submitted) {
      if (group == null || !active.containsKey(group.subTypeId())) {
        long id = group == null ? 0 : group.subTypeId();
        throw BusinessException.badRequest("Unknown or inactive sub type: " + id);
      }
      if (groups.putIfAbsent(group.subTypeId(), group) != null) {
        throw BusinessException.badRequest("Duplicate child group: " + group.subTypeId());
      }
    }
    Map<Long, Long> formalRowTypes = formalRowTypes(formal);
    Set<Long> childRecordIds = new HashSet<>();
    var result = new ArrayList<RecordDraft.ChildRows>();
    for (SubType type : active.values()) {
      var group = groups.get(type.id());
      if (group == null) continue;
      Set<Integer> orders = new HashSet<>();
      var rows = new ArrayList<RecordDraft.ChildRow>();
      for (var row : group.rows()) {
        if (row == null) throw BusinessException.badRequest("Child row is required");
        if (row.rowOrder() < 0 || !orders.add(row.rowOrder())) {
          throw BusinessException.badRequest("Invalid child row order: " + row.rowOrder());
        }
        if (row.recordId() != null && !childRecordIds.add(row.recordId())) {
          throw BusinessException.badRequest("Duplicate child record id: " + row.recordId());
        }
        requireChildIdentity(action, type.id(), row.recordId(), formalRowTypes);
        validator.validate(metadata.findSubFields(departmentId, type.id()), row.values());
        rows.add(new RecordDraft.ChildRow(row.recordId(), row.rowOrder(), row.values()));
      }
      rows.sort(Comparator.comparingInt(RecordDraft.ChildRow::rowOrder));
      result.add(new RecordDraft.ChildRows(type.id(), rows));
    }
    return List.copyOf(result);
  }

  private void requireChildIdentity(RecordAction action, long subTypeId, Long rowId,
      Map<Long, Long> formalRowTypes) {
    if (action == RecordAction.CREATE && rowId != null) {
      throw BusinessException.badRequest("New records cannot reference existing child rows");
    }
    if (rowId != null && !Objects.equals(formalRowTypes.get(rowId), subTypeId)) {
      throw BusinessException.badRequest("Child row does not belong to the target record: " + rowId);
    }
  }

  private Map<Long, Long> formalRowTypes(RecordView formal) {
    var result = new HashMap<Long, Long>();
    if (formal == null) return result;
    formal.children().forEach(group -> group.rows().forEach(row ->
        result.put(row.id(), group.subTypeId())));
    return result;
  }

  private RecordView formalFor(RecordDraftCommand command, long departmentId) {
    if (command.action() == RecordAction.CREATE) {
      if (command.recordId() != null || command.baseVersion() != 0) {
        throw BusinessException.badRequest("Create draft cannot target an existing record");
      }
      return null;
    }
    if (command.recordId() == null || command.recordId() <= 0) {
      throw BusinessException.badRequest("Target record is required");
    }
    RecordView formal = records.findRecord(departmentId, command.recordId());
    if (formal.masterTypeId() != command.masterTypeId()
        || formal.departmentId() != departmentId) {
      throw BusinessException.forbidden();
    }
    requireActive(formal);
    if (formal.version() != command.baseVersion()) {
      throw new BusinessException(HttpStatus.CONFLICT, "Record version changed");
    }
    return formal;
  }

  private void requireUnchangedPath(RecordDraft existing, RecordDraftCommand command) {
    if (!Objects.equals(existing.recordId(), command.recordId())
        || existing.masterTypeId() != command.masterTypeId()
        || existing.action() != command.action()
        || existing.baseVersion() != command.baseVersion()) {
      throw BusinessException.badRequest("Draft identity cannot be changed");
    }
  }

  private void requireActive(RecordView formal) {
    if (!"ACTIVE".equals(formal.status())) {
      throw new BusinessException(HttpStatus.CONFLICT, "Record is no longer active");
    }
  }

  private void requireCommand(RecordDraftCommand command) {
    if (command == null || command.action() == null) {
      throw BusinessException.badRequest("Draft command is required");
    }
    if (command.action() == RecordAction.DELETE) {
      throw BusinessException.badRequest("Delete drafts must be created through logical delete");
    }
  }

  private void requireAssignedTemplate(UserPrincipal actor, long masterTypeId) {
    if (metadata.findAssignedMasterType(actor.department().id()).id() != masterTypeId) {
      throw BusinessException.forbidden();
    }
  }

  private RecordDraftCommand toCommand(RecordDraft draft) {
    var children = draft.children().stream().map(group -> new RecordDraftCommand.ChildRows(
        group.subTypeId(), group.rows().stream().map(row ->
            new RecordDraftCommand.ChildRowCommand(row.recordId(), row.rowOrder(), row.values()))
            .toList())).toList();
    return new RecordDraftCommand(draft.recordId(), draft.masterTypeId(), draft.baseVersion(),
        draft.action(), draft.masterValues(), children, draft.deleteReason());
  }

  private String normalizeReason(String reason) {
    return reason == null ? null : reason.trim();
  }

  private RecordDraft ownedDraft(UserPrincipal actor, long draftId) {
    RecordDraft draft = records.findDraft(actor.department().id(), draftId);
    if (draft.createdBy() != actor.id()) throw BusinessException.forbidden();
    return draft;
  }

  private UserPrincipal editor() {
    UserPrincipal actor = authorization.requireRole(Role.DEPT_EDITOR);
    if (actor.department() == null) throw BusinessException.forbidden();
    authorization.requireDepartment(actor.department().id());
    return actor;
  }

  private BusinessException notEditable() {
    return new BusinessException(HttpStatus.CONFLICT, "Draft is no longer editable");
  }
}
