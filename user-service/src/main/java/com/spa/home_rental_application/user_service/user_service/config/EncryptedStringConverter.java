package com.spa.home_rental_application.user_service.user_service.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA {@link AttributeConverter} that encrypts {@link String} columns
 * at rest with AES-256-GCM. Used to protect sensitive PII like bank
 * account numbers — the column stores ciphertext, but the entity
 * field is a plain {@code String} that callers don't have to think
 * about.
 *
 * <p>Format on disk: {@code ENC:<base64(iv || ciphertext)>}. The
 * {@code ENC:} prefix is a sentinel that lets the reverse path
 * (decrypt) tell encrypted rows apart from legacy plaintext rows
 * that pre-date this converter — those are returned as-is, so an
 * existing deployment can roll this in without a migration. New
 * writes are always encrypted.
 *
 * <p>Key material is loaded once at startup from
 * {@code app.encryption.key} (typically backed by a secret manager
 * in prod). The value is deliberately short-padded / truncated to
 * 32 bytes (AES-256 key size) so an operator can pass any
 * reasonable string without worrying about exact length.
 *
 * <p>Hibernate 6 + Spring Boot 3 auto-wires the
 * {@code BeanContainer}, so making this a {@code @Component} +
 * {@code @Converter} works — Hibernate looks up the bean from the
 * Spring context rather than instantiating it via reflection (which
 * would skip the {@code @Value} injection).
 */
@Component
@Converter
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String SENTINEL = "ENC:";

    private final SecretKey key;

    public EncryptedStringConverter(
            // Default is a placeholder so dev / local can boot without
            // an env var; SecretsBootstrapValidator (or the equivalent
            // production deploy checklist) is responsible for refusing
            // to start the app under the prod profile with this value.
            @Value("${app.encryption.key:CHANGE_ME_LOCAL_DEV_ENCRYPTION_KEY_PLACEHOLDER}")
            String keyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key is required but was not configured");
        }
        // Stretch/truncate to exactly 32 bytes for AES-256. Operators
        // can pass any reasonable string; we don't burden them with
        // exact length requirements. For hardened deployments, pass
        // a 32-byte base64-decoded value sourced from a key manager.
        byte[] raw = keyMaterial.getBytes(StandardCharsets.UTF_8);
        byte[] key32 = Arrays.copyOf(raw, 32);
        this.key = new SecretKeySpec(key32, ALGORITHM);
        log.info("EncryptedStringConverter initialised (AES-256-GCM)");
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);
            return SENTINEL + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) return null;
        // Legacy rows written before this converter rolled in have no
        // sentinel — return them as-is so a deployment can adopt
        // encryption without a back-fill migration. New writes always
        // get the sentinel.
        if (!stored.startsWith(SENTINEL)) return stored;
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(SENTINEL.length()));
            if (combined.length <= IV_BYTES) {
                throw new IllegalStateException("Ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Decryption failures are usually a key-rotation accident;
            // surface loudly so an operator notices. Returning null
            // would silently corrupt the entity load path.
            throw new IllegalStateException("Decryption failed — has the encryption key changed?", e);
        }
    }
}
