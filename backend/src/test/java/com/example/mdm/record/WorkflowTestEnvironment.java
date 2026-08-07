package com.example.mdm.record;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import redis.embedded.RedisServer;

final class WorkflowTestEnvironment {
  static final String MYSQL_SERVER_URL = "MDM_TEST_MYSQL_SERVER_URL";
  static final String MYSQL_USERNAME = "MDM_TEST_MYSQL_USERNAME";
  static final String MYSQL_PASSWORD = "MDM_TEST_MYSQL_PASSWORD";
  static final String REDIS_SERVER = "MDM_TEST_REDIS_SERVER";

  private WorkflowTestEnvironment() {}

  static MySqlSettings mysql() {
    return mysql(System::getenv);
  }

  static MySqlSettings mysql(Function<String, String> environment) {
    String serverUrl = trim(environment.apply(MYSQL_SERVER_URL));
    if (serverUrl == null) return null;
    return new MySqlSettings(serverUrl, required(environment, MYSQL_USERNAME),
        requiredPassword(environment));
  }

  private static String required(Function<String, String> environment, String name) {
    String value = trim(environment.apply(name));
    if (value == null) throw new IllegalStateException(name + " is required");
    return value;
  }

  private static String requiredPassword(Function<String, String> environment) {
    String value = environment.apply(MYSQL_PASSWORD);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(MYSQL_PASSWORD + " is required");
    }
    return value;
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  record MySqlSettings(String serverUrl, String username, String password) {}

  static RedisExecutable redisExecutable() {
    return redisExecutable(System.getProperty("os.name", ""), System.getenv(REDIS_SERVER),
        System.getenv("PATH"), WorkflowTestEnvironment::extractBundledWindowsRedis);
  }

  static RedisExecutable redisExecutable(String osName, String explicitPath, String pathValue,
      Supplier<Path> windowsBundled) {
    return redisExecutable(osName, explicitPath, pathValue, windowsBundled, Files::isExecutable);
  }

  static RedisExecutable redisExecutable(String osName, String explicitPath, String pathValue,
      Supplier<Path> windowsBundled, Predicate<Path> executable) {
    boolean windows = osName.toLowerCase(Locale.ROOT).startsWith("windows");
    if (explicitPath != null && !explicitPath.isBlank()) {
      Path resolved = requireFile(Path.of(explicitPath), REDIS_SERVER);
      if (!windows && !executable.test(resolved)) {
        throw new IllegalStateException(REDIS_SERVER + " does not identify an executable file");
      }
      return new RedisExecutable(resolved, false);
    }
    List<String> names = windows ? List.of("redis-server.exe", "redis-server")
        : List.of("redis-server");
    if (pathValue != null) {
      for (String directory : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (directory.isBlank()) continue;
        for (String name : names) {
          Path candidate = Path.of(directory, name).toAbsolutePath().normalize();
          if (Files.isRegularFile(candidate) && (windows || executable.test(candidate))) {
            return new RedisExecutable(candidate, false);
          }
        }
      }
    }
    if (windows) {
      return new RedisExecutable(requireFile(windowsBundled.get(), "bundled Redis server"), true);
    }
    throw new IllegalStateException("redis-server is required via " + REDIS_SERVER + " or PATH");
  }

  private static Path extractBundledWindowsRedis() {
    try (InputStream source = RedisServer.class.getResourceAsStream("/redis-server-2.8.19.exe")) {
      if (source == null) throw new IllegalStateException("Bundled Windows Redis is unavailable");
      Path result = Files.createTempFile("mdm-redis-", ".exe");
      Files.copy(source, result, StandardCopyOption.REPLACE_EXISTING);
      return result;
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to extract bundled Windows Redis", exception);
    }
  }

  private static Path requireFile(Path path, String source) {
    Path resolved = path.toAbsolutePath().normalize();
    if (!Files.isRegularFile(resolved)) {
      throw new IllegalStateException(source + " does not identify a regular file");
    }
    return resolved;
  }

  static void cleanup(CleanupStage... stages) throws Exception {
    Exception first = null;
    for (CleanupStage stage : stages) {
      try {
        stage.run();
      } catch (Exception failure) {
        if (first == null) first = failure;
        else first.addSuppressed(failure);
      }
    }
    if (first != null) throw first;
  }

  static <E extends Exception> E withCleanupFailure(E primary, CleanupStage cleanup) {
    try {
      cleanup.run();
    } catch (Exception cleanupFailure) {
      primary.addSuppressed(cleanupFailure);
    }
    return primary;
  }

  @FunctionalInterface
  interface CleanupStage {
    void run() throws Exception;
  }

  record RedisExecutable(Path path, boolean temporary) {}
}
