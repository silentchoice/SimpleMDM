package com.simplemdm.service.integration;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

public interface EndpointUrlPolicy {
    ValidatedEndpoint validate(String value);

    default ValidatedEndpoint validate(String value, AttemptDeadline deadline) {
        if (deadline.isExpired()) throw new ResolutionTimeoutException();
        return validate(value);
    }

    record ValidatedEndpoint(URI uri, List<InetAddress> addresses) {
        public ValidatedEndpoint {
            addresses = List.copyOf(addresses);
        }
    }

    final class RejectedEndpointException extends RuntimeException {
        public RejectedEndpointException() {
            super("Endpoint URL is not allowed");
        }
    }

    final class ResolutionTimeoutException extends RuntimeException {
        public ResolutionTimeoutException() {
            super("Endpoint resolution deadline exceeded");
        }
    }
}
