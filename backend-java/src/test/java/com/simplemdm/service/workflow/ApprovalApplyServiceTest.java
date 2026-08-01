package com.simplemdm.service.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.integration.PushEndpoint;
import com.simplemdm.model.integration.PushLog;
import com.simplemdm.model.integration.PushSubscription;
import com.simplemdm.model.mdm.ChildFieldDefinition;
import com.simplemdm.model.mdm.ChildRecord;
import com.simplemdm.model.mdm.ChildRecordValue;
import com.simplemdm.model.mdm.ChildType;
import com.simplemdm.model.mdm.FieldDataType;
import com.simplemdm.model.mdm.FieldDefinition;
import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.model.mdm.ObjectType;
import com.simplemdm.model.mdm.RecordValue;
import com.simplemdm.model.mdm.TypedValue;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.model.workflow.ApprovalChange;
import com.simplemdm.model.workflow.ApprovalChildChange;
import com.simplemdm.model.workflow.ApprovalChildValueChange;
import com.simplemdm.model.workflow.ApprovalRequest;
import com.simplemdm.model.workflow.ApproverAssignment;
import com.simplemdm.repository.mdm.ChildFieldDefinitionRepository;
import com.simplemdm.repository.mdm.ChildRecordRepository;
import com.simplemdm.repository.mdm.ChildRecordValueRepository;
import com.simplemdm.repository.mdm.ChildTypeRepository;
import com.simplemdm.repository.mdm.FieldDefinitionRepository;
import com.simplemdm.repository.mdm.MdmRecordRepository;
import com.simplemdm.repository.mdm.ObjectTypeRepository;
import com.simplemdm.repository.mdm.RecordValueRepository;
import com.simplemdm.repository.integration.PushEndpointRepository;
import com.simplemdm.repository.integration.PushLogOutboxWriter;
import com.simplemdm.repository.integration.PushLogRepository;
import com.simplemdm.repository.integration.PushSubscriptionRepository;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.workflow.ApprovalActionRepository;
import com.simplemdm.repository.workflow.ApprovalChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildChangeRepository;
import com.simplemdm.repository.workflow.ApprovalChildValueChangeRepository;
import com.simplemdm.repository.workflow.ApprovalRequestRepository;
import com.simplemdm.repository.workflow.ApproverAssignmentRepository;
import com.simplemdm.service.mdm.CreateFieldCommand;
import com.simplemdm.service.mdm.CurrentUserProvider;
import com.simplemdm.service.mdm.RecordView;
import com.simplemdm.service.mdm.TypedValueConverter;
import com.simplemdm.service.integration.PushEventService;
import com.simplemdm.service.system.AuthorizationService;
import com.simplemdm.service.system.DepartmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.url=jdbc:h2:mem:approval-apply;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "simple-mdm.push.request-snapshot-limit=1024"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.simplemdm.model")
@EnableJpaRepositories(basePackages = "com.simplemdm.repository")
@Import({ApprovalApplyService.class, DepartmentService.class, TypedValueConverter.class,
    PushEventService.class, PushLogOutboxWriter.class, ApprovalApplyServiceTest.JsonConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApprovalApplyServiceTest {
    @Autowired private ApprovalApplyService service;
    @Autowired private SystemRepository systems;
    @Autowired private UserRepository users;
    @Autowired private ObjectTypeRepository objectTypes;
    @Autowired private FieldDefinitionRepository fields;
    @Autowired private ChildTypeRepository childTypes;
    @Autowired private ChildFieldDefinitionRepository childFields;
    @Autowired private MdmRecordRepository records;
    @Autowired private RecordValueRepository values;
    @Autowired private ChildRecordRepository childRecords;
    @Autowired private ChildRecordValueRepository childValues;
    @Autowired private ApprovalRequestRepository requests;
    @Autowired private ApprovalChangeRepository changes;
    @Autowired private ApprovalChildChangeRepository childChanges;
    @Autowired private ApprovalChildValueChangeRepository childValueChanges;
    @Autowired private ApproverAssignmentRepository assignments;
    @Autowired private ApprovalActionRepository actions;
    @Autowired private PushEndpointRepository pushEndpoints;
    @Autowired private PushSubscriptionRepository pushSubscriptions;
    @Autowired private PushLogRepository pushLogs;
    @Autowired private PushEventService pushEvents;
    @Autowired private DepartmentService departmentService;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockBean private CurrentUserProvider currentUser;
    @MockBean private AuthorizationService authorization;

    private final AtomicLong currentActor = new AtomicLong();
    private TransactionTemplate tx;
    private Long systemId;
    private Long departmentId;
    private Long objectTypeId;
    private Long approverOne;
    private Long approverTwo;
    private Long masterFieldId;
    private Long childTypeId;
    private Long childFieldId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        inTx(() -> {
            SystemEntity system = systems.saveAndFlush(SystemEntity.create("SYS", "System"));
            systemId = system.getId();
            Department department = departmentService.create(systemId, null, "HR", "HR");
            departmentId = department.getId();
            User first = users.saveAndFlush(User.create(system, department, "approver1", "hash", "Approver 1"));
            User second = users.saveAndFlush(User.create(system, department, "approver2", "hash", "Approver 2"));
            approverOne = first.getId();
            approverTwo = second.getId();
            ObjectType person = objectTypes.saveAndFlush(ObjectType.create(system, "person", "Person"));
            objectTypeId = person.getId();
            FieldDefinition name = FieldDefinition.create(objectTypeId, person,
                new CreateFieldCommand("name", "Name", FieldDataType.STRING, true, false,
                    false, false, 128, null, null, null, null, null, 0), null);
            entityManager.persist(name);
            entityManager.flush();
            masterFieldId = name.getId();
            ChildType phone = childTypes.saveAndFlush(ChildType.create(objectTypeId, person, "phone", "Phone"));
            childTypeId = phone.getId();
            ChildFieldDefinition number = ChildFieldDefinition.create(childTypeId, phone,
                new CreateFieldCommand("number", "Number", FieldDataType.STRING, true, false,
                    false, false, 64, null, null, null, null, null, 0), null);
            entityManager.persist(number);
            entityManager.flush();
            childFieldId = number.getId();
            persistAssignment(approverOne);
            persistAssignment(approverTwo);
            return null;
        });
        currentActor.set(approverOne);
        when(currentUser.currentSystemUserId()).thenAnswer(invocation -> Optional.of(currentActor.get()));
        when(authorization.canInStrictSelfScope(anyLong(), eq("APPROVAL_REVIEW"), eq(departmentId)))
            .thenReturn(true);
    }

    @Test
    void approvesMasterCreateAndChildCreateWithoutAnyPreexistingEffectiveRecord() throws Exception {
        Long requestId = inTx(() -> {
            createPushSubscription();
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-001", departmentId, approverOne, null));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                TypedValue.empty(), typed("Alice")));
            ApprovalChildChange child = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                request.getId(), "phone:0", childTypeId, null, ApprovalChildChange.Operation.CREATE, null, 0));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, child.getId(), childFieldId,
                TypedValue.empty(), typed("123")));
            return request.getId();
        });

        RecordView result = service.approve(requestId, approverOne);

        assertThat(result.recordCode()).isEqualTo("EMP-001");
        assertThat(result.departmentId()).isEqualTo(departmentId);
        String requestSnapshot = inTx(() -> {
            ApprovalRequest applied = requests.findById(requestId).orElseThrow();
            assertThat(applied.getStatus()).isEqualTo("APPROVED");
            assertThat(applied.getRecordId()).isEqualTo(result.id());
            assertThat(values.findByRecordId(result.id())).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            ChildRecord child = childRecords.findAll().get(0);
            assertThat(child.getRecordId()).isEqualTo(result.id());
            assertThat(child.getDepartmentId()).isEqualTo(departmentId);
            assertThat(childValues.findByChildRecordIdIn(List.of(child.getId()))).singleElement()
                .extracting(ChildRecordValue::typedValue).isEqualTo(typed("123"));
            PushLog log = pushLogs.findAll().get(0);
            assertThat(log.getEventId()).isEqualTo(
                "record:" + result.id() + ":version:" + records.findById(result.id()).orElseThrow().getVersion());
            return log.getRequestSnapshot();
        });
        JsonNode snapshot = json.readTree(requestSnapshot);
        assertThat(snapshot.path("data").path("name").asText()).isEqualTo("Alice");
        assertThat(snapshot.path("children").get(0).path("child_type").asText()).isEqualTo("phone");
        assertThat(snapshot.path("children").get(0).path("data").path("number").asText())
            .isEqualTo("123");

        pushEvents.enqueueApprovedRecord(result.id(), approverOne);
        assertThat(inTx(() -> pushLogs.findAll().size())).isEqualTo(1);
    }

    @Test
    void pendingCreateRemainsApprovableWhenARequiredFieldIsAddedAfterSubmission() {
        Long[] ids = inTx(() -> {
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-METADATA-ADDED", departmentId, approverOne, null));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                TypedValue.empty(), typed("Alice")));
            ObjectType type = objectTypes.findById(objectTypeId).orElseThrow();
            FieldDefinition level = FieldDefinition.create(objectTypeId, type,
                new CreateFieldCommand("level", "Level", FieldDataType.STRING, true, false,
                    false, false, 32, null, null, null, null, null, 1), null);
            fields.saveAndFlush(level);
            return new Long[]{request.getId(), level.getId()};
        });

        RecordView result = service.approve(ids[0], approverOne);

        inTx(() -> {
            assertThat(requests.findById(ids[0]).orElseThrow().getStatus()).isEqualTo("APPROVED");
            assertThat(values.findByRecordId(result.id())).hasSize(2);
            assertThat(values.findByRecordIdAndFieldDefinitionId(result.id(), ids[1])).get()
                .extracting(RecordValue::typedValue).isEqualTo(TypedValue.empty());
            return null;
        });
    }

    @Test
    void approvedUpdateCreatesTheMissingValueRowForAFieldAddedAfterTheRecord() {
        Long[] ids = inTx(() -> {
            MdmRecord record = createRecord("EMP-NEW-FIELD", "Alice");
            ObjectType type = objectTypes.findById(objectTypeId).orElseThrow();
            FieldDefinition level = FieldDefinition.create(objectTypeId, type,
                new CreateFieldCommand("level", "Level", FieldDataType.STRING, true, false,
                    false, false, 32, null, null, null, null, null, 1), null);
            fields.saveAndFlush(level);
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), level.getId(),
                TypedValue.empty(), typed("L3")));
            return new Long[]{request.getId(), record.getId(), level.getId()};
        });

        service.approve(ids[0], approverOne);

        inTx(() -> {
            assertThat(values.findByRecordIdAndFieldDefinitionId(ids[1], ids[2])).get()
                .extracting(RecordValue::typedValue).isEqualTo(typed("L3"));
            return null;
        });
    }

    @Test
    void oversizedRealPushSnapshotRollsBackEffectiveMutationApprovalStatusAndAction() {
        Long[] ids = inTx(() -> {
            createPushSubscription();
            ObjectType type = objectTypes.findById(objectTypeId).orElseThrow();
            FieldDefinition biography = FieldDefinition.create(objectTypeId, type,
                new CreateFieldCommand("biography", "Biography", FieldDataType.TEXT, false, false,
                    false, false, 4096, null, null, null, null, null, 1), null);
            entityManager.persist(biography);
            entityManager.flush();
            MdmRecord record = createRecord("EMP-PUSH-ROLLBACK", "Alice");
            values.saveAndFlush(RecordValue.create(record, biography, text("short"), approverOne));
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), biography.getId(),
                text("short"), text("x".repeat(2000))));
            return new Long[]{request.getId(), record.getId(), biography.getId()};
        });

        assertThatThrownBy(() -> service.approve(ids[0], approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(413));

        inTx(() -> {
            assertThat(values.findByRecordIdAndFieldDefinitionId(ids[1], ids[2])).get()
                .extracting(RecordValue::typedValue).isEqualTo(text("short"));
            assertThat(requests.findById(ids[0]).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(actions.findAll()).isEmpty();
            assertThat(pushLogs.findAll()).isEmpty();
            return null;
        });
    }

    @Test
    void nonDuplicateOutboxConstraintFailureRollsBackApprovalAndEffectiveMutation() {
        Long[] ids = inTx(() -> {
            createPushSubscription();
            MdmRecord record = createRecord("EMP-OUTBOX-CONSTRAINT", "Alice");
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Must Roll Back")));
            return new Long[]{request.getId(), record.getId()};
        });
        jdbc.execute("ALTER TABLE sys_push_log ADD CONSTRAINT ck_push_log_no_record_event "
            + "CHECK (event_id NOT LIKE 'record:%')");

        assertThatThrownBy(() -> service.approve(ids[0], approverOne))
            .isInstanceOf(DataIntegrityViolationException.class);

        inTx(() -> {
            assertThat(values.findByRecordId(ids[1])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            assertThat(requests.findById(ids[0]).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(actions.findAll()).isEmpty();
            assertThat(pushLogs.findAll()).isEmpty();
            return null;
        });
    }

    @Test
    void appliesMasterAndChildCreateUpdateAndSoftDeleteWhileRetainingApprovalAudit() {
        Fixture fixture = inTx(this::existingFixtureWithAllChildOperations);

        RecordView result = service.approve(fixture.requestId(), approverOne);

        assertThat(result.id()).isEqualTo(fixture.recordId());
        inTx(() -> {
            assertThat(values.findByRecordId(fixture.recordId())).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alicia"));
            ChildRecord deleted = childRecords.findById(fixture.deletedChildId()).orElseThrow();
            assertThat(deleted.getStatus()).isEqualTo("deleted");
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(childValues.findByChildRecordIdIn(List.of(fixture.updatedChildId()))).singleElement()
                .extracting(ChildRecordValue::typedValue).isEqualTo(typed("789"));
            assertThat(childRecords.findBySystemIdAndRecordIdAndChildTypeId(systemId, fixture.recordId(), childTypeId))
                .hasSize(2);
            assertThat(childValues.findActiveByFieldDefinitionId(childFieldId))
                .extracting(ChildRecordValue::typedValue)
                .containsExactlyInAnyOrder(typed("789"), typed("999"))
                .doesNotContain(typed("456"));
            ApprovalChildChange deleteAudit = childChanges
                .findByApprovalRequestIdOrderBySortOrderAscIdAsc(fixture.requestId()).stream()
                .filter(change -> change.getOperation() == ApprovalChildChange.Operation.DELETE)
                .findFirst().orElseThrow();
            assertThat(deleteAudit.getChildRecordId()).isEqualTo(fixture.deletedChildId());
            assertThat(childValueChanges.findByApprovalChildChangeId(deleteAudit.getId())).singleElement()
                .satisfies(value -> {
                    assertThat(value.oldValue()).isEqualTo(typed("456"));
                    assertThat(value.newValue()).isEqualTo(TypedValue.empty());
                });
            assertThat(requests.findById(fixture.requestId()).orElseThrow().getStatus()).isEqualTo("APPROVED");
            return null;
        });
    }

    @Test
    void staleMasterOrChildVersionLeavesRequestPendingAndEffectiveRowsUntouched() {
        Fixture fixture = inTx(this::existingFixtureWithAllChildOperations);
        inTx(() -> {
            MdmRecord record = records.findById(fixture.recordId()).orElseThrow();
            record.touch(approverOne);
            records.saveAndFlush(record);
            return null;
        });

        assertThatThrownBy(() -> service.approve(fixture.requestId(), approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
        inTx(() -> {
            assertThat(requests.findById(fixture.requestId()).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(values.findByRecordId(fixture.recordId())).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            return null;
        });

        Long childStaleRequest = inTx(() -> {
            MdmRecord record = records.findById(fixture.recordId()).orElseThrow();
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                fixture.recordId(), departmentId, approverOne, record.getVersion()));
            ChildRecord child = childRecords.findById(fixture.updatedChildId()).orElseThrow();
            child.touch(approverOne);
            childRecords.saveAndFlush(child);
            childChanges.save(ApprovalChildChange.create(systemId, request.getId(), "phone:stale", childTypeId,
                child.getId(), ApprovalChildChange.Operation.DELETE, child.getVersion() - 1, 0));
            return request.getId();
        });
        assertThatThrownBy(() -> service.approve(childStaleRequest, approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void lateMissingChildValueRollsBackEarlierMasterMutationAndApprovalStatus() {
        Long[] ids = inTx(() -> {
            MdmRecord record = createRecord("EMP-ROLLBACK", "Alice");
            ChildType type = childTypes.findById(childTypeId).orElseThrow();
            ChildRecord child = childRecords.saveAndFlush(ChildRecord.create(record, type, 0, approverOne));
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Changed before failure")));
            ApprovalChildChange childChange = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                request.getId(), "phone:missing-value", childTypeId, child.getId(),
                ApprovalChildChange.Operation.UPDATE, child.getVersion(), 0));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, childChange.getId(), childFieldId,
                typed("missing"), typed("will fail")));
            return new Long[]{request.getId(), record.getId()};
        });

        assertThatThrownBy(() -> service.approve(ids[0], approverOne))
            .isInstanceOf(BusinessException.class).hasMessageContaining("value row");

        inTx(() -> {
            assertThat(values.findByRecordId(ids[1])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            assertThat(requests.findById(ids[0]).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(actions.findAll()).isEmpty();
            return null;
        });
    }

    @Test
    void eitherAssignedApproverMayApproveAndSecondAttemptReturnsConflictWithoutReapply() {
        Long[] ids = inTx(() -> {
            MdmRecord record = createRecord("EMP-MULTI", "Alice");
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Alicia")));
            return new Long[]{request.getId(), record.getId()};
        });
        currentActor.set(approverTwo);
        service.approve(ids[0], approverTwo);
        currentActor.set(approverOne);

        assertThatThrownBy(() -> service.approve(ids[0], approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
        inTx(() -> {
            assertThat(values.findByRecordId(ids[1])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alicia"));
            assertThat(actions.findAll()).hasSize(1);
            return null;
        });
    }

    @Test
    void assignedApproverRejectsWithoutApplyingDataOrCreatingOutboxAndSecondDecisionConflicts() {
        Long[] ids = inTx(() -> {
            MdmRecord record = createRecord("EMP-REJECT", "Alice");
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Must not apply")));
            return new Long[]{request.getId(), record.getId()};
        });

        service.reject(ids[0], approverOne, "  数据不完整  ");

        inTx(() -> {
            ApprovalRequest request = requests.findById(ids[0]).orElseThrow();
            assertThat(request.getStatus()).isEqualTo("REJECTED");
            assertThat(request.getDecidedAt()).isNotNull();
            assertThat(values.findByRecordId(ids[1])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            assertThat(actions.findAll()).singleElement().satisfies(action -> {
                assertThat(action.getAction()).isEqualTo("REJECT");
                assertThat(action.getActorId()).isEqualTo(approverOne);
                assertThat(action.getComment()).isEqualTo("数据不完整");
            });
            assertThat(pushLogs.count()).isZero();
            return null;
        });

        currentActor.set(approverTwo);
        assertThatThrownBy(() -> service.approve(ids[0], approverTwo))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));
    }

    @Test
    void unauthorizedActorCannotDistinguishPendingFromRejectedRequests() {
        Long[] ids = inTx(() -> {
            SystemEntity system = systems.findById(systemId).orElseThrow();
            Department department = entityManager.find(Department.class, departmentId);
            User unauthorized = users.saveAndFlush(User.create(
                system, department, "unassigned-rejector", "hash", "Unassigned Rejector"));
            ApprovalRequest pending = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-REJECT-HIDDEN-PENDING", departmentId,
                approverOne, null));
            ApprovalRequest rejected = ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-REJECT-HIDDEN-DONE", departmentId,
                approverOne, null);
            rejected.reject();
            requests.saveAndFlush(rejected);
            return new Long[]{unauthorized.getId(), pending.getId(), rejected.getId()};
        });
        currentActor.set(ids[0]);
        when(authorization.canInStrictSelfScope(ids[0], "APPROVAL_REVIEW", departmentId))
            .thenReturn(false);

        BusinessException pendingFailure = catchThrowableOfType(
            () -> service.reject(ids[1], ids[0], null), BusinessException.class);
        BusinessException rejectedFailure = catchThrowableOfType(
            () -> service.reject(ids[2], ids[0], null), BusinessException.class);

        assertThat(pendingFailure.getCode()).isEqualTo(404);
        assertThat(rejectedFailure.getCode()).isEqualTo(404);
        assertThat(pendingFailure.getMessage()).isEqualTo(rejectedFailure.getMessage());
    }

    @Test
    void unauthorizedActorCannotDistinguishPendingFromApprovedRequests() {
        Long[] ids = inTx(() -> {
            SystemEntity system = systems.findById(systemId).orElseThrow();
            Department department = entityManager.find(Department.class, departmentId);
            User unauthorized = users.saveAndFlush(User.create(
                system, department, "unassigned-reviewer", "hash", "Unassigned Reviewer"));
            ApprovalRequest pending = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-INVISIBLE-PENDING", departmentId,
                approverOne, null));
            ApprovalRequest approved = ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-INVISIBLE-APPROVED", departmentId,
                approverOne, null);
            approved.approve();
            requests.saveAndFlush(approved);
            return new Long[]{unauthorized.getId(), pending.getId(), approved.getId()};
        });
        currentActor.set(ids[0]);
        when(authorization.canInStrictSelfScope(ids[0], "APPROVAL_REVIEW", departmentId))
            .thenReturn(false);

        BusinessException pendingFailure = catchThrowableOfType(
            () -> service.approve(ids[1], ids[0]), BusinessException.class);
        BusinessException approvedFailure = catchThrowableOfType(
            () -> service.approve(ids[2], ids[0]), BusinessException.class);

        assertThat(pendingFailure.getCode()).isEqualTo(404);
        assertThat(approvedFailure.getCode()).isEqualTo(404);
        assertThat(pendingFailure.getMessage()).isEqualTo(approvedFailure.getMessage());
    }

    @Test
    void rejectsClaimedApproverDifferentFromJwtActorBeforeLockingOrWriting() {
        assertThatThrownBy(() -> service.approve(999L, approverTwo))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(403));
        assertThat(actions.findAll()).isEmpty();
    }

    @Test
    void crossSystemApprovalIdIsUniformNotFound() {
        Long foreignRequestId = inTx(() -> {
            SystemEntity foreignSystem = systems.saveAndFlush(SystemEntity.create("FOREIGN", "Foreign"));
            Department foreignDepartment = departmentService.create(foreignSystem.getId(), null, "FOREIGN", "Foreign");
            User foreignUser = users.saveAndFlush(User.create(foreignSystem, foreignDepartment,
                "foreign-user", "hash", "Foreign User"));
            ObjectType foreignType = objectTypes.saveAndFlush(ObjectType.create(
                foreignSystem, "foreign-person", "Foreign Person"));
            return requests.saveAndFlush(ApprovalRequest.pending(foreignSystem.getId(), foreignType.getId(),
                ApprovalRequest.Operation.CREATE, null, "FOREIGN-1", foreignDepartment.getId(),
                foreignUser.getId(), null)).getId();
        });

        assertThatThrownBy(() -> service.approve(foreignRequestId, approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(404));
    }

    @Test
    void currentMasterMaxLengthMetadataIsRevalidatedBeforeApply() {
        Long[] ids = inTx(() -> {
            MdmRecord record = createRecord("EMP-METADATA-MASTER", "Alice");
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Alicia")));
            FieldDefinition field = fields.findById(masterFieldId).orElseThrow();
            ReflectionTestUtils.setField(field, "maxLength", 3);
            entityManager.flush();
            return new Long[]{request.getId(), record.getId()};
        });

        assertThatThrownBy(() -> service.approve(ids[0], approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));

        inTx(() -> {
            assertThat(requests.findById(ids[0]).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(values.findByRecordId(ids[1])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            return null;
        });
    }

    @Test
    void decimalValueRoundTripsAcrossPhysicalAndMetadataScaleWithoutFalseConflict() {
        Long[] ids = inTx(() -> {
            ObjectType type = objectTypes.findById(objectTypeId).orElseThrow();
            FieldDefinition amount = FieldDefinition.create(objectTypeId, type,
                new CreateFieldCommand("amount", "Amount", FieldDataType.DECIMAL, false, false,
                    false, false, null, 10, 2, null, null, null, 1), null);
            entityManager.persist(amount);
            entityManager.flush();
            MdmRecord record = createRecord("EMP-DECIMAL", "Alice");
            values.saveAndFlush(RecordValue.create(record, amount, decimal("12.34"), approverOne));
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), amount.getId(),
                decimal("12.34"), decimal("56.78")));
            return new Long[]{request.getId(), record.getId(), amount.getId()};
        });

        service.approve(ids[0], approverOne);

        inTx(() -> {
            RecordValue amount = values.findByRecordIdAndFieldDefinitionId(ids[1], ids[2]).orElseThrow();
            assertThat(amount.typedValue().decimalValue()).isEqualByComparingTo("56.78");
            return null;
        });
    }

    @Test
    void currentChildDataTypeMetadataIsRevalidatedBeforeApply() {
        Long requestId = inTx(() -> {
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-METADATA-CHILD", departmentId, approverOne, null));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                TypedValue.empty(), typed("Alice")));
            ApprovalChildChange child = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                request.getId(), "phone:metadata", childTypeId, null,
                ApprovalChildChange.Operation.CREATE, null, 0));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, child.getId(), childFieldId,
                TypedValue.empty(), typed("not-an-integer")));
            ChildFieldDefinition field = childFields.findById(childFieldId).orElseThrow();
            ReflectionTestUtils.setField(field, "dataType", FieldDataType.INTEGER);
            entityManager.flush();
            return request.getId();
        });

        assertThatThrownBy(() -> service.approve(requestId, approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));

        inTx(() -> {
            assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(records.findAll()).isEmpty();
            return null;
        });
    }

    @Test
    void rejectsDuplicatePersistedChildTargetBeforeAnyMutation() {
        Long[] ids = inTx(() -> {
            MdmRecord record = createRecord("EMP-DUP-CHILD", "Alice");
            ChildType type = childTypes.findById(childTypeId).orElseThrow();
            ChildFieldDefinition field = childFields.findById(childFieldId).orElseThrow();
            ChildRecord child = childRecords.saveAndFlush(ChildRecord.create(record, type, 0, approverOne));
            childValues.save(ChildRecordValue.create(child, field, typed("111"), approverOne));
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            ApprovalChildChange first = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                request.getId(), "phone:first", childTypeId, child.getId(),
                ApprovalChildChange.Operation.UPDATE, child.getVersion(), 0));
            ApprovalChildChange second = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                request.getId(), "phone:second", childTypeId, child.getId(),
                ApprovalChildChange.Operation.DELETE, child.getVersion(), 1));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, first.getId(), childFieldId,
                typed("111"), typed("222")));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, second.getId(), childFieldId,
                typed("111"), TypedValue.empty()));
            return new Long[]{request.getId(), child.getId()};
        });

        assertThatThrownBy(() -> service.approve(ids[0], approverOne))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(409));

        inTx(() -> {
            assertThat(childValues.findByChildRecordIdIn(List.of(ids[1]))).singleElement()
                .extracting(ChildRecordValue::typedValue).isEqualTo(typed("111"));
            assertThat(requests.findById(ids[0]).orElseThrow().getStatus()).isEqualTo("PENDING");
            return null;
        });
    }

    @Test
    void systemAdministratorStillRequiresAssignmentAndExactSelfReviewPermission() {
        Long[] ids = inTx(() -> {
            SystemEntity system = systems.findById(systemId).orElseThrow();
            Department department = entityManager.find(Department.class, departmentId);
            User admin = User.create(system, department, "unassigned-admin", "hash", "Unassigned Admin");
            admin.makeSystemAdmin();
            users.saveAndFlush(admin);
            MdmRecord record = createRecord("EMP-ADMIN-DENY", "Alice");
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Forbidden")));
            return new Long[]{admin.getId(), request.getId(), record.getId()};
        });
        currentActor.set(ids[0]);
        when(authorization.canInStrictSelfScope(ids[0], "APPROVAL_REVIEW", departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(ids[1], ids[0]))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(404));

        inTx(() -> {
            assertThat(requests.findById(ids[1]).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(values.findByRecordId(ids[2])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Alice"));
            return null;
        });
    }

    @Test
    void assignedSystemAdministratorWithExactSelfReviewPermissionMayApprove() {
        Long[] ids = inTx(() -> {
            SystemEntity system = systems.findById(systemId).orElseThrow();
            Department department = entityManager.find(Department.class, departmentId);
            User admin = User.create(system, department, "assigned-admin", "hash", "Assigned Admin");
            admin.makeSystemAdmin();
            users.saveAndFlush(admin);
            persistAssignment(admin.getId());
            MdmRecord record = createRecord("EMP-ADMIN-ALLOW", "Alice");
            ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                record.getId(), departmentId, approverOne, record.getVersion()));
            changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
                typed("Alice"), typed("Approved")));
            return new Long[]{admin.getId(), request.getId(), record.getId()};
        });
        currentActor.set(ids[0]);
        when(authorization.canInStrictSelfScope(ids[0], "APPROVAL_REVIEW", departmentId)).thenReturn(true);

        service.approve(ids[1], ids[0]);

        inTx(() -> {
            assertThat(requests.findById(ids[1]).orElseThrow().getStatus()).isEqualTo("APPROVED");
            assertThat(values.findByRecordId(ids[2])).singleElement()
                .extracting(RecordValue::typedValue).isEqualTo(typed("Approved"));
            return null;
        });
    }

    @Test
    void concurrentApprovalsCannotCommitDuplicateTypedMasterOrChildValuesUnderRepeatableRead() throws Exception {
        Long[] recordCodeRequests = inTx(() -> {
            ApprovalRequest first = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-CODE-DUP", departmentId, approverOne, null));
            ApprovalRequest second = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                ApprovalRequest.Operation.CREATE, null, "EMP-CODE-DUP", departmentId, approverOne, null));
            changes.save(ApprovalChange.create(systemId, first.getId(), masterFieldId,
                TypedValue.empty(), typed("First")));
            changes.save(ApprovalChange.create(systemId, second.getId(), masterFieldId,
                TypedValue.empty(), typed("Second")));
            return new Long[]{first.getId(), second.getId()};
        });

        assertThat(approveConcurrently(recordCodeRequests)).containsExactlyInAnyOrder(200, 409);
        inTx(() -> {
            assertThat(records.findAll().stream()
                .filter(record -> "EMP-CODE-DUP".equals(record.getRecordCode()))).hasSize(1);
            return null;
        });

        Long[] masterRequests = inTx(() -> {
            FieldDefinition uniqueName = fields.findById(masterFieldId).orElseThrow();
            ReflectionTestUtils.setField(uniqueName, "uniqueValue", true);
            MdmRecord first = createRecord("EMP-UNIQUE-M1", "Alice");
            MdmRecord second = createRecord("EMP-UNIQUE-M2", "Bob");
            ApprovalRequest firstRequest = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                first.getId(), departmentId, approverOne, first.getVersion()));
            ApprovalRequest secondRequest = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                second.getId(), departmentId, approverOne, second.getVersion()));
            changes.save(ApprovalChange.create(systemId, firstRequest.getId(), masterFieldId,
                typed("Alice"), typed("MASTER-DUP")));
            changes.save(ApprovalChange.create(systemId, secondRequest.getId(), masterFieldId,
                typed("Bob"), typed("MASTER-DUP")));
            entityManager.flush();
            return new Long[]{firstRequest.getId(), secondRequest.getId()};
        });

        assertThat(approveConcurrently(masterRequests)).containsExactlyInAnyOrder(200, 409);
        inTx(() -> {
            assertThat(values.findByFieldDefinitionId(masterFieldId).stream()
                .filter(value -> value.typedValue().equals(typed("MASTER-DUP")))).hasSize(1);
            return null;
        });

        Long[] childRequests = inTx(() -> {
            ChildFieldDefinition uniqueNumber = childFields.findById(childFieldId).orElseThrow();
            ReflectionTestUtils.setField(uniqueNumber, "uniqueValue", true);
            ChildType type = childTypes.findById(childTypeId).orElseThrow();
            MdmRecord first = createRecord("EMP-UNIQUE-C1", "Carol");
            MdmRecord second = createRecord("EMP-UNIQUE-C2", "Dave");
            ChildRecord firstChild = childRecords.saveAndFlush(ChildRecord.create(first, type, 0, approverOne));
            ChildRecord secondChild = childRecords.saveAndFlush(ChildRecord.create(second, type, 0, approverOne));
            childValues.save(ChildRecordValue.create(firstChild, uniqueNumber, typed("111"), approverOne));
            childValues.save(ChildRecordValue.create(secondChild, uniqueNumber, typed("222"), approverOne));
            ApprovalRequest firstRequest = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                first.getId(), departmentId, approverOne, first.getVersion()));
            ApprovalRequest secondRequest = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
                second.getId(), departmentId, approverOne, second.getVersion()));
            ApprovalChildChange firstChange = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                firstRequest.getId(), "phone:first", childTypeId, firstChild.getId(),
                ApprovalChildChange.Operation.UPDATE, firstChild.getVersion(), 0));
            ApprovalChildChange secondChange = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
                secondRequest.getId(), "phone:second", childTypeId, secondChild.getId(),
                ApprovalChildChange.Operation.UPDATE, secondChild.getVersion(), 0));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, firstChange.getId(), childFieldId,
                typed("111"), typed("CHILD-DUP")));
            childValueChanges.save(ApprovalChildValueChange.create(systemId, secondChange.getId(), childFieldId,
                typed("222"), typed("CHILD-DUP")));
            entityManager.flush();
            return new Long[]{firstRequest.getId(), secondRequest.getId()};
        });

        assertThat(approveConcurrently(childRequests)).containsExactlyInAnyOrder(200, 409);
        inTx(() -> {
            assertThat(childValues.findByFieldDefinitionId(childFieldId).stream()
                .filter(value -> value.typedValue().equals(typed("CHILD-DUP")))).hasSize(1);
            return null;
        });
    }

    private Fixture existingFixtureWithAllChildOperations() {
        MdmRecord record = createRecord("EMP-UPDATE", "Alice");
        ChildType type = childTypes.findById(childTypeId).orElseThrow();
        ChildFieldDefinition number = childFields.findById(childFieldId).orElseThrow();
        ChildRecord updated = childRecords.saveAndFlush(ChildRecord.create(record, type, 0, approverOne));
        childValues.save(ChildRecordValue.create(updated, number, typed("123"), approverOne));
        ChildRecord deleted = childRecords.saveAndFlush(ChildRecord.create(record, type, 1, approverOne));
        childValues.save(ChildRecordValue.create(deleted, number, typed("456"), approverOne));
        ApprovalRequest request = requests.saveAndFlush(ApprovalRequest.pending(systemId, objectTypeId,
            record.getId(), departmentId, approverOne, record.getVersion()));
        changes.save(ApprovalChange.create(systemId, request.getId(), masterFieldId,
            typed("Alice"), typed("Alicia")));
        ApprovalChildChange created = childChanges.saveAndFlush(ApprovalChildChange.create(systemId, request.getId(),
            "phone:create", childTypeId, null, ApprovalChildChange.Operation.CREATE, null, 2));
        childValueChanges.save(ApprovalChildValueChange.create(systemId, created.getId(), childFieldId,
            TypedValue.empty(), typed("999")));
        ApprovalChildChange changed = childChanges.saveAndFlush(ApprovalChildChange.create(systemId, request.getId(),
            "phone:update", childTypeId, updated.getId(), ApprovalChildChange.Operation.UPDATE,
            updated.getVersion(), 0));
        childValueChanges.save(ApprovalChildValueChange.create(systemId, changed.getId(), childFieldId,
            typed("123"), typed("789")));
        ApprovalChildChange removed = childChanges.saveAndFlush(ApprovalChildChange.create(systemId,
            request.getId(), "phone:delete", childTypeId, deleted.getId(),
            ApprovalChildChange.Operation.DELETE, deleted.getVersion(), 1));
        childValueChanges.save(ApprovalChildValueChange.create(systemId, removed.getId(), childFieldId,
            typed("456"), TypedValue.empty()));
        return new Fixture(request.getId(), record.getId(), updated.getId(), deleted.getId());
    }

    private MdmRecord createRecord(String code, String name) {
        ObjectType type = objectTypes.findById(objectTypeId).orElseThrow();
        Department department = entityManager.find(Department.class, departmentId);
        FieldDefinition field = fields.findById(masterFieldId).orElseThrow();
        MdmRecord record = records.saveAndFlush(MdmRecord.create(systemId, type, objectTypeId,
            department, code, approverOne));
        values.saveAndFlush(RecordValue.create(record, field, typed(name), approverOne));
        return record;
    }

    private PushSubscription createPushSubscription() {
        PushEndpoint endpoint = pushEndpoints.saveAndFlush(PushEndpoint.create(
            systemId, "ERP", "ERP", "https://public.example/hook", "NONE"));
        return pushSubscriptions.saveAndFlush(PushSubscription.active(
            null, systemId, endpoint.getId(), objectTypeId, "RECORD_CHANGED"));
    }

    private void persistAssignment(Long userId) {
        ApproverAssignment assignment = newAssignment();
        ReflectionTestUtils.setField(assignment, "systemId", systemId);
        ReflectionTestUtils.setField(assignment, "objectTypeId", objectTypeId);
        ReflectionTestUtils.setField(assignment, "departmentId", departmentId);
        ReflectionTestUtils.setField(assignment, "approverUserId", userId);
        ReflectionTestUtils.setField(assignment, "status", "active");
        assignments.saveAndFlush(assignment);
    }

    private ApproverAssignment newAssignment() {
        try {
            var constructor = ApproverAssignment.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct approval assignment fixture", exception);
        }
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    private List<Integer> approveConcurrently(Long[] requestIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = java.util.Arrays.stream(requestIds).map(requestId -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Approval start timed out");
                try {
                    service.approve(requestId, approverOne);
                    return 200;
                } catch (BusinessException exception) {
                    return exception.getCode();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return results.stream().map(result -> {
                try {
                    return result.get(20, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private TypedValue typed(String value) {
        return new TypedValue(value, null, null, null, null, null, null, null);
    }

    private TypedValue text(String value) {
        return new TypedValue(null, value, null, null, null, null, null, null);
    }

    private TypedValue decimal(String value) {
        return new TypedValue(null, null, null, new BigDecimal(value), null, null, null, null);
    }

    private record Fixture(Long requestId, Long recordId, Long updatedChildId, Long deletedChildId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
