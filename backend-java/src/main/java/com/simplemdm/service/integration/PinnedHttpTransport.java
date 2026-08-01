package com.simplemdm.service.integration;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PinnedHttpTransport {
    public record Response(int statusCode, String body, boolean truncated) {
    }

    public static class AttemptTimeoutException extends IOException {
        AttemptTimeoutException() {
            super("HTTP attempt deadline exceeded");
        }
    }

    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
        daemonThreads());

    public Response postJson(EndpointUrlPolicy.ValidatedEndpoint endpoint, String eventId,
                             String snapshot, int bodyLimit, Duration connectTimeout,
                             AttemptDeadline attemptDeadline, Map<String, String> authenticationHeaders) throws IOException {
        String pinnedHost = normalizeHost(endpoint.uri().getHost());
        InetAddress[] pinnedAddresses = endpoint.addresses().toArray(InetAddress[]::new);
        DnsResolver pinnedDns = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                requirePinnedHost(host, pinnedHost);
                return Arrays.copyOf(pinnedAddresses, pinnedAddresses.length);
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                requirePinnedHost(host, pinnedHost);
                return host;
            }
        };
        long remainingNanos = attemptDeadline.remainingNanos();
        if (remainingNanos == 0) throw new AttemptTimeoutException();
        long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        long connectMillis = Math.max(1, Math.min(connectTimeout.toMillis(), remainingMillis));
        Timeout connect = Timeout.ofMilliseconds(connectMillis);
        Timeout deadline = Timeout.ofMilliseconds(remainingMillis);
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(connect)
            .setConnectionRequestTimeout(connect)
            .setResponseTimeout(deadline)
            .build();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(pinnedDns)
            .build();
        AtomicBoolean deadlineReached = new AtomicBoolean();
        try (CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .disableAutomaticRetries()
            .disableRedirectHandling()
            .disableCookieManagement()
            .disableContentCompression()
            .build()) {
            ScheduledFuture<?> cutoff = deadlines.schedule(() -> {
                deadlineReached.set(true);
                client.close(CloseMode.IMMEDIATE);
            }, attemptDeadline.remainingNanos(), TimeUnit.NANOSECONDS);
            try {
                HttpPost request = new HttpPost(endpoint.uri());
                request.setHeader("Content-Type", "application/json; charset=UTF-8");
                request.setHeader("Accept", "application/json");
                request.setHeader("X-SimpleMDM-Event-Id", eventId);
                authenticationHeaders.forEach(request::setHeader);
                request.setEntity(new StringEntity(snapshot,
                    ContentType.create("application/json", StandardCharsets.UTF_8)));
                Response response = client.execute(request,
                    value -> read(value.getCode(), value.getEntity(), bodyLimit));
                if (deadlineReached.get() || attemptDeadline.isExpired()) throw new AttemptTimeoutException();
                return response;
            } catch (IOException | RuntimeException exception) {
                if (deadlineReached.get() || isTimeout(exception)) throw new AttemptTimeoutException();
                throw exception;
            } finally {
                cutoff.cancel(false);
            }
        }
    }

    private Response read(int status, HttpEntity entity, int bodyLimit) throws IOException {
        if (entity == null) return new Response(status, "", false);
        try (InputStream input = entity.getContent()) {
            byte[] bytes = input.readNBytes(bodyLimit + 1);
            boolean truncated = bytes.length > bodyLimit;
            int kept = Math.min(bytes.length, bodyLimit);
            return new Response(status, new String(bytes, 0, kept, StandardCharsets.UTF_8), truncated);
        }
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("[") && normalized.endsWith("]")
            ? normalized.substring(1, normalized.length() - 1) : normalized;
    }

    private void requirePinnedHost(String host, String pinnedHost) throws UnknownHostException {
        if (!pinnedHost.equals(normalizeHost(host))) throw new UnknownHostException(host);
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                || current instanceof ConnectionRequestTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ThreadFactory daemonThreads() {
        return task -> {
            Thread thread = new Thread(task, "push-http-deadline");
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void stop() {
        deadlines.shutdownNow();
    }
}
