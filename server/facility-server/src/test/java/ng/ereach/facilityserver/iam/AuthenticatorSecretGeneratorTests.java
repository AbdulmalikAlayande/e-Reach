package ng.ereach.facilityserver.iam;

import ng.ereach.facilityserver.iam.services.AuthenticatorSecretGenerator;
import ng.ereach.facilityserver.iam.services.AuthenticatorSecretGenerator.Algorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Part A of ER-007, part 1: the TOTP building blocks the installer's authenticator step relies on (D-014).
 * Codes are checked against the test vectors of RFC 6238 Appendix B, whose secret is the ASCII text
 * "12345678901234567890" for SHA-1.
 */
class AuthenticatorSecretGeneratorTests {

	/** RFC 6238 Appendix B: the SHA-1 test secret. */
	private static final byte[] RFC_SHA1_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	/** The moment TC-007-10 to TC-007-13 verify at: RFC time 1111111111, which is time step 37037037. */
	private static final long NOW = 1_111_111_111L;
	private static final long NOW_STEP = 37_037_037L;

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, RFC 6238 R3</p>
	 */
	@Test
	@DisplayName("TC-007-01 | A secret is as long as the HMAC output, and random")
	void secretLengthFollowsTheAlgorithm() {
		// Given nothing.

		// When secrets are generated.
		byte[] first = AuthenticatorSecretGenerator.generateSecret(Algorithm.SHA1);
		byte[] second = AuthenticatorSecretGenerator.generateSecret(Algorithm.SHA1);
		byte[] sha256 = AuthenticatorSecretGenerator.generateSecret(Algorithm.SHA256);
		byte[] sha512 = AuthenticatorSecretGenerator.generateSecret(Algorithm.SHA512);

		// Then each is as long as its HMAC output, and two SHA-1 secrets differ.
		assertEquals(20, first.length);
		assertEquals(32, sha256.length);
		assertEquals(64, sha512.length);
		assertFalse(Arrays.equals(first, second), "Two secrets must not be the same");
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, RFC 4648 section 10</p>
	 */
	@Test
	@DisplayName("TC-007-02 | Base32 follows RFC 4648, without padding")
	void base32MatchesTheRfcExamples() {
		// Given the RFC 4648 section 10 inputs, and their encodings with the padding removed.
		String[][] examples = {
				{"f", "MY"}, {"fo", "MZXQ"}, {"foo", "MZXW6"},
				{"foob", "MZXW6YQ"}, {"fooba", "MZXW6YTB"}, {"foobar", "MZXW6YTBOI"},
		};

		// When each is encoded.
		// Then each matches the RFC, and none carries a "=".
		assertAll(Arrays.stream(examples).map(example -> (Executable) () -> {
			String encoded = AuthenticatorSecretGenerator.encodeBase32NoPadding(example[0].getBytes(StandardCharsets.US_ASCII));
			assertEquals(example[1], encoded, "Base32 of \"" + example[0] + "\"");
			assertFalse(encoded.contains("="), "No padding in \"" + encoded + "\"");
		}));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-03 | Base32 decodes what users type: lower case, spaces and padding")
	void base32DecodesWhatUsersType() {
		// Given a key typed in lower case, with spaces, and with padding.
		byte[] foobar = "foobar".getBytes(StandardCharsets.US_ASCII);

		// When each is decoded.
		// Then each gives the bytes of "foobar".
		assertArrayEquals(foobar, AuthenticatorSecretGenerator.decodeBase32("mzxw6ytboi"));
		assertArrayEquals(foobar, AuthenticatorSecretGenerator.decodeBase32("MZXW 6YTB OI"));
		assertArrayEquals(foobar, AuthenticatorSecretGenerator.decodeBase32("MZXW6YTBOI======"));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-04 | Base32 refuses a character outside its alphabet")
	void base32RefusesForeignCharacters() {
		// Given a key holding "1", which Base32 does not use.
		String typed = "MZXW1";

		// When it is decoded.
		// Then it is refused.
		assertThrows(IllegalArgumentException.class, () -> AuthenticatorSecretGenerator.decodeBase32(typed));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-05 | The manual entry key is 32 Base32 characters with no padding")
	void manualEntryKeyHasAFixedShape() {
		// Given a new SHA-1 secret.
		byte[] secret = AuthenticatorSecretGenerator.generateSecret(Algorithm.SHA1);

		// When it is encoded for the user to type.
		String manualEntryKey = AuthenticatorSecretGenerator.encodeBase32NoPadding(secret);

		// Then it is exactly 32 characters from A to Z and 2 to 7.
		assertTrue(manualEntryKey.matches("^[A-Z2-7]{32}$"), manualEntryKey);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, RFC 6238 Appendix B</p>
	 */
	@Test
	@DisplayName("TC-007-06 | Codes match the RFC 6238 SHA-1 test vectors")
	void codesMatchTheRfcSha1Vectors() {
		// Given the SHA-1 test secret and the RFC's times with their 8-digit codes.
		long[] times = {59L, 1_111_111_109L, 1_111_111_111L, 1_234_567_890L, 2_000_000_000L, 20_000_000_000L};
		String[] codes = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};

		// When the code for each time step is made.
		// Then each matches the RFC.
		assertAll(IntStream.range(0, times.length).mapToObj(i -> (Executable) () -> assertEquals(codes[i],
				AuthenticatorSecretGenerator.generateCode(RFC_SHA1_SECRET,
						AuthenticatorSecretGenerator.timeStep(times[i], 30), Algorithm.SHA1, 8),
				"Code at time " + times[i])));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-07 | A six-digit code keeps its leading zero")
	void sixDigitCodeKeepsItsLeadingZero() {
		// Given the SHA-1 test secret at time 1111111109, whose 8-digit RFC code is 07081804.
		long step = AuthenticatorSecretGenerator.timeStep(1_111_111_109L, 30);

		// When the 6-digit code is made.
		String code = AuthenticatorSecretGenerator.generateCode(RFC_SHA1_SECRET, step, Algorithm.SHA1, 6);

		// Then it is the last six digits, with the leading zero kept.
		assertEquals("081804", code);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: RFC 6238 Appendix B</p>
	 */
	@Test
	@DisplayName("TC-007-08 | Codes match the RFC 6238 SHA-256 and SHA-512 test vectors")
	void codesMatchTheRfcSha256AndSha512Vectors() {
		// Given the RFC's SHA-256 and SHA-512 test secrets at time 59.
		byte[] sha256Secret = "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);
		byte[] sha512Secret = "1234567890".repeat(6).concat("1234").getBytes(StandardCharsets.US_ASCII);
		long step = AuthenticatorSecretGenerator.timeStep(59L, 30);

		// When 8-digit codes are made.
		String sha256Code = AuthenticatorSecretGenerator.generateCode(sha256Secret, step, Algorithm.SHA256, 8);
		String sha512Code = AuthenticatorSecretGenerator.generateCode(sha512Secret, step, Algorithm.SHA512, 8);

		// Then they match the RFC.
		assertEquals("46119246", sha256Code);
		assertEquals("90693936", sha512Code);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, RFC 6238 section 4</p>
	 */
	@Test
	@DisplayName("TC-007-09 | Time steps are 30 seconds counted from the epoch")
	void timeStepsAreThirtySecondsFromTheEpoch() {
		// Given times on both sides of the first two step boundaries.
		// When the step is computed for each.
		// Then each falls in the right step.
		assertEquals(0L, AuthenticatorSecretGenerator.timeStep(0L, 30));
		assertEquals(0L, AuthenticatorSecretGenerator.timeStep(29L, 30));
		assertEquals(1L, AuthenticatorSecretGenerator.timeStep(30L, 30));
		assertEquals(1L, AuthenticatorSecretGenerator.timeStep(59L, 30));
		assertEquals(2L, AuthenticatorSecretGenerator.timeStep(60L, 30));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-10 | The current code is accepted and reports its time step")
	void currentCodeIsAccepted() {
		// Given the SHA-1 test secret at time 1111111111, and no step used yet.
		String currentCode = "050471"; // the last six digits of the RFC code 14050471

		// When the current code is verified.
		OptionalLong matched = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, currentCode, NOW, -1);

		// Then it is accepted, matching the current step.
		assertEquals(OptionalLong.of(NOW_STEP), matched);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, RFC 6238 section 5.2</p>
	 */
	@Test
	@DisplayName("TC-007-11 | A code one step early or late is accepted")
	void oneStepOfDriftIsAccepted() {
		// Given the codes of the steps just before and just after the current one.
		String previousCode = "081804"; // RFC time 1111111109 is step 37037036
		String nextCode = AuthenticatorSecretGenerator.generateCode(RFC_SHA1_SECRET, NOW_STEP + 1, Algorithm.SHA1, 6);

		// When each is verified at the current time.
		OptionalLong previous = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, previousCode, NOW, -1);
		OptionalLong next = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, nextCode, NOW, -1);

		// Then both are accepted, each reporting its own step.
		assertEquals(OptionalLong.of(NOW_STEP - 1), previous);
		assertEquals(OptionalLong.of(NOW_STEP + 1), next);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-12 | A code two steps early or late is refused")
	void twoStepsOfDriftAreRefused() {
		// Given the codes of the steps two before and two after the current one.
		String twoEarly = AuthenticatorSecretGenerator.generateCode(RFC_SHA1_SECRET, NOW_STEP - 2, Algorithm.SHA1, 6);
		String twoLate = AuthenticatorSecretGenerator.generateCode(RFC_SHA1_SECRET, NOW_STEP + 2, Algorithm.SHA1, 6);

		// When each is verified at the current time.
		OptionalLong early = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, twoEarly, NOW, -1);
		OptionalLong late = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, twoLate, NOW, -1);

		// Then both are refused.
		assertTrue(early.isEmpty(), "A code two steps early must be refused");
		assertTrue(late.isEmpty(), "A code two steps late must be refused");
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: F2 ("an authenticator code that was accepted once is refused the second time"), D-014</p>
	 */
	@Test
	@DisplayName("TC-007-13 | A code that was accepted once is refused the second time")
	void acceptedCodeNeverWorksTwice() {
		// Given the current code, already accepted at the current step.
		String currentCode = "050471";

		// When it is verified again, then with the last accepted step one lower.
		OptionalLong replay = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, currentCode, NOW, NOW_STEP);
		OptionalLong afterEarlierStep = AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, currentCode, NOW, NOW_STEP - 1);

		// Then the replay is refused, and the code is accepted when only an earlier step was used.
		assertTrue(replay.isEmpty(), "A code from an already used step must be refused");
		assertEquals(OptionalLong.of(NOW_STEP), afterEarlierStep);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-14 | A malformed code is refused without an exception")
	void malformedCodesAreRefusedQuietly() {
		// Given codes that are too short, too long, hold a letter, or start with a space.
		String[] malformed = {"05047", "0504711", "05a471", " 50471"};

		// When each is verified.
		// Then each is refused, and nothing is thrown.
		assertAll(Arrays.stream(malformed).map(code -> (Executable) () -> assertTrue(
				assertDoesNotThrow(() -> AuthenticatorSecretGenerator.verifyCode(RFC_SHA1_SECRET, code, NOW, -1)).isEmpty(),
				"\"" + code + "\" must be refused")));
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: D-014, Google's Key Uri Format</p>
	 */
	@Test
	@DisplayName("TC-007-15 | The provisioning URI has the agreed shape")
	void provisioningUriHasTheAgreedShape() {
		// Given the e-Reach issuer, an operator's username and a Base32 secret.
		String issuer = "e-Reach";
		String account = "amina.bello";
		String secret = "JBSWY3DPEHPK3PXP";

		// When the URI is built.
		String uri = AuthenticatorSecretGenerator.buildOtpAuthUri(issuer, account, secret);

		// Then it names the issuer twice, the secret, SHA1, 6 digits and 30 seconds.
		assertEquals("otpauth://totp/e-Reach:amina.bello?secret=JBSWY3DPEHPK3PXP&issuer=e-Reach"
				+ "&algorithm=SHA1&digits=6&period=30", uri);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: RFC 3986</p>
	 */
	@Test
	@DisplayName("TC-007-16 | The label and the issuer are percent-encoded")
	void labelAndIssuerArePercentEncoded() {
		// Given an issuer and an account holding spaces and an ampersand.
		String issuer = "My App & Co";
		String account = "amina bello";

		// When the URI is built.
		String uri = AuthenticatorSecretGenerator.buildOtpAuthUri(issuer, account, "JBSWY3DPEHPK3PXP");

		// Then spaces become %20, never "+", and "&" becomes %26, in the label and in the issuer parameter.
		assertTrue(uri.startsWith("otpauth://totp/My%20App%20%26%20Co:amina%20bello?"), uri);
		assertTrue(uri.contains("&issuer=My%20App%20%26%20Co&"), uri);
		assertFalse(uri.contains("+"), uri);
	}

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: Google's Key Uri Format, RFC 4226 R4</p>
	 */
	@Test
	@DisplayName("TC-007-17 | Unusable provisioning URI inputs are refused")
	void unusableUriInputsAreRefused() {
		// Given a usable secret.
		String secret = "JBSWY3DPEHPK3PXP";

		// When the URI is built with a colon in the issuer or the account, a blank issuer,
		// 5 or 9 digits, or a period of 0.
		// Then each is refused.
		assertAll(
				() -> assertThrows(IllegalArgumentException.class,
						() -> AuthenticatorSecretGenerator.buildOtpAuthUri("e-Reach:Lagos", "amina.bello", secret)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> AuthenticatorSecretGenerator.buildOtpAuthUri("e-Reach", "amina:bello", secret)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> AuthenticatorSecretGenerator.buildOtpAuthUri("  ", "amina.bello", secret)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> AuthenticatorSecretGenerator.buildOtpAuthUri("e-Reach", "amina.bello", secret, Algorithm.SHA1, 5, 30)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> AuthenticatorSecretGenerator.buildOtpAuthUri("e-Reach", "amina.bello", secret, Algorithm.SHA1, 9, 30)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> AuthenticatorSecretGenerator.buildOtpAuthUri("e-Reach", "amina.bello", secret, Algorithm.SHA1, 6, 0)));
	}
}
