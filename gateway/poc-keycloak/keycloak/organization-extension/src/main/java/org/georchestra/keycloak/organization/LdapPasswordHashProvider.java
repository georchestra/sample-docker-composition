package org.georchestra.keycloak.organization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.credential.PasswordCredentialModel;

/**
 * Password hash algorithm "georchestra-ldap": checks the passwords imported from the geOrchestra
 * LDAP with their LDAP hash, stored as is ({@code {SHA}...}, {@code {SSHA}...},
 * {@code {SHA256}}, {@code {SSHA256}}, {@code {SHA512}}, {@code {SSHA512}}, or clear text).
 * <p>
 * Import a user with the credential
 * {@code {"type": "password", "secretData": "{\"value\": \"{SSHA}...\", \"salt\": \"\"}",
 * "credentialData": "{\"hashIterations\": 1, \"algorithm\": \"georchestra-ldap\"}"}}.
 * <p>
 * {@link #policyCheck} never matches the realm policy: at the first successful login Keycloak
 * hashes the password again with the realm algorithm, and the LDAP hash is gone.
 */
public class LdapPasswordHashProvider implements PasswordHashProvider {

    public static final String ID = "georchestra-ldap";

    private static final Map<String, String> DIGESTS = Map.of(
            "SHA", "SHA-1", "SSHA", "SHA-1",
            "SHA256", "SHA-256", "SSHA256", "SHA-256",
            "SHA512", "SHA-512", "SSHA512", "SHA-512");

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public boolean policyCheck(PasswordPolicy policy, PasswordCredentialModel credential) {
        return false;
    }

    /** New passwords are never hashed with this algorithm (see policyCheck), but stay valid */
    @Override
    public PasswordCredentialModel encodedCredential(String rawPassword, int iterations) {
        byte[] salt = new byte[8];
        RANDOM.nextBytes(salt);
        byte[] digest = digest("SHA-1", rawPassword, salt);
        byte[] hashAndSalt = new byte[digest.length + salt.length];
        System.arraycopy(digest, 0, hashAndSalt, 0, digest.length);
        System.arraycopy(salt, 0, hashAndSalt, digest.length, salt.length);
        String value = "{SSHA}" + Base64.getEncoder().encodeToString(hashAndSalt);
        return PasswordCredentialModel.createFromValues(ID, new byte[0], 1, value);
    }

    @Override
    public boolean verify(String rawPassword, PasswordCredentialModel credential) {
        String stored = credential.getPasswordSecretData().getValue();
        if (stored == null || rawPassword == null) {
            return false;
        }
        int end = stored.startsWith("{") ? stored.indexOf('}') : -1;
        if (end < 0) {
            return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
                    rawPassword.getBytes(StandardCharsets.UTF_8));
        }
        String scheme = stored.substring(1, end).toUpperCase(Locale.ROOT);
        String algorithm = DIGESTS.get(scheme);
        if (algorithm == null) {
            return "CLEARTEXT".equals(scheme) && MessageDigest.isEqual(
                    stored.substring(end + 1).getBytes(StandardCharsets.UTF_8),
                    rawPassword.getBytes(StandardCharsets.UTF_8));
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(stored.substring(end + 1).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        int length = digestLength(algorithm);
        if (decoded.length < length || (!scheme.startsWith("S") && decoded.length != length)) {
            return false;
        }
        byte[] expected = new byte[length];
        byte[] salt = new byte[decoded.length - length];
        System.arraycopy(decoded, 0, expected, 0, length);
        System.arraycopy(decoded, length, salt, 0, salt.length);
        return MessageDigest.isEqual(expected, digest(algorithm, rawPassword, salt));
    }

    private static int digestLength(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm).getDigestLength();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] digest(String algorithm, String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            md.update(salt);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
    }
}
