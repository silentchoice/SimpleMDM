package com.example.mdm.record;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EditLockController.class)
@AutoConfigureMockMvc(addFilters = false)
class EditLockControllerTest {
  @Autowired private MockMvc mvc;
  @MockBean private EditLockService service;

  @Test void postPutAndDeleteUseTheRecordPathAndTokenOnlyBody() throws Exception {
    EditLock lock = new EditLock(42L, 7L, 12L, "Editor", "owner-token",
        Instant.parse("2026-08-05T08:30:00Z"));
    when(service.acquire(42L)).thenReturn(lock);
    when(service.renew(42L, "owner-token")).thenReturn(lock);

    mvc.perform(post("/api/master-record/42/lock"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.token").value("owner-token"));
    mvc.perform(put("/api/master-record/42/lock").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"owner-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.expiresAt").value("2026-08-05T08:30:00Z"));
    mvc.perform(delete("/api/master-record/42/lock").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"owner-token\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test void aLockConflictUsesTheStandardEnvelopeAndDoesNotIncludeTheSecretToken() throws Exception {
    when(service.acquire(42L)).thenThrow(new EditLockConflictException(new EditLock(42L, 7L, 12L,
        "Editor", "owner-token", Instant.parse("2026-08-05T08:30:00Z"))));

    mvc.perform(post("/api/master-record/42/lock"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Editor")))
        .andExpect(jsonPath("$.data.holderId").value(12))
        .andExpect(jsonPath("$.data.holderDisplayName").value("Editor"))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-08-05T08:30:00Z"))
        .andExpect(jsonPath("$.data.token").doesNotExist());
  }

  @Test void tokenIsRequiredForRenewalAndRelease() throws Exception {
    mvc.perform(put("/api/master-record/42/lock").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/master-record/42/lock").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
