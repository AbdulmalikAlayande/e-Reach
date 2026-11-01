package ng.ereach.facilityserver.iam;

import ng.ereach.facilityserver.iam.security.MfaSecretCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Part B of ER-007, part 1: the authenticator secret is stored only encrypted, with AES-256-GCM and a
 * key from the environment (D-014). Every key here is random and made inside the test.
 */
class MfaSecretCipherTests {

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: F2, D-014</p>
	 */
	@Test
	@DisplayName("TC-007-18 | A secret survives encryption and decryption unchanged")
	void secretSurvivesEncryption() {
		// Given a cipher with a random 32-byte key, and a 20-byte authenticator secret.
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		MfaSecretCipher cipher = new MfaSecretCipher(Base64.getEncoder().encodeToString(key));
		byte[] secret = new byte[20];
		new SecureRandom().nextBytes(secret);

		// When it is encrypted, then decrypted.
		byte[] decrypted = cipher.decrypt(cipher.encrypt(secret));

		// Then the same 20 bytes come back.
		assertArrayEquals(secret, decrypted);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-19 | The stored form is 48 bytes and does not contain the secret")
	void storedFormHidesTheSecret() {
		// Given a cipher and a 20-byte secret.
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		MfaSecretCipher cipher = new MfaSecretCipher(Base64.getEncoder().encodeToString(key));
		byte[] secret = new byte[20];
		new SecureRandom().nextBytes(secret);

		// When it is encrypted.
		byte[] stored = cipher.encrypt(secret);

		// Then it is a 12-byte IV, 20 bytes of ciphertext and a 16-byte tag, and the secret is not inside it.
		assertEquals(12 + 20 + 16, stored.length);
		for (int start = 0; start + secret.length <= stored.length; start++) {
			assertFalse(Arrays.equals(secret, Arrays.copyOfRange(stored, start, start + secret.length)),
					"The secret's bytes must not appear in the stored form");
		}
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, NIST SP 800-38D (a GCM IV is never reused with the same key)</p>
	 */
	@Test
	@DisplayName("TC-007-20 | Every encryption uses a fresh IV")
	void everyEncryptionUsesAFreshIv() {
		// Given a cipher and one secret.
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		MfaSecretCipher cipher = new MfaSecretCipher(Base64.getEncoder().encodeToString(key));
		byte[] secret = new byte[20];
		new SecureRandom().nextBytes(secret);

		// When the same secret is encrypted twice.
		byte[] first = cipher.encrypt(secret);
		byte[] second = cipher.encrypt(secret);

		// Then the two stored forms differ, starting with their IVs, and both decrypt to the secret.
		assertFalse(Arrays.equals(Arrays.copyOf(first, 12), Arrays.copyOf(second, 12)), "The IVs must differ");
		assertFalse(Arrays.equals(first, second));
		assertArrayEquals(secret, cipher.decrypt(first));
		assertArrayEquals(secret, cipher.decrypt(second));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-21 | A stored secret with one changed byte is refused, never decrypted to wrong bytes")
	void changedByteIsDetected() {
		// Given a stored secret with its last byte flipped.
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		MfaSecretCipher cipher = new MfaSecretCipher(Base64.getEncoder().encodeToString(key));
		byte[] stored = cipher.encrypt(new byte[20]);
		stored[stored.length - 1] ^= 0x01;

		// When it is decrypted.
		// Then the change is detected.
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(stored));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-22 | A secret stored under one key cannot be read with another")
	void anotherKeyCannotReadIt() {
		// Given a secret stored under key K, and a cipher with a different key.
		byte[] keyK = new byte[32];
		byte[] otherKey = new byte[32];
		new SecureRandom().nextBytes(keyK);
		new SecureRandom().nextBytes(otherKey);
		byte[] stored = new MfaSecretCipher(Base64.getEncoder().encodeToString(keyK)).encrypt(new byte[20]);
		MfaSecretCipher otherCipher = new MfaSecretCipher(Base64.getEncoder().encodeToString(otherKey));

		// When the other cipher decrypts it.
		// Then it is refused.
		assertThrows(IllegalStateException.class, () -> otherCipher.decrypt(stored));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-23 | Only a base64 key of exactly 32 bytes is accepted")
	void onlyA32ByteKeyIsAccepted() {
		// Given a base64 key of 16 bytes, and text that is not base64.
		String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
		String notBase64 = "not base64!";

		// When a cipher is made with each.
		IllegalStateException shortKey = assertThrows(IllegalStateException.class, () -> new MfaSecretCipher(sixteenBytes));

		// Then the short key is refused, naming the 32 bytes, and the text is refused as not base64.
		assertTrue(shortKey.getMessage().contains("32 bytes"), shortKey.getMessage());
		assertThrows(IllegalArgumentException.class, () -> new MfaSecretCipher(notBase64));
	}

	/**
	 * <p>Level: Integration (a Spring context, no database)</p>
	 * <p>Traces to: D-014, PRD section 8 rule 17</p>
	 */
	@Test
	@DisplayName("TC-007-24 | Without ereach.mfa.encryption-key the application does not start")
	void noKeyNoStart() {
		// Given a Spring context holding MfaSecretCipher, and a random key for the second run.
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
				.withUserConfiguration(MfaSecretCipher.class);
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);

		// When it starts with no key.
		contextRunner.run(context -> {
			// Then it fails, naming the missing setting.
			assertNotNull(context.getStartupFailure(), "The context must not start without a key");
			assertTrue(rootMessages(context.getStartupFailure()).contains("ereach.mfa.encryption-key"),
					rootMessages(context.getStartupFailure()));
		});

		// When it starts with a key, then it holds the cipher.
		contextRunner.withPropertyValues("ereach.mfa.encryption-key=" + Base64.getEncoder().encodeToString(key))
				.run(context -> {
					assertNull(context.getStartupFailure());
					assertNotNull(context.getBean(MfaSecretCipher.class));
				});
	}

	/** Every message in the cause chain, so the test does not depend on how deep Spring wraps the cause. */
	private static String rootMessages(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			messages.append(cause.getMessage()).append(" | ");
		}
		return messages.toString();
	}
}
