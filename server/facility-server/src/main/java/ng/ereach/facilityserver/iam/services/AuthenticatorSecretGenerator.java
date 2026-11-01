package ng.ereach.facilityserver.iam.services;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * TOTP secret generation, provisioning URI, and verification.
 <p>
 * Standards used:
 *   RFC 4226 (HOTP): HMAC truncation and the minimum 128-bit key length (R6).
 *   RFC 6238 (TOTP): time steps, keys the length of the HMAC output (R3),
 *                    and a small window for clock drift (section 5.2).
 *   RFC 4648 (Base32): alphabet and bit packing for the textual secret.
 *   RFC 3986 (URI): percent-encoding of the label and query values.
 </p>
 * The otpauth:// URI is NOT an IETF standard. Its format comes from Google's
 * "Key Uri Format" wiki, which authenticator apps follow by convention.
 */
public final class AuthenticatorSecretGenerator {

    /** HMAC algorithm plus the key length RFC 6238 R3 recommends for it. */
    public enum Algorithm {
        SHA1("HmacSHA1", 20),
        SHA256("HmacSHA256", 32),
        SHA512("HmacSHA512", 64);

        final String jcaName;
        final int keyLengthBytes;

        Algorithm(String jcaName, int keyLengthBytes) {
            this.jcaName = jcaName;
            this.keyLengthBytes = keyLengthBytes;
        }
    }

    /* Parameters that every common authenticator app supports. Google
     Authenticator ignores algorithm/period (and digits on Android), and
     Microsoft Authenticator silently produces wrong codes for SHA-256/512,
     so these defaults should only change when you control the client app.
     */
    public static final Algorithm DEFAULT_ALGORITHM = Algorithm.SHA1;
    public static final int DEFAULT_DIGITS = 6;
    public static final int DEFAULT_PERIOD_SECONDS = 30;

    /** RFC 6238 section 5.2: accept at most this many steps of drift either way. */
    public static final int ALLOWED_DRIFT_STEPS = 1;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000,
            1_000_000, 10_000_000, 100_000_000};
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthenticatorSecretGenerator() {
    }

    // ------------------------------------------------------------------
    // Secret generation
    // ------------------------------------------------------------------

    /** Generates a random secret sized to the HMAC output of the algorithm (RFC 6238 R3). */
    public static byte[] generateSecret(Algorithm algorithm) {
        byte[] secret = new byte[algorithm.keyLengthBytes];
        RANDOM.nextBytes(secret);
        return secret;
    }

    // ------------------------------------------------------------------
    // Base32 (RFC 4648 section 6), padding omitted per the Key Uri Format
    // ------------------------------------------------------------------

    /**
     * Encodes bytes as RFC 4648 Base32 without "=" padding. Padding is dropped
     * because the Key Uri Format says it should be omitted, not because "="
     * is illegal in a URI (RFC 3986 allows it in query values). A 20-byte
     * SHA-1 key is 160 bits, which is exactly 32 characters, so it never pads;
     * 32- and 64-byte keys would pad, which is where omission matters.
     */
    public static String encodeBase32NoPadding(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                out.append(BASE32_ALPHABET.charAt((buffer >> bitsInBuffer) & 0x1F));
            }
        }
        if (bitsInBuffer > 0) {
            // Left-align the remaining bits and pad the group with zero bits.
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsInBuffer)) & 0x1F));
        }
        return out.toString();
    }

    /** Decodes RFC 4648 Base32, tolerating lowercase, spaces and trailing "=" padding. */
    public static byte[] decodeBase32(String text) {
        String cleaned = text.replace(" ", "").replace("=", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(cleaned.length() * 5 / 8);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (char c : cleaned.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                out.put((byte) (buffer >> bitsInBuffer));
            }
        }
        return out.array();
    }

    // ------------------------------------------------------------------
    // Provisioning URI (Google Key Uri Format)
    // ------------------------------------------------------------------

    /** Builds a provisioning URI with the widely supported defaults (SHA1, 6 digits, 30s). */
    public static String buildOtpAuthUri(String issuer, String account, String base32Secret) {
        return buildOtpAuthUri(issuer, account, base32Secret,
                DEFAULT_ALGORITHM, DEFAULT_DIGITS, DEFAULT_PERIOD_SECONDS);
    }

    /**
     * Builds an otpauth://totp URI. The label and the issuer parameter are
     * percent-encoded per RFC 3986, and colons are rejected because the
     * Key Uri Format reserves ":" as the issuer/account separator.
     */
    public static String buildOtpAuthUri(String issuer, String account, String base32Secret,
                                         Algorithm algorithm, int digits, int periodSeconds) {
        requireNonBlank(issuer, "issuer");
        requireNonBlank(account, "account");
        requireNonBlank(base32Secret, "secret");
        if (issuer.contains(":") || account.contains(":")) {
            throw new IllegalArgumentException("Issuer and account name must not contain ':'");
        }
        validateDigits(digits);
        if (periodSeconds <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }

        String encodedIssuer = percentEncode(issuer);
        return "otpauth://totp/" + encodedIssuer + ":" + percentEncode(account)
                + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=" + algorithm.name()
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    /** URLEncoder does form encoding (space becomes "+"), so convert to RFC 3986 "%20". */
    private static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ------------------------------------------------------------------
    // Code generation (RFC 4226 section 5.3 truncation, RFC 6238 time steps)
    // ------------------------------------------------------------------

    /** Returns the RFC 6238 time step (T) for a Unix timestamp, with T0 = 0. */
    public static long timeStep(long epochSeconds, int periodSeconds) {
        return Math.floorDiv(epochSeconds, periodSeconds);
    }

    /** Computes the zero-padded TOTP code for a given time step. */
    public static String generateCode(byte[] secret, long timeStep, Algorithm algorithm, int digits) {
        validateDigits(digits);
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(algorithm.jcaName);
            mac.init(new SecretKeySpec(secret, algorithm.jcaName));
            hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable: " + algorithm.jcaName, e);
        }

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % POWERS_OF_TEN[digits];
        return String.format("%0" + digits + "d", otp);
    }

    // ------------------------------------------------------------------
    // Verification with drift window and replay protection
    // ------------------------------------------------------------------

    /**
     * Verifies a submitted code with the default parameters.
     *
     * @param lastAcceptedStep the time step of the last code this user
     *                         successfully used, or -1 if none. Persist it
     *                         per user so a captured code cannot be replayed.
     * @return the matched time step (store it as the new lastAcceptedStep),
     *         or empty if the code is invalid or already used.
     */
    public static OptionalLong verifyCode(byte[] secret, String submittedCode,
                                          long epochSeconds, long lastAcceptedStep) {
        return verifyCode(secret, submittedCode, epochSeconds, lastAcceptedStep,
                DEFAULT_ALGORITHM, DEFAULT_DIGITS, DEFAULT_PERIOD_SECONDS);
    }

    public static OptionalLong verifyCode(byte[] secret, String submittedCode, long epochSeconds,
                                          long lastAcceptedStep, Algorithm algorithm,
                                          int digits, int periodSeconds) {
        if (Objects.requireNonNull(submittedCode).length() != digits
                || !submittedCode.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] submitted = submittedCode.getBytes(StandardCharsets.US_ASCII);
        long current = timeStep(epochSeconds, periodSeconds);

        // Check every step in the window so timing does not reveal which one matched.
        long matched = -1;
        for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
            byte[] expected = generateCode(secret, step, algorithm, digits)
                    .getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, submitted) && step > lastAcceptedStep && matched < 0) {
                matched = step;
            }
        }
        return matched >= 0 ? OptionalLong.of(matched) : OptionalLong.empty();
    }

    private static void validateDigits(int digits) {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("Digits must be 6, 7 or 8 (RFC 4226 R4)");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (Objects.requireNonNull(value).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /**
     * Demo only. In production never log the secret or the URI (the URI
     * contains the secret). Store the secret encrypted at rest; it cannot be
     * hashed, because the server needs the raw key to verify codes.
     */
    public static void main(String[] args) {
        byte[] secret = generateSecret(DEFAULT_ALGORITHM);
        String base32Secret = encodeBase32NoPadding(secret);
        String uri = buildOtpAuthUri("My App", "user@example.com", base32Secret);

        System.out.println("Provisioning URI (render as QR code): " + uri);

        long now = Instant.now().getEpochSecond();
        String code = generateCode(secret, timeStep(now, DEFAULT_PERIOD_SECONDS),
                DEFAULT_ALGORITHM, DEFAULT_DIGITS);

        OptionalLong first = verifyCode(secret, code, now, -1);
        OptionalLong replay = verifyCode(secret, code, now, first.orElse(-1));
        System.out.println("First use accepted: " + first.isPresent());
        System.out.println("Replay accepted:    " + replay.isPresent());
    }
}