package com.example.mdm.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.embedded.RedisServer;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisEditLockStoreTest {
  private LettuceConnectionFactory connections;
  private RedisServer server;
  private Path executable;
  private StringRedisTemplate redis;
  private RedisEditLockStore store;
  private long recordId;

  @BeforeEach void setUp() {
    int port = availablePort();
    executable = redisExecutable();
    server = new RedisServer(executable.toFile(), port);
    server.start();
    connections = new LettuceConnectionFactory("127.0.0.1", port);
    connections.afterPropertiesSet();
    redis = new StringRedisTemplate(connections);
    redis.afterPropertiesSet();
    store = new RedisEditLockStore(redis);
    recordId = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, Long.MAX_VALUE);
  }

  @AfterEach void tearDown() throws IOException {
    if (redis != null) redis.delete(key());
    if (connections != null) connections.destroy();
    if (server != null) server.stop();
    if (executable != null) Files.deleteIfExists(executable);
  }

  @Test void redisExecutesCompareScriptsAtomicallyAndExpiresTheActualLockKey() throws Exception {
    EditLock original = lock("first-token", "2026-08-05T08:30:00Z");
    EditLock replacement = lock("second-token", "2026-08-05T08:45:00Z");
    Duration ttl = Duration.ofMillis(150);

    assertThat(store.acquire(original, ttl)).isTrue();
    assertThat(store.renew(recordId, "wrong-token", replacement, ttl)).isFalse();
    assertThat(store.find(recordId)).isEqualTo(original);
    assertThat(store.renew(recordId, "first-token", replacement, ttl)).isTrue();
    assertThat(store.release(recordId, "first-token")).isFalse();
    assertThat(store.find(recordId)).isEqualTo(replacement);

    waitForExpiry();

    assertThat(store.find(recordId)).isNull();
    assertThat(store.release(recordId, "second-token")).isFalse();
  }

  private EditLock lock(String token, String expiry) {
    return new EditLock(recordId, 7L, 12L, "Editor | principal", token, Instant.parse(expiry));
  }

  private void waitForExpiry() throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (store.find(recordId) != null && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertThat(store.find(recordId)).isNull();
  }

  private String key() {
    return "mdm:record:edit-lock:" + recordId;
  }

  private int availablePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Unable to allocate Redis test port", exception);
    }
  }

  private Path redisExecutable() {
    try (InputStream source = RedisServer.class.getResourceAsStream("/redis-server-2.8.19.exe")) {
      if (source == null) throw new IllegalStateException("Embedded Redis executable is unavailable");
      Path result = Files.createTempFile("mdm-redis-", ".exe");
      Files.copy(source, result, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      File file = result.toFile();
      if (!file.setExecutable(true)) throw new IllegalStateException("Embedded Redis executable is not runnable");
      return result;
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to extract embedded Redis executable", exception);
    }
  }
}
