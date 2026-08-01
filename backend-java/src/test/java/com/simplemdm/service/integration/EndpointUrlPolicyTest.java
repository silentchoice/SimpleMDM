package com.simplemdm.service.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointUrlPolicyTest {
    private final Map<String, InetAddress[]> resolved = Map.of(
        "public.example", addresses("93.184.216.34"),
        "public-v6.example", addresses("2606:4700:4700::1111"),
        "93.184.216.34", addresses("93.184.216.34"),
        "mixed.example", addresses("93.184.216.34", "10.0.0.7")
    );
    private final PublicEndpointUrlPolicy policy = new PublicEndpointUrlPolicy(host -> {
        InetAddress[] addresses = resolved.get(host);
        return addresses == null ? InetAddress.getAllByName(host) : addresses;
    });

    @AfterEach
    void stopPolicy() {
        policy.stop();
    }

    @Test
    void acceptsPublicHttpEndpointAndReturnsEveryValidatedAddress() {
        EndpointUrlPolicy.ValidatedEndpoint validated = policy.validate(
            "https://public.example:8443/hooks/records?source=mdm");

        assertThat(validated.uri().toString())
            .isEqualTo("https://public.example:8443/hooks/records?source=mdm");
        assertThat(validated.addresses()).extracting(InetAddress::getHostAddress)
            .containsExactly("93.184.216.34");
        assertThat(policy.validate("http://93.184.216.34/hook").addresses())
            .extracting(InetAddress::getHostAddress)
            .containsExactly("93.184.216.34");
        assertThat(policy.validate("https://public-v6.example/hook").addresses())
            .extracting(InetAddress::getHostAddress)
            .containsExactly("2606:4700:4700:0:0:0:0:1111");
    }

    @Test
    void rejectsCredentialsFragmentsLocalNamesAndUnsafeLiteralRanges() {
        assertRejected("https://user:pass@public.example/hook");
        assertRejected("https://public.example/hook#fragment");
        assertRejected("https://public.example:65536/hook");
        assertRejected("http://localhost/hook");
        assertRejected("http://127.0.0.1/hook");
        assertRejected("http://0.0.0.0/hook");
        assertRejected("http://0.1.2.3/hook");
        assertRejected("http://10.1.2.3/hook");
        assertRejected("http://172.16.0.1/hook");
        assertRejected("http://192.168.1.1/hook");
        assertRejected("http://100.64.0.1/hook");
        assertRejected("http://169.254.169.254/latest/meta-data");
        assertRejected("http://192.0.2.1/hook");
        assertRejected("http://198.18.0.1/hook");
        assertRejected("http://198.51.100.1/hook");
        assertRejected("http://203.0.113.1/hook");
        assertRejected("http://240.0.0.1/hook");
        assertRejected("http://255.255.255.255/hook");
        assertRejected("http://[::1]/hook");
        assertRejected("http://[fc00::1]/hook");
        assertRejected("http://[fe80::1]/hook");
        assertRejected("http://[2001:db8::1]/hook");
    }

    @Test
    void rejectsHostnameWhenAnyDnsAnswerIsUnsafe() {
        assertRejected("https://mixed.example/hook");
    }

    @ParameterizedTest(name = "allows IANA prefix {0}; predecessor={1}, successor={2}")
    @MethodSource("allowedIpv6Prefixes")
    void acceptsEveryAllowedIpv6PrefixBoundaryAndClassifiesItsNeighbours(
        String cidr, boolean predecessorAllowed, boolean successorAllowed) {
        Ipv6Range range = ipv6Range(cidr);

        assertAllowed(range.first(), "first address of " + cidr);
        assertAllowed(range.last(), "last address of " + cidr);
        assertAllowed(range.predecessor(), predecessorAllowed, "predecessor of " + cidr);
        assertAllowed(range.successor(), successorAllowed, "successor of " + cidr);
    }

    @ParameterizedTest(name = "rejects non-global IPv6 {0}")
    @MethodSource("nonGloballyReachableIpv6")
    void rejectsRegistryDefinedNonGloballyReachableIpv6(String address) {
        assertRejected("https://[" + address + "]/hook");
    }

    @Test
    void boundedResolverFailsFastWhenItsWorkerAndQueueAreSaturated() throws Exception {
        CountDownLatch resolverEntered = new CountDownLatch(1);
        CountDownLatch resolverRelease = new CountDownLatch(1);
        ThreadPoolExecutor resolverExecutor = boundedExecutor(1, 1);
        PublicEndpointUrlPolicy saturated = new PublicEndpointUrlPolicy(host -> {
            resolverEntered.countDown();
            awaitIgnoringInterrupts(resolverRelease);
            return addresses("93.184.216.34");
        }, resolverExecutor, Duration.ofSeconds(5), ignored -> true);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> running = callers.submit(() -> saturated.validate(
                "https://running.example/hook", AttemptDeadline.after(Duration.ofSeconds(5))));
            assertThat(resolverEntered.await(1, TimeUnit.SECONDS)).isTrue();
            Future<?> queued = callers.submit(() -> saturated.validate(
                "https://queued.example/hook", AttemptDeadline.after(Duration.ofSeconds(5))));
            awaitQueueSize(resolverExecutor, 1);

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> saturated.validate(
                "https://rejected.example/hook", AttemptDeadline.after(Duration.ofSeconds(5))))
                .isInstanceOf(EndpointUrlPolicy.ResolutionTimeoutException.class);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(elapsedMillis).isLessThan(250L);
            assertThat(resolverExecutor.getPoolSize()).isEqualTo(1);
            assertThat(resolverExecutor.getQueue()).hasSize(1);
            assertThat(running).isNotDone();
            assertThat(queued).isNotDone();
        } finally {
            resolverRelease.countDown();
            saturated.stop();
            callers.shutdownNow();
        }
    }

    private static Stream<Arguments> allowedIpv6Prefixes() {
        return Stream.of(
            // IANA IPv6 Special-Purpose Address Space, Globally Reachable=True (2025-10-09).
            Arguments.of("64:ff9b::/96", false, false),
            Arguments.of("2001:1::1/128", false, true),
            Arguments.of("2001:1::2/128", true, true),
            Arguments.of("2001:1::3/128", true, false),
            Arguments.of("2001:3::/32", false, false),
            Arguments.of("2001:4:112::/48", false, false),
            Arguments.of("2001:20::/28", false, true),
            Arguments.of("2001:30::/28", true, false),
            Arguments.of("2620:4f:8000::/48", true, true),

            // IANA IPv6 Global Unicast Address Space, Status=ALLOCATED (2025-10-10).
            Arguments.of("2001:200::/23", false, true),
            Arguments.of("2001:400::/23", true, true),
            Arguments.of("2001:600::/23", true, true),
            Arguments.of("2001:800::/22", true, true),
            Arguments.of("2001:c00::/23", true, true),
            Arguments.of("2001:e00::/23", true, false),
            Arguments.of("2001:1200::/23", false, true),
            Arguments.of("2001:1400::/22", true, true),
            Arguments.of("2001:1800::/23", true, true),
            Arguments.of("2001:1a00::/23", true, true),
            Arguments.of("2001:1c00::/22", true, true),
            Arguments.of("2001:2000::/19", true, true),
            Arguments.of("2001:4000::/23", true, true),
            Arguments.of("2001:4200::/23", true, true),
            Arguments.of("2001:4400::/23", true, true),
            Arguments.of("2001:4600::/23", true, true),
            Arguments.of("2001:4800::/23", true, true),
            Arguments.of("2001:4a00::/23", true, true),
            Arguments.of("2001:4c00::/23", true, false),
            Arguments.of("2001:5000::/20", false, false),
            Arguments.of("2001:8000::/19", false, true),
            Arguments.of("2001:a000::/20", true, true),
            Arguments.of("2001:b000::/20", true, false),
            Arguments.of("2003::/18", false, false),
            Arguments.of("2400::/12", false, true),
            Arguments.of("2410::/12", true, false),
            Arguments.of("2600::/12", false, true),
            Arguments.of("2610::/23", true, false),
            Arguments.of("2620::/23", false, false),
            Arguments.of("2630::/12", false, false),
            Arguments.of("2800::/12", false, false),
            Arguments.of("2a00::/12", false, true),
            Arguments.of("2a10::/12", true, false),
            Arguments.of("2c00::/12", false, false)
        );
    }

    private static Stream<String> nonGloballyReachableIpv6() {
        return Stream.of(
            "::",
            "::1",
            "::ffff:192.0.2.1",
            "::ffff:8.8.8.8",
            "64:ff9b:1::1",
            "100::1",
            "100:0:0:1::1",
            "2001::1",
            "2001:1::",
            "2001:1::4",
            "2001:2::1",
            "2001:4:111::1",
            "2001:4:113::1",
            "2001:10::1",
            "2001:db8::1",
            "2002::1",
            "2001:1000::1",
            "2001:4e00::1",
            "2001:6000::1",
            "2001:c000::1",
            "2003:4000::1",
            "2420::1",
            "2500::1",
            "2610:200::1",
            "2611::1",
            "2620:200::1",
            "2621::1",
            "2640::1",
            "2700::1",
            "2810::1",
            "2900::1",
            "2a20::1",
            "2b00::1",
            "2c10::1",
            "2d00::1",
            "3ffe::1",
            "3ffe:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "3fff::1",
            "3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff",
            "3fff:1000::1",
            "5f00::1",
            "fc00::1",
            "fe80::1"
        );
    }

    private void assertRejected(String value) {
        assertThatThrownBy(() -> policy.validate(value))
            .isInstanceOf(EndpointUrlPolicy.RejectedEndpointException.class);
    }

    private void assertAllowed(String address, String description) {
        assertThatCode(() -> policy.validate("https://[" + address + "]/hook"))
            .as(description + " (" + address + ")")
            .doesNotThrowAnyException();
    }

    private void assertAllowed(String address, boolean expected, String description) {
        if (expected) {
            assertAllowed(address, description);
        } else {
            assertThatThrownBy(() -> policy.validate("https://[" + address + "]/hook"))
                .as(description + " (" + address + ")")
                .isInstanceOf(EndpointUrlPolicy.RejectedEndpointException.class);
        }
    }

    private static Ipv6Range ipv6Range(String cidr) {
        String[] parts = cidr.split("/", 2);
        byte[] bytes = address(parts[0]).getAddress();
        if (bytes.length != 16) throw new AssertionError("Not an IPv6 CIDR: " + cidr);
        int bits = Integer.parseInt(parts[1]);
        BigInteger size = BigInteger.ONE.shiftLeft(128 - bits);
        BigInteger value = new BigInteger(1, bytes);
        BigInteger first = value.divide(size).multiply(size);
        BigInteger last = first.add(size).subtract(BigInteger.ONE);
        return new Ipv6Range(ipv6(first), ipv6(last), ipv6(first.subtract(BigInteger.ONE)),
            ipv6(last.add(BigInteger.ONE)));
    }

    private static String ipv6(BigInteger value) {
        byte[] source = value.toByteArray();
        byte[] bytes = new byte[16];
        int length = Math.min(source.length, bytes.length);
        System.arraycopy(source, source.length - length, bytes, bytes.length - length, length);
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Ipv6Range(String first, String last, String predecessor, String successor) {
    }

    private static InetAddress[] addresses(String... values) {
        return java.util.Arrays.stream(values).map(EndpointUrlPolicyTest::address)
            .toArray(InetAddress[]::new);
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ThreadPoolExecutor boundedExecutor(int workers, int queueCapacity) {
        return new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity), task -> {
                Thread thread = new Thread(task, "test-endpoint-resolver");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    }

    private static void awaitIgnoringInterrupts(CountDownLatch release) {
        while (release.getCount() > 0) {
            try {
                release.await();
            } catch (InterruptedException ignored) {
                // The fixture models a resolver that cannot be interrupted.
            }
        }
    }

    private static void awaitQueueSize(ThreadPoolExecutor executor, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (executor.getQueue().size() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(executor.getQueue()).hasSize(expected);
    }
}
