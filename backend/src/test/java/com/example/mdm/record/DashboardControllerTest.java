package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mdm.auth.AuthorizationService;
import com.example.mdm.auth.DepartmentPrincipal;
import com.example.mdm.auth.Role;
import com.example.mdm.auth.UserPrincipal;
import com.example.mdm.common.api.RequestId;
import com.example.mdm.common.error.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerTest {
  private final AuthorizationService authorization = Mockito.mock(AuthorizationService.class);
  private final MemoryDashboardStore store = new MemoryDashboardStore();
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T08:00:00Z"), ZoneOffset.UTC);

  @BeforeEach void setUp() {
    Mockito.reset(authorization);
    store.calls.clear();
  }

  @Test void departmentEditorApproverReceivesDepartmentAndActorScopedMetricsAndRecentTasks() {
    var actor = new UserPrincipal(12, "editor", "Editor",
        new DepartmentPrincipal(7, "SALES", "Sales"),
        List.of(Role.DEPT_EDITOR, Role.DEPT_APPROVER));
    when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
        Role.DEPT_VIEWER)).thenReturn(actor);

    DashboardService.DashboardSummary summary = service().summary();

    assertThat(summary.formalCount()).isEqualTo(17);
    assertThat(summary.myDraftCount()).isEqualTo(3);
    assertThat(summary.pendingApprovalCount()).isEqualTo(5);
    assertThat(summary.activatedThisMonth()).isEqualTo(4);
    assertThat(summary.recentTasks()).extracting(DashboardService.RecentTask::id)
        .containsExactly(91L);
    assertThat(store.calls).contains("formal:7", "draft:7:12", "pending:7",
        "activated:7:2026-08-01T00:00", "recent:7:null:5");
  }

  @Test void superAdminReceivesGlobalAdministrativeTotalsAndNoDepartmentDraftCount() {
    var admin = new UserPrincipal(1, "admin", "Admin", null, List.of(Role.SUPER_ADMIN));
    when(authorization.requireRole(Role.SUPER_ADMIN, Role.DEPT_EDITOR, Role.DEPT_APPROVER,
        Role.DEPT_VIEWER)).thenReturn(admin);

    DashboardService.DashboardSummary summary = service().summary();

    assertThat(summary.formalCount()).isEqualTo(117);
    assertThat(summary.myDraftCount()).isZero();
    assertThat(summary.pendingApprovalCount()).isEqualTo(15);
    assertThat(summary.activatedThisMonth()).isEqualTo(14);
    assertThat(store.calls).contains("formal:null", "pending:null",
        "activated:null:2026-08-01T00:00", "recent:null:null:5")
        .noneMatch(call -> call.startsWith("draft:"));
  }

  @Test void controllerReturnsOneSummaryEnvelopeWithRequestId() throws Exception {
    DashboardService dashboard = Mockito.mock(DashboardService.class);
    when(dashboard.summary()).thenReturn(new DashboardService.DashboardSummary(17, 3, 5, 4,
        List.of(new DashboardService.RecentTask(91, "RECORD", "RECORD", 81, "PENDING",
            12, LocalDateTime.of(2026, 8, 5, 9, 0)))));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboard))
        .setControllerAdvice(new GlobalExceptionHandler()).build();

    mvc.perform(get("/api/dashboard/summary")
            .requestAttr(RequestId.ATTRIBUTE, "req-dashboard"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.formalCount").value(17))
        .andExpect(jsonPath("$.data.myDraftCount").value(3))
        .andExpect(jsonPath("$.data.pendingApprovalCount").value(5))
        .andExpect(jsonPath("$.data.activatedThisMonth").value(4))
        .andExpect(jsonPath("$.data.recentTasks[0].taskType").value("RECORD"))
        .andExpect(jsonPath("$.requestId").value("req-dashboard"));
  }

  private DashboardService service() {
    return new DashboardService(store, authorization, clock);
  }

  private static final class MemoryDashboardStore implements DashboardService.DashboardStore {
    private final List<String> calls = new ArrayList<>();

    @Override public long formalCount(Long departmentId) {
      calls.add("formal:" + departmentId);
      return departmentId == null ? 117 : 17;
    }
    @Override public long draftCount(long departmentId, long userId) {
      calls.add("draft:" + departmentId + ":" + userId);
      return 3;
    }
    @Override public long pendingApprovalCount(Long departmentId) {
      calls.add("pending:" + departmentId);
      return departmentId == null ? 15 : 5;
    }
    @Override public long activatedSince(Long departmentId, LocalDateTime start) {
      calls.add("activated:" + departmentId + ":" + start);
      return departmentId == null ? 14 : 4;
    }
    @Override public List<DashboardService.RecentTask> recentTasks(Long departmentId,
        Long submittedBy, int limit) {
      calls.add("recent:" + departmentId + ":" + submittedBy + ":" + limit);
      return List.of(new DashboardService.RecentTask(91, "RECORD", "RECORD", 81, "PENDING",
          12, LocalDateTime.of(2026, 8, 5, 9, 0)));
    }
  }
}
