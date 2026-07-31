package com.simplemdm.service.mdm;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovedRecordWriterApiTest {
    @Test
    void injectableCapabilityAcceptsOnlyPersistedRequestId() {
        assertThat(Arrays.stream(ApprovedRecordWriter.class.getDeclaredMethods())
            .map(Method::getParameterTypes).toList())
            .containsExactly(new Class<?>[]{Long.class});
    }
}
