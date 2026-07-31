package com.simplemdm.controller;
import static org.assertj.core.api.Assertions.assertThat;import org.junit.jupiter.api.Test;import org.springframework.web.bind.annotation.RequestMapping;
class GenericReachabilityTest {
 @Test void relationalWorkflowAndIntegrationHaveHttpEntrypoints(){assertThat(WorkflowController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/workflow/approvals");assertThat(IntegrationController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/integration");}
}
