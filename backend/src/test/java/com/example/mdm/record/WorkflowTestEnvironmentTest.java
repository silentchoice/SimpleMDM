package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowTestEnvironmentTest {
  @TempDir Path temporaryDirectory;

  @Test void localMySqlCredentialsComeOnlyFromTheProcessEnvironment() {
    String propertyName = "record.repository.mysql.password";
    String previous = System.getProperty(propertyName);
    System.setProperty(propertyName, "system-property-sentinel");
    try {
      Map<String, String> environment = Map.of(
          "MDM_TEST_MYSQL_SERVER_URL", "jdbc:mysql://127.0.0.1:3306/",
          "MDM_TEST_MYSQL_USERNAME", "environment-user",
          "MDM_TEST_MYSQL_PASSWORD", "environment-password");

      WorkflowTestEnvironment.MySqlSettings settings =
          WorkflowTestEnvironment.mysql(environment::get);

      assertThat(settings.serverUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/");
      assertThat(settings.username()).isEqualTo("environment-user");
      assertThat(settings.password()).isEqualTo("environment-password");
      assertThat(settings.password()).isNotEqualTo(System.getProperty(propertyName));
    } finally {
      if (previous == null) System.clearProperty(propertyName);
      else System.setProperty(propertyName, previous);
    }
  }

  @Test void localMySqlPasswordPreservesSignificantWhitespace() {
    Map<String, String> environment = Map.of(
        "MDM_TEST_MYSQL_SERVER_URL", "jdbc:mysql://127.0.0.1:3306/",
        "MDM_TEST_MYSQL_USERNAME", "environment-user",
        "MDM_TEST_MYSQL_PASSWORD", " password with edges ");

    WorkflowTestEnvironment.MySqlSettings settings =
        WorkflowTestEnvironment.mysql(environment::get);

    assertThat(settings.password()).isEqualTo(" password with edges ");
  }

  @Test void explicitRedisServerPathTakesPriorityOverPathDiscovery() throws Exception {
    Path explicit = Files.createFile(temporaryDirectory.resolve("approved-redis-server"));
    Path pathDirectory = Files.createDirectory(temporaryDirectory.resolve("path"));
    Files.createFile(pathDirectory.resolve("redis-server"));

    WorkflowTestEnvironment.RedisExecutable resolved = WorkflowTestEnvironment.redisExecutable(
        "Linux", explicit.toString(), pathDirectory.toString(),
        () -> temporaryDirectory.resolve("redis-server-2.8.19.exe"),
        candidate -> candidate.equals(explicit.toAbsolutePath()));

    assertThat(resolved.path()).isEqualTo(explicit.toAbsolutePath());
    assertThat(resolved.temporary()).isFalse();
  }

  @Test void linuxRedisResolutionUsesTheNativePathBinaryWithoutWindowsFallback() throws Exception {
    Path pathDirectory = Files.createDirectory(temporaryDirectory.resolve("linux-path"));
    Path nativeBinary = Files.createFile(pathDirectory.resolve("redis-server"));
    AtomicBoolean windowsFallbackCalled = new AtomicBoolean();

    WorkflowTestEnvironment.RedisExecutable resolved = WorkflowTestEnvironment.redisExecutable(
        "Linux", null, pathDirectory.toString(), () -> {
          windowsFallbackCalled.set(true);
          return temporaryDirectory.resolve("redis-server-2.8.19.exe");
        }, candidate -> candidate.equals(nativeBinary.toAbsolutePath()));

    assertThat(resolved.path()).isEqualTo(nativeBinary.toAbsolutePath());
    assertThat(resolved.temporary()).isFalse();
    assertThat(windowsFallbackCalled).isFalse();
  }

  @Test void linuxRedisResolutionSkipsNonExecutablePathEntries() throws Exception {
    Path firstDirectory = Files.createDirectory(temporaryDirectory.resolve("not-runnable"));
    Path nonExecutable = Files.createFile(firstDirectory.resolve("redis-server"));
    Path secondDirectory = Files.createDirectory(temporaryDirectory.resolve("runnable"));
    Path executable = Files.createFile(secondDirectory.resolve("redis-server"));

    WorkflowTestEnvironment.RedisExecutable resolved = WorkflowTestEnvironment.redisExecutable(
        "Linux", null, firstDirectory + java.io.File.pathSeparator + secondDirectory,
        () -> temporaryDirectory.resolve("redis-server-2.8.19.exe"),
        candidate -> candidate.equals(executable.toAbsolutePath()));

    assertThat(resolved.path()).isEqualTo(executable.toAbsolutePath());
    assertThat(resolved.path()).isNotEqualTo(nonExecutable.toAbsolutePath());
  }

  @Test void explicitNonExecutableRedisServerIsRejectedOnUnix() throws Exception {
    Path nonExecutable = Files.createFile(temporaryDirectory.resolve("explicit-not-runnable"));

    Throwable failure = catchThrowable(() -> WorkflowTestEnvironment.redisExecutable(
        "Linux", nonExecutable.toString(), "", () -> null, candidate -> false));

    assertThat(failure).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not identify an executable file");
  }

  @Test void darwinRedisResolutionNeverUsesTheWindowsBundledBinary() {
    AtomicBoolean windowsFallbackCalled = new AtomicBoolean();

    Throwable failure = catchThrowable(() -> WorkflowTestEnvironment.redisExecutable(
        "Darwin", null, "", () -> {
          windowsFallbackCalled.set(true);
          return temporaryDirectory.resolve("redis-server-2.8.19.exe");
        }));

    assertThat(failure).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("redis-server is required");
    assertThat(windowsFallbackCalled).isFalse();
  }

  @Test void cleanupAttemptsEveryStageAndSuppressesFailuresAfterTheFirst() {
    List<String> attempted = new ArrayList<>();

    Throwable failure = catchThrowable(() -> WorkflowTestEnvironment.cleanup(
        () -> { attempted.add("redis-connection"); throw new IOException("first cleanup"); },
        () -> attempted.add("redis-server"),
        () -> { attempted.add("schema"); throw new IllegalStateException("later cleanup"); },
        () -> attempted.add("container")));

    assertThat(attempted).containsExactly("redis-connection", "redis-server", "schema",
        "container");
    assertThat(failure).isInstanceOf(IOException.class).hasMessage("first cleanup");
    assertThat(failure.getSuppressed()).singleElement().satisfies(suppressed ->
        assertThat(suppressed).isInstanceOf(IllegalStateException.class)
            .hasMessage("later cleanup"));
  }

  @Test void initializationFailureRemainsPrimaryWhenCleanupAlsoFails() {
    IOException initialization = new IOException("initialization");

    Exception retained = WorkflowTestEnvironment.withCleanupFailure(initialization,
        () -> { throw new IllegalStateException("cleanup"); });

    assertThat(retained).isSameAs(initialization);
    assertThat(retained.getSuppressed()).singleElement().satisfies(suppressed ->
        assertThat(suppressed).isInstanceOf(IllegalStateException.class).hasMessage("cleanup"));
  }
}
