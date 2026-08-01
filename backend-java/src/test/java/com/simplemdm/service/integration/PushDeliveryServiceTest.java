package com.simplemdm.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.url=jdbc:h2:mem:push-delivery;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "simple-mdm.push.max-attempts=3",
    "simple-mdm.push.response-snapshot-limit=256",
    "simple-mdm.push.connect-timeout-ms=1000",
    "simple-mdm.push.request-timeout-ms=1000",
    "simple-mdm.push.claim-timeout-seconds=30",
    "simple-mdm.integration.key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PushDeliveryService.class, PinnedHttpTransport.class, CredentialEncryptionService.class,
    PushDeliveryServiceTest.JsonConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PushDeliveryServiceTest {
    @Autowired private PushDeliveryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BlockingResolverControl resolver;

    private HttpServer server;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("accepted");
    private final AtomicInteger requests = new AtomicInteger();
    private final List<CapturedRequest> captured = new ArrayList<>();
    private final AtomicReference<CountDownLatch> deliveryRelease = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> deliveryArrived = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> stalledRelease = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> stalledArrived = new AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean stallNextResponse =
        new java.util.concurrent.atomic.AtomicBoolean();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/downstream/records", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbc.update("delete from sys_push_log");
        jdbc.update("delete from sys_push_subscription");
        jdbc.update("delete from sys_push_endpoint");
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsActualJsonPostAndPersistsSuccessfulAttempt() {
        String approvedSnapshot = """
            {"record_id":41,"system_id":10,"object_type_id":20,"department_id":30,"record_code":"EMP-41","status":"active","version":4,"data":{"name":"Alice","private_pay_grade":"P9"},"children":[{"id":301,"child_type_id":201,"child_type":"part_time","status":"active","version":2,"data":{"company":"Acme","monthly_income":"9000"}}]}
            """.trim();
        insertDelivery("PENDING", 0, approvedSnapshot);

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(requests).hasValue(1);
        assertThat(captured).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.contentType()).startsWith("application/json");
            assertThat(request.eventId()).isEqualTo("record:41:version:4");
            assertThat(request.body()).isEqualTo(approvedSnapshot);
            assertThat(request.host()).isEqualTo("delivery.test:" + server.getAddress().getPort());
        });
        assertThat(status()).isEqualTo("SUCCESS");
        assertThat(retryCount()).isEqualTo(1);
        assertThat(lastAttemptAt()).isNotNull();
        assertThat(responseSnapshot()).contains("\"http_status\":200", "accepted");
    }

    @Test
    void sendsNoAuthenticationHeaderForNoneEndpoint() {
        insertDelivery("PENDING", 0, "{\"record_id\":41}");
        responseBody.set("non-sensitive downstream body");

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
        assertThat(captured).singleElement().satisfies(request ->
            assertThat(request.authorization()).isNull());
        assertThat(responseSnapshot()).contains("non-sensitive downstream body");
    }

    @Test
    void sendsBasicAuthorizationWithoutPersistingCredentialsInSnapshots() {
        String username = "test-basic-user";
        String password = "test-basic-password";
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        insertDelivery("PENDING", 0, "{\"record_id\":41}", "BASIC",
            encryptedCredentials("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
        responseBody.set("Authorization: " + authorization);

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
        assertThat(captured).singleElement().satisfies(request ->
            assertThat(request.authorization()).isEqualTo(authorization));
        assertThat(responseSnapshot()).doesNotContain(username, password, authorization, "Authorization");
    }

    @Test
    void sendsBearerAuthorizationWithoutPersistingTokenInSnapshots() {
        String token = "test-bearer-token";
        insertDelivery("PENDING", 0, "{\"record_id\":41}", "BEARER",
            encryptedCredentials("{\"token\":\"" + token + "\"}"));
        responseBody.set("Authorization: Bearer " + token);

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
        assertThat(captured).singleElement().satisfies(request ->
            assertThat(request.authorization()).isEqualTo("Bearer " + token));
        assertThat(responseSnapshot()).doesNotContain(token, "Bearer", "Authorization");
    }

    @Test
    void sendsConfiguredApiKeyHeaderWithoutPersistingCredentialInSnapshots() {
        String headerName = "X-Test-Api-Key";
        String value = "test-api-key";
        insertDelivery("PENDING", 0, "{\"record_id\":41}", "API_KEY",
            encryptedCredentials("{\"header_name\":\"" + headerName + "\",\"value\":\"" + value + "\"}"));
        responseBody.set(headerName + ": " + value);

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
        assertThat(captured).singleElement().satisfies(request ->
            assertThat(request.apiKey()).isEqualTo(value));
        assertThat(responseSnapshot()).doesNotContain(headerName, value);
    }

    @Test
    void truncatesFailureResponseWithoutPersistingEndpointOrCredentials() {
        insertDelivery("PENDING", 0, "{\"record_id\":41}");
        responseStatus.set(503);
        responseBody.set("downstream failure ".repeat(200));

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.FAILED);
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(retryCount()).isEqualTo(1);
        assertThat(responseSnapshot()).hasSizeLessThanOrEqualTo(256)
            .contains("\"http_status\":503")
            .doesNotContain(endpointUrl(), "encrypted-fixture-value", "Authorization");
    }

    @Test
    void thirdFailedAttemptExhaustsMaximumAndFourthCallDoesNotSend() {
        insertDelivery("FAILED", 2, "{\"record_id\":41}");
        responseStatus.set(503);

        PushDeliveryService.DeliveryResult third = service.deliver(1L);
        PushDeliveryService.DeliveryResult fourth = service.deliver(1L);

        assertThat(third.outcome()).isEqualTo(PushDeliveryService.Outcome.FAILED);
        assertThat(third.attempt()).isEqualTo(3);
        assertThat(fourth.outcome()).isEqualTo(PushDeliveryService.Outcome.SKIPPED);
        assertThat(requests).hasValue(1);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(retryCount()).isEqualTo(3);
    }

    @Test
    void invalidStoredRequestIsRecordedAsSanitizedFailureInsteadOfLeavingClaimProcessing() {
        insertDelivery("PENDING", 0, null);

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.FAILED);
        assertThat(requests).hasValue(0);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(retryCount()).isEqualTo(1);
        assertThat(responseSnapshot()).isEqualTo("{\"error\":\"REQUEST_CONFIGURATION_ERROR\"}");
    }

    @Test
    void refusesPersistedAuthenticationModeThatHasNoCredentialProvider() {
        insertDelivery("PENDING", 0, "{\"record_id\":41}");
        jdbc.update("update sys_push_endpoint set authentication_type='BEARER', "
            + "encrypted_credentials='encrypted-fixture-value' where id=81");

        PushDeliveryService.DeliveryResult result = service.deliver(1L);

        assertThat(result.outcome()).isEqualTo(PushDeliveryService.Outcome.FAILED);
        assertThat(requests).hasValue(0);
        assertThat(responseSnapshot()).isEqualTo("{\"error\":\"AUTHENTICATION_UNAVAILABLE\"}")
            .doesNotContain("encrypted-fixture-value", "Authorization");
    }

    @Test
    void stalledResponseBodyHitsAttemptDeadlineAndDoesNotBlockNextLog() throws Exception {
        insertDelivery("PENDING", 0, "{\"record_id\":41}");
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        stalledArrived.set(arrived);
        stalledRelease.set(release);
        stallNextResponse.set(true);
        try {
            long startedAt = System.nanoTime();
            PushDeliveryService.DeliveryResult stalled = service.deliver(1L);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(arrived.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stalled.outcome()).isEqualTo(PushDeliveryService.Outcome.FAILED);
            assertThat(responseSnapshot()).isEqualTo("{\"error\":\"ATTEMPT_TIMEOUT\"}");
            assertThat(elapsedMillis).isLessThan(1500L);

            insertLog(2L, "record:42:version:1", "{\"record_id\":42}");
            PushDeliveryService.DeliveryResult next = service.deliver(2L);

            assertThat(next.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
            assertThat(requests).hasValue(2);
        } finally {
            release.countDown();
        }
    }

    @Test
    void blockingDnsSharesAttemptDeadlineAndDoesNotBlockNextLog() throws Exception {
        insertDelivery("PENDING", 0, "{\"record_id\":41}");
        jdbc.update("update sys_push_endpoint set endpoint_url=? where id=81",
            endpointUrl().replace("delivery.test", "blocked.test"));
        resolver.block("blocked.test");
        try {
            long startedAt = System.nanoTime();
            PushDeliveryService.DeliveryResult blocked = service.deliver(1L);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(resolver.awaitBlocked(1, TimeUnit.SECONDS)).isTrue();
            assertThat(blocked.outcome()).isEqualTo(PushDeliveryService.Outcome.FAILED);
            assertThat(responseSnapshot()).isEqualTo("{\"error\":\"ATTEMPT_TIMEOUT\"}");
            assertThat(elapsedMillis).isLessThan(1500L);
            assertThat(requests).hasValue(0);

            jdbc.update("update sys_push_endpoint set endpoint_url=? where id=81", endpointUrl());
            insertLog(2L, "record:42:version:1", "{\"record_id\":42}");
            PushDeliveryService.DeliveryResult next = service.deliver(2L);

            assertThat(next.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
            assertThat(requests).hasValue(1);
        } finally {
            resolver.release();
        }
    }

    @Test
    void concurrentDeliveryClaimsOnlyOnceWhileNetworkCallIsOutsideClaimTransaction() throws Exception {
        insertDelivery("PENDING", 0, "{\"record_id\":41}");
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        deliveryArrived.set(arrived);
        deliveryRelease.set(release);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PushDeliveryService.DeliveryResult> first = executor.submit(() -> service.deliver(1L));
            assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();

            PushDeliveryService.DeliveryResult duplicate = service.deliver(1L);
            release.countDown();
            PushDeliveryService.DeliveryResult original = first.get(5, TimeUnit.SECONDS);

            assertThat(duplicate.outcome()).isEqualTo(PushDeliveryService.Outcome.SKIPPED);
            assertThat(original.outcome()).isEqualTo(PushDeliveryService.Outcome.SUCCEEDED);
            assertThat(requests).hasValue(1);
            assertThat(retryCount()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private void insertDelivery(String status, int retryCount, String snapshot) {
        insertDelivery(status, retryCount, snapshot, "NONE", null);
    }

    private void insertDelivery(String status, int retryCount, String snapshot,
                                String authenticationType, String encryptedCredentials) {
        jdbc.update("""
            insert into sys_push_endpoint
                (id, system_id, code, name, endpoint_url, authentication_type,
                 encrypted_credentials, status, created_at, updated_at, version)
            values (81, 10, 'ERP', 'ERP', ?, ?, ?,
                    'active', current_timestamp, current_timestamp, 0)
            """, endpointUrl(), authenticationType, encryptedCredentials);
        jdbc.update("""
            insert into sys_push_subscription
                (id, system_id, endpoint_id, object_type_id, event_type, status, created_at, updated_at)
            values (91, 10, 81, 20, 'RECORD_CHANGED', 'active', current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into sys_push_log
                (id, system_id, subscription_id, record_id, event_id, status, retry_count,
                 request_snapshot, trigger_type, idempotency_key, active_dedup_key, created_at)
            values (1, 10, 91, 41, 'record:41:version:4', ?, ?, ?, 'AUTOMATIC',
                    'delivery:1', '10:81:20:41:4', current_timestamp)
            """, status, retryCount, snapshot);
    }

    private void insertLog(Long id, String eventId, String snapshot) {
        jdbc.update("""
            insert into sys_push_log
                (id, system_id, subscription_id, record_id, event_id, status, retry_count,
                 request_snapshot, trigger_type, idempotency_key, active_dedup_key, created_at)
            values (?, 10, 91, 41, ?, 'PENDING', 0, ?, 'AUTOMATIC', ?, ?, current_timestamp)
            """, id, eventId, snapshot, "delivery:" + id, "dedup:" + id);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        synchronized (captured) {
            captured.add(new CapturedRequest(exchange.getRequestMethod(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                exchange.getRequestHeaders().getFirst("X-SimpleMDM-Event-Id"),
                new String(requestBody, StandardCharsets.UTF_8),
                exchange.getRequestHeaders().getFirst("Host"),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Test-Api-Key")));
        }
        if (stallNextResponse.compareAndSet(true, false)) {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            CountDownLatch arrived = stalledArrived.get();
            if (arrived != null) arrived.countDown();
            CountDownLatch release = stalledRelease.get();
            try {
                if (release != null) release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
        }
        CountDownLatch arrived = deliveryArrived.get();
        if (arrived != null) arrived.countDown();
        CountDownLatch release = deliveryRelease.get();
        if (release != null) {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String endpointUrl() {
        return "http://delivery.test:" + server.getAddress().getPort() + "/downstream/records";
    }

    private String status() {
        return jdbc.queryForObject("select status from sys_push_log where id=1", String.class);
    }

    private Integer retryCount() {
        return jdbc.queryForObject("select retry_count from sys_push_log where id=1", Integer.class);
    }

    private java.sql.Timestamp lastAttemptAt() {
        return jdbc.queryForObject("select last_attempt_at from sys_push_log where id=1", java.sql.Timestamp.class);
    }

    private String responseSnapshot() {
        return jdbc.queryForObject("select response_snapshot from sys_push_log where id=1", String.class);
    }

    private String encryptedCredentials(String plaintext) {
        try {
            byte[] initializationVector = new byte[12];
            for (int index = 0; index < initializationVector.length; index++) initializationVector[index] = (byte) index;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(
                Base64.getDecoder().decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="), "AES"),
                new GCMParameterSpec(128, initializationVector));
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(initializationVector)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record CapturedRequest(String method, String contentType, String eventId, String body, String host,
                                   String authorization, String apiKey) {
    }

    @TestConfiguration
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        BlockingResolverControl blockingResolverControl() {
            return new BlockingResolverControl();
        }

        @Bean
        EndpointUrlPolicy endpointUrlPolicy(BlockingResolverControl resolver) {
            return new PublicEndpointUrlPolicy(resolver::resolve, boundedResolverExecutor(),
                Duration.ofSeconds(2), ignored -> true);
        }

        private ThreadPoolExecutor boundedResolverExecutor() {
            return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, "test-delivery-resolver");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        }
    }

    static final class BlockingResolverControl {
        private final AtomicReference<String> blockedHost = new AtomicReference<>();
        private final AtomicReference<CountDownLatch> entered = new AtomicReference<>(new CountDownLatch(0));
        private final AtomicReference<CountDownLatch> release = new AtomicReference<>(new CountDownLatch(0));

        void block(String host) {
            blockedHost.set(host);
            entered.set(new CountDownLatch(1));
            release.set(new CountDownLatch(1));
        }

        InetAddress[] resolve(String host) {
            if (host.equals(blockedHost.get())) {
                entered.get().countDown();
                while (release.get().getCount() > 0) {
                    try {
                        release.get().await();
                    } catch (InterruptedException ignored) {
                        // Model a native resolver call that ignores interruption.
                    }
                }
            }
            return new InetAddress[]{InetAddress.getLoopbackAddress()};
        }

        boolean awaitBlocked(long timeout, TimeUnit unit) throws InterruptedException {
            return entered.get().await(timeout, unit);
        }

        void release() {
            release.get().countDown();
            blockedHost.set(null);
        }
    }
}
