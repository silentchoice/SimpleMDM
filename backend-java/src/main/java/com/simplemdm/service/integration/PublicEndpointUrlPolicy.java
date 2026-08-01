package com.simplemdm.service.integration;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

@Component
public class PublicEndpointUrlPolicy implements EndpointUrlPolicy {
    private static final Duration DEFAULT_RESOLUTION_TIMEOUT = Duration.ofSeconds(3);

    /*
     * Static registry snapshots; endpoint validation never downloads registry data at runtime.
     * IANA IPv6 Global Unicast Address Space, last updated 2025-10-10:
     * https://www.iana.org/assignments/ipv6-unicast-address-assignments/ipv6-unicast-address-assignments.xhtml
     * IANA IPv6 Special-Purpose Address Space, last updated 2025-10-09:
     * https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry.xhtml
     */
    private static final List<Ipv6Prefix> GLOBALLY_REACHABLE_SPECIAL_PREFIXES = List.of(
        prefix(96, 0x00, 0x64, 0xff, 0x9b, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        prefix(128, 0x20, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01),
        prefix(128, 0x20, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02),
        prefix(128, 0x20, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03),
        prefix(32, 0x20, 0x01, 0x00, 0x03),
        prefix(48, 0x20, 0x01, 0x00, 0x04, 0x01, 0x12),
        prefix(28, 0x20, 0x01, 0x00, 0x20),
        prefix(28, 0x20, 0x01, 0x00, 0x30),
        prefix(48, 0x26, 0x20, 0x00, 0x4f, 0x80, 0x00)
    );

    /* Every current special-purpose entry not marked Globally Reachable=True fails closed.
       Java's local-address predicates and the mapped-literal guard reject the omitted ::/128,
       ::1/128, and ::ffff:0:0/96 entries before this table is consulted. */
    private static final List<Ipv6Prefix> NON_GLOBALLY_REACHABLE_SPECIAL_PREFIXES = List.of(
        prefix(48, 0x00, 0x64, 0xff, 0x9b, 0x00, 0x01),
        prefix(64, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        prefix(64, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01),
        prefix(23, 0x20, 0x01, 0x00),
        prefix(32, 0x20, 0x01, 0x00, 0x00),
        prefix(48, 0x20, 0x01, 0x00, 0x02, 0x00, 0x00),
        prefix(28, 0x20, 0x01, 0x00, 0x10),
        prefix(32, 0x20, 0x01, 0x0d, 0xb8),
        prefix(16, 0x20, 0x02),
        prefix(20, 0x3f, 0xff, 0x00),
        prefix(16, 0x5f, 0x00),
        prefix(7, 0xfc),
        prefix(10, 0xfe, 0x80)
    );

    /* Status=ALLOCATED rows, excluding 2001::/23 and 2002::/16 because the
       Special-Purpose registry defines their usable semantics. */
    private static final List<Ipv6Prefix> ALLOCATED_GLOBAL_UNICAST_PREFIXES = List.of(
        prefix(23, 0x20, 0x01, 0x02),
        prefix(23, 0x20, 0x01, 0x04),
        prefix(23, 0x20, 0x01, 0x06),
        prefix(22, 0x20, 0x01, 0x08),
        prefix(23, 0x20, 0x01, 0x0c),
        prefix(23, 0x20, 0x01, 0x0e),
        prefix(23, 0x20, 0x01, 0x12),
        prefix(22, 0x20, 0x01, 0x14),
        prefix(23, 0x20, 0x01, 0x18),
        prefix(23, 0x20, 0x01, 0x1a),
        prefix(22, 0x20, 0x01, 0x1c),
        prefix(19, 0x20, 0x01, 0x20),
        prefix(23, 0x20, 0x01, 0x40),
        prefix(23, 0x20, 0x01, 0x42),
        prefix(23, 0x20, 0x01, 0x44),
        prefix(23, 0x20, 0x01, 0x46),
        prefix(23, 0x20, 0x01, 0x48),
        prefix(23, 0x20, 0x01, 0x4a),
        prefix(23, 0x20, 0x01, 0x4c),
        prefix(20, 0x20, 0x01, 0x50),
        prefix(19, 0x20, 0x01, 0x80),
        prefix(20, 0x20, 0x01, 0xa0),
        prefix(20, 0x20, 0x01, 0xb0),
        prefix(18, 0x20, 0x03, 0x00),
        prefix(12, 0x24, 0x00),
        prefix(12, 0x24, 0x10),
        prefix(12, 0x26, 0x00),
        prefix(23, 0x26, 0x10, 0x00),
        prefix(23, 0x26, 0x20, 0x00),
        prefix(12, 0x26, 0x30),
        prefix(12, 0x28, 0x00),
        prefix(12, 0x2a, 0x00),
        prefix(12, 0x2a, 0x10),
        prefix(12, 0x2c, 0x00)
    );

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver resolver;
    private final ThreadPoolExecutor resolverExecutor;
    private final Duration defaultResolutionTimeout;
    private final Predicate<InetAddress> addressAllowed;

    public PublicEndpointUrlPolicy() {
        this(InetAddress::getAllByName, boundedExecutor(), DEFAULT_RESOLUTION_TIMEOUT,
            PublicEndpointUrlPolicy::isPublic);
    }

    PublicEndpointUrlPolicy(HostResolver resolver) {
        this(resolver, boundedExecutor(), DEFAULT_RESOLUTION_TIMEOUT, PublicEndpointUrlPolicy::isPublic);
    }

    PublicEndpointUrlPolicy(HostResolver resolver, ThreadPoolExecutor resolverExecutor,
                            Duration defaultResolutionTimeout, Predicate<InetAddress> addressAllowed) {
        this.resolver = resolver;
        this.resolverExecutor = resolverExecutor;
        this.defaultResolutionTimeout = defaultResolutionTimeout;
        this.addressAllowed = addressAllowed;
    }

    @Override
    public ValidatedEndpoint validate(String value) {
        return validate(value, AttemptDeadline.after(defaultResolutionTimeout));
    }

    @Override
    public ValidatedEndpoint validate(String value, AttemptDeadline deadline) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            throw rejected();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
            || host == null || host.isBlank() || uri.getUserInfo() != null || uri.getFragment() != null
            || uri.getPort() == 0 || uri.getPort() < -1 || uri.getPort() > 65_535) {
            throw rejected();
        }
        String resolverHost = stripIpv6Brackets(host);
        String lowerHost = resolverHost.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".localhost")) throw rejected();
        List<InetAddress> addresses = resolve(resolverHost, deadline);
        boolean mappedIpv4Literal = resolverHost.indexOf(':') >= 0
            && addresses.stream().anyMatch(address -> address != null && address.getAddress().length == 4);
        if (addresses.isEmpty() || mappedIpv4Literal
            || addresses.stream().anyMatch(address -> !addressAllowed.test(address))) {
            throw rejected();
        }
        return new ValidatedEndpoint(uri, addresses);
    }

    private List<InetAddress> resolve(String host, AttemptDeadline deadline) {
        if (deadline.isExpired()) throw timedOut();
        Future<InetAddress[]> resolution;
        try {
            resolution = resolverExecutor.submit(() -> resolver.resolve(host));
        } catch (RejectedExecutionException exception) {
            throw timedOut();
        }
        try {
            long remaining = deadline.remainingNanos();
            if (remaining == 0) throw new TimeoutException();
            return Arrays.asList(resolution.get(remaining, TimeUnit.NANOSECONDS));
        } catch (TimeoutException exception) {
            resolution.cancel(true);
            resolverExecutor.purge();
            throw timedOut();
        } catch (InterruptedException exception) {
            resolution.cancel(true);
            resolverExecutor.purge();
            Thread.currentThread().interrupt();
            throw timedOut();
        } catch (ExecutionException exception) {
            throw rejected();
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                && first != 10
                && first != 127
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 0 && (third == 0 || third == 2))
                && !(first == 192 && second == 88 && third == 99)
                && !(first == 192 && second == 168)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113)
                && first < 224;
        }
        return bytes.length == 16 && isGloballyReachableIpv6(bytes);
    }

    private static boolean isGloballyReachableIpv6(byte[] address) {
        if (matchesAny(address, GLOBALLY_REACHABLE_SPECIAL_PREFIXES)) return true;
        if (matchesAny(address, NON_GLOBALLY_REACHABLE_SPECIAL_PREFIXES)) return false;
        return matchesAny(address, ALLOCATED_GLOBAL_UNICAST_PREFIXES);
    }

    private static boolean matchesAny(byte[] address, List<Ipv6Prefix> prefixes) {
        return prefixes.stream().anyMatch(prefix -> prefix.matches(address));
    }

    private static Ipv6Prefix prefix(int bits, int... bytes) {
        return new Ipv6Prefix(bytes, bits);
    }

    private static boolean hasPrefix(byte[] address, int[] prefix, int bits) {
        int completeBytes = bits / 8;
        for (int index = 0; index < completeBytes; index++) {
            if (Byte.toUnsignedInt(address[index]) != prefix[index]) return false;
        }
        int remaining = bits % 8;
        if (remaining == 0) return true;
        int mask = 0xff << (8 - remaining);
        return (Byte.toUnsignedInt(address[completeBytes]) & mask) == (prefix[completeBytes] & mask);
    }

    private record Ipv6Prefix(int[] bytes, int bits) {
        private Ipv6Prefix {
            bytes = bytes.clone();
        }

        private boolean matches(byte[] address) {
            return hasPrefix(address, bytes, bits);
        }
    }

    private static String stripIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static RejectedEndpointException rejected() {
        return new RejectedEndpointException();
    }

    private static ResolutionTimeoutException timedOut() {
        return new ResolutionTimeoutException();
    }

    private static ThreadPoolExecutor boundedExecutor() {
        return new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), daemonThreads(), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory daemonThreads() {
        return task -> {
            Thread thread = new Thread(task, "endpoint-dns-resolution");
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void stop() {
        resolverExecutor.shutdownNow();
    }
}
