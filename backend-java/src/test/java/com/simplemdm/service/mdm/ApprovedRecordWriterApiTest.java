package com.simplemdm.service.mdm;

import com.simplemdm.service.workflow.ApprovalApplyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovedRecordWriterApiTest {
    @Test
    void injectableCapabilityAcceptsOnlyPersistedRequestId() {
        assertThat(Arrays.stream(ApprovedRecordWriter.class.getDeclaredMethods())
            .map(Method::getParameterTypes).toList())
            .containsExactly(new Class<?>[]{Long.class});
    }

    @Test
    void legacyAdapterDelegatesToAtomicApplyUsingJwtActor() {
        ApprovalApplyService applyService = mock(ApprovalApplyService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        RecordView expected = mock(RecordView.class);
        when(currentUser.currentSystemUserId()).thenReturn(Optional.of(20L));
        when(applyService.approve(100L, 20L)).thenReturn(expected);

        RecordServiceApprovedWriter writer = new RecordServiceApprovedWriter(applyService, currentUser);

        assertThat(writer.apply(100L)).isSameAs(expected);
        verify(applyService).approve(100L, 20L);
    }
}
