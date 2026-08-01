package com.simplemdm.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CredentialEncryptionService {
    private static final String VERSION = "v1";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 32;
    private static final int MAX_VALUE_LENGTH = 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = 4096;
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}$");
    private static final Set<String> RESERVED_HEADERS = Set.of(
        "accept", "authorization", "connection", "content-length", "content-type", "host",
        "transfer-encoding", "x-simplemdm-event-id");

    private final ObjectMapper json;
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public CredentialEncryptionService(ObjectMapper json,
                                       @Value("${simple-mdm.integration.key:}") String encodedKey) {
        this.json = json;
        this.encodedKey = encodedKey;
    }

    public String encrypt(String authenticationType, Map<String, String> credentials) {
        Map<String, String> validated = validatedCredentials(authenticationType, credentials);
        if (validated.isEmpty()) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(json.writeValueAsBytes(validated));
            String encoded = VERSION + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
            if (encoded.length() > MAX_CIPHERTEXT_LENGTH) throw new CredentialUnavailableException();
            return encoded;
        } catch (GeneralSecurityException | JsonProcessingException exception) {
            throw new CredentialUnavailableException();
        }
    }

    public Map<String, String> requestHeaders(String authenticationType, String encryptedCredentials) {
        if ("NONE".equals(authenticationType)) return Map.of();
        if (encryptedCredentials == null || encryptedCredentials.isBlank()) throw new CredentialUnavailableException();
        Map<String, String> credentials = decrypt(encryptedCredentials);
        Map<String, String> validated = validatedCredentials(authenticationType, credentials);
        return switch (authenticationType) {
            case "BASIC" -> Map.of("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (validated.get("username") + ":" + validated.get("password")).getBytes(StandardCharsets.UTF_8)));
            case "BEARER" -> Map.of("Authorization", "Bearer " + validated.get("token"));
            case "API_KEY" -> Map.of(validated.get("header_name"), validated.get("value"));
            default -> throw new CredentialUnavailableException();
        };
    }

    private Map<String, String> decrypt(String encryptedCredentials) {
        try {
            String[] parts = encryptedCredentials.split("\\.", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) throw new CredentialUnavailableException();
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            if (iv.length != IV_LENGTH) throw new CredentialUnavailableException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            Map<String, String> decoded = json.readValue(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])),
                new TypeReference<LinkedHashMap<String, String>>() { });
            if (decoded == null) throw new CredentialUnavailableException();
            return decoded;
        } catch (GeneralSecurityException | IllegalArgumentException | IOException exception) {
            throw new CredentialUnavailableException();
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey.trim());
            if (key.length != KEY_LENGTH) throw new CredentialUnavailableException();
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new CredentialUnavailableException();
        }
    }

    private Map<String, String> validatedCredentials(String authenticationType, Map<String, String> credentials) {
        if ("NONE".equals(authenticationType)) {
            if (credentials == null || credentials.isEmpty()) return Map.of();
            throw new CredentialUnavailableException();
        }
        if (credentials == null) throw new CredentialUnavailableException();
        return switch (authenticationType) {
            case "BASIC" -> basic(credentials);
            case "BEARER" -> required(credentials, Set.of("token"));
            case "API_KEY" -> apiKey(credentials);
            default -> throw new CredentialUnavailableException();
        };
    }

    private Map<String, String> required(Map<String, String> credentials, Set<String> keys) {
        if (!credentials.keySet().equals(keys)) throw new CredentialUnavailableException();
        Map<String, String> copy = new LinkedHashMap<>();
        for (String key : keys) copy.put(key, requiredValue(credentials.get(key)));
        return copy;
    }

    private Map<String, String> apiKey(Map<String, String> credentials) {
        Map<String, String> copy = required(credentials, Set.of("header_name", "value"));
        String headerName = copy.get("header_name");
        if (!HEADER_NAME.matcher(headerName).matches()
            || RESERVED_HEADERS.contains(headerName.toLowerCase(java.util.Locale.ROOT))) {
            throw new CredentialUnavailableException();
        }
        return copy;
    }

    private Map<String, String> basic(Map<String, String> credentials) {
        Map<String, String> copy = required(credentials, Set.of("username", "password"));
        if (copy.get("username").indexOf(':') >= 0) throw new CredentialUnavailableException();
        return copy;
    }

    private String requiredValue(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH
            || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new CredentialUnavailableException();
        }
        return value;
    }

    public static class CredentialUnavailableException extends RuntimeException {
        public CredentialUnavailableException() {
            super("Integration credentials are unavailable");
        }
    }
}
