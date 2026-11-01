package ng.ereach.facilityserver.iam;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jayway.jsonpath.JsonPath;
import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.facilityserver.iam.security.MfaSecretCipher;
import ng.ereach.facilityserver.iam.services.AuthenticatorSecretGenerator;
import ng.ereach.facilityserver.iam.services.AuthenticatorSecretGenerator.Algorithm;
import ng.ereach.shared.id.Ids;
import ng.ereach.shared.id.SystemActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Parts D and E of ER-007, part 1: the installer's first-run step, through the setup endpoints of D-029,
 * against the real application and PostgreSQL. Written before the endpoints exist, so every case fails
 * until they do.
 *
 * <p>Every request carries the setup code, a new Idempotency-Key and a CSRF token, unless the case says
 * otherwise. Time is fixed at 01/11/2026 09:00 UTC through the Clock bean, so authenticator codes are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PlatformOperatorSetupTests extends PostgresIntegrationTest {

	/** The code the installer would show on the server's screen; made fresh for each test run (D-029). */
	static final String SETUP_CODE = newSetupCode();

	/** The fixed moment of every case, and its 30-second authenticator time step. */
	static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");
	static final long NOW_STEP = NOW.getEpochSecond() / 30;

	/** The operator of every case: Amina Bello. */
	static final String FULL_NAME = "Amina Bello";
	static final String USERNAME = "amina.bello";
	static final String EMAIL = "amina.bello@primexhospital.ng";
	static final String PHONE = "+2348031234567";
	static final String PASSWORD = "Ikeja-General-26";
	static final String PIN = "4821";

	private static final String SETUP = "/api/v1/setup/platform-operator";
	private static final String MFA = SETUP + "/mfa";
	private static final String CONFIRM = MFA + "/confirm";

	@DynamicPropertySource
	static void setupCode(DynamicPropertyRegistry registry) {
		registry.add("ereach.setup.code", () -> SETUP_CODE);
	}

	@TestConfiguration
	static class FixedClock {
		@Bean
		@Primary
		Clock fixedTestClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

	@Value("${spring.datasource.url}")
	String databaseUrl;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MfaSecretCipher secretCipher;

	@BeforeEach
	void startFromANewServer() {
		// As the table owner: empty everything that refers to a person or a facility, then remove every
		// person account, so only the four system actors remain (D-017, D-030).
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE mfa_challenge, mfa_method, practitioner_profile, role_assignment, facility_membership, "
				+ "user_security_key, facility_setting, clinic, facility, lga, state");
		owner.update("DELETE FROM user_account WHERE kind = 'person'");
	}

	// ------------------------------------------------------------------ requests

	private static String newSetupCode() {
		byte[] code = new byte[16];
		new SecureRandom().nextBytes(code);
		return AuthenticatorSecretGenerator.encodeBase32NoPadding(code);
	}

	/** The JSON the first-run screen sends; a null value is left out, as a screen that never asked for it would. */
	private static String operator(String fullName, String username, String email, String password, String pin) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fullName", fullName);
		body.put("username", username);
		if (email != null) {
			body.put("email", email);
		}
		body.put("phone", PHONE);
		body.put("password", password);
		body.put("pin", pin);
		return JsonMapper.builder().build().writeValueAsString(body);
	}

	private ResultActions postAsInstaller(String path, String json, String setupCode) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json)
				.header("Idempotency-Key", Ids.newId().toString()).with(csrf());
		if (setupCode != null) {
			request.header("X-Setup-Code", setupCode);
		}
		return mockMvc.perform(request);
	}

	private ResultActions createOperator(String json) throws Exception {
		return postAsInstaller(SETUP, json, SETUP_CODE);
	}

	private ResultActions startSetup(String method) throws Exception {
		return postAsInstaller(MFA, "{\"method\": \"" + method + "\"}", SETUP_CODE);
	}

	private ResultActions confirm(String code) throws Exception {
		return postAsInstaller(CONFIRM, "{\"method\": \"totp\", \"code\": \"" + code + "\"}", SETUP_CODE);
	}

	/** The authenticator code an app holding this manual key shows at the given time step. */
	private static String codeFor(String manualEntryKey, long step) {
		return AuthenticatorSecretGenerator.generateCode(
				AuthenticatorSecretGenerator.decodeBase32(manualEntryKey), step, Algorithm.SHA1, 6);
	}

	private static String read(ResultActions result, String jsonPath) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
	}

	private long liveOperators() {
		return jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM user_account WHERE kind = 'person' AND is_platform_operator AND deleted_at IS NULL
				""", Long.class);
	}

	private long personAccounts() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE kind = 'person'", Long.class);
	}

	// ------------------------------------------------------------------ part D: the installer step

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F1, D-008, D-009, D-023, D-028</p>
	 */
	@Test
	@DisplayName("TC-007-34 | The step creates the first operator: a person, active, flagged, authored by the installer")
	void stepCreatesTheFirstOperator() throws Exception {
		// Given a new server, where only the four system actors exist.

		// When the step runs with the operator's details.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value(USERNAME))
				.andExpect(jsonPath("$.fullName").value(FULL_NAME))
				.andExpect(jsonPath("$.email").value(EMAIL));

		// Then one live person account holds the details, is an operator, needs MFA now, and was written by the INSTALLER.
		Map<String, Object> row = jdbcTemplate.queryForMap("""
				SELECT full_name, username, email, phone, status, is_platform_operator, must_change_password,
				       created_by, updated_by
				FROM user_account WHERE kind = 'person' AND deleted_at IS NULL
				""");
		assertEquals(FULL_NAME, row.get("full_name"));
		assertEquals(USERNAME, row.get("username"));
		assertEquals(EMAIL, row.get("email"));
		assertEquals(PHONE, row.get("phone"));
		assertEquals("active", row.get("status"));
		assertEquals(true, row.get("is_platform_operator"));
		assertEquals(false, row.get("must_change_password"));
		assertEquals(SystemActors.INSTALLER.uuid(), row.get("created_by"));
		assertEquals(SystemActors.INSTALLER.uuid(), row.get("updated_by"));
		assertEquals(NOW, jdbcTemplate.queryForObject(
				"SELECT mfa_required_by FROM user_account WHERE kind = 'person'", OffsetDateTime.class).toInstant());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2, D-021</p>
	 */
	@Test
	@DisplayName("TC-007-35 | A username is stored exactly as typed")
	void usernameIsStoredAsTyped() throws Exception {
		// Given a new server.

		// When the step runs with the username Amina.Bello.
		createOperator(operator(FULL_NAME, "Amina.Bello", EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// Then it is stored with its capitals.
		assertEquals("Amina.Bello",
				jdbcTemplate.queryForObject("SELECT username FROM user_account WHERE kind = 'person'", String.class));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2, ADR-007</p>
	 */
	@Test
	@DisplayName("TC-007-36 | The password is kept only as an Argon2id hash")
	void passwordIsKeptOnlyAsAnArgon2idHash() throws Exception {
		// Given a new server.

		// When the step runs.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// Then the stored value is an Argon2id hash of the password, not the password.
		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM user_account WHERE kind = 'person'", String.class);
		assertTrue(hash.startsWith("$argon2id$"), hash);
		assertNotEquals(PASSWORD, hash);
		assertTrue(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().matches(PASSWORD, hash));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2, ADR-007</p>
	 */
	@Test
	@DisplayName("TC-007-37 | The PIN is kept only as an Argon2id hash")
	void pinIsKeptOnlyAsAnArgon2idHash() throws Exception {
		// Given a new server.

		// When the step runs.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// Then the stored value is an Argon2id hash of the PIN, not the PIN.
		String hash = jdbcTemplate.queryForObject("SELECT pin_hash FROM user_account WHERE kind = 'person'", String.class);
		assertTrue(hash.startsWith("$argon2id$"), hash);
		assertNotEquals(PIN, hash);
		assertTrue(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().matches(PIN, hash));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2 (at least 10 characters), PRD section 10</p>
	 */
	@Test
	@DisplayName("TC-007-38 | A password shorter than 10 characters is refused, without echoing it")
	void shortPasswordIsRefused() throws Exception {
		// Given a new server, and a 9-character password.
		String shortPassword = "Ikeja-26!";

		// When the step runs with it.
		ResultActions result = createOperator(operator(FULL_NAME, USERNAME, EMAIL, shortPassword, PIN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("password")));

		// Then the password is nowhere in the answer, and nothing was saved.
		assertFalse(result.andReturn().getResponse().getContentAsString().contains(shortPassword));
		assertEquals(0, personAccounts());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2 (a 4 to 6 digit PIN)</p>
	 */
	@Test
	@DisplayName("TC-007-39 | A PIN must be 4 to 6 digits")
	void pinMustBeFourToSixDigits() throws Exception {
		// Given a new server, and PINs that are too short, too long and not all digits.
		String[] refused = {"482", "4821503", "48a1"};

		for (String pin : refused) {
			// When the step runs with each.
			ResultActions result = createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, pin))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[*].field", hasItem("pin")));

			// Then it is refused without the PIN in the answer.
			assertFalse(result.andReturn().getResponse().getContentAsString().contains("\"" + pin + "\""), pin);
		}
		// And nothing was saved.
		assertEquals(0, personAccounts());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2, D-022, PRD section 10</p>
	 */
	@Test
	@DisplayName("TC-007-40 | Name, username and email are checked before anything is saved")
	void nameUsernameAndEmailAreCheckedFirst() throws Exception {
		// Given a new server, and four bodies that each break one rule.
		Map<String, String> bodies = new LinkedHashMap<>();
		bodies.put("fullName", operator("   ", USERNAME, EMAIL, PASSWORD, PIN));
		bodies.put("username", operator(FULL_NAME, "amina bello", EMAIL, PASSWORD, PIN));
		bodies.put("email", operator(FULL_NAME, USERNAME, null, PASSWORD, PIN));
		bodies.put("email ", operator(FULL_NAME, USERNAME, "amina", PASSWORD, PIN));

		for (Map.Entry<String, String> body : bodies.entrySet()) {
			// When the step runs with each.
			// Then it is a validation problem naming the field, never a 500 from a database check.
			createOperator(body.getValue())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[*].field", hasItem(body.getKey().trim())));
		}
		// And nothing was saved.
		assertEquals(0, personAccounts());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-021, D-025</p>
	 */
	@Test
	@DisplayName("TC-007-41 | A taken username gets USERNAME_TAKEN, without repeating the username")
	void takenUsernameHasItsOwnAnswer() throws Exception {
		// Given a new server, whose system actor system.installer already holds that username ignoring case.

		// When the step runs with the username System.Installer.
		ResultActions result = createOperator(operator(FULL_NAME, "System.Installer", EMAIL, PASSWORD, PIN))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
				.andExpect(jsonPath("$.detail").value("That username is not available. Choose another."));

		// Then the answer does not repeat the username, and nothing was saved.
		assertFalse(result.andReturn().getResponse().getContentAsString().toLowerCase().contains("system.installer"));
		assertEquals(0, personAccounts());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F1 acceptance criterion 1, D-008, D-029</p>
	 */
	@Test
	@DisplayName("TC-007-42 | Once the first operator is confirmed, the step cannot make a second one")
	void noSecondOperatorAfterTheFirstIsConfirmed() throws Exception {
		// Given a confirmed operator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");
		confirm(codeFor(key, NOW_STEP)).andExpect(status().isOk());

		// When the step runs again with another username and email.
		createOperator(operator("Chidi Okafor", "chidi.okafor", "chidi.okafor@primexhospital.ng", PASSWORD, PIN))
				// Then it is not found, and there is still exactly one operator.
				.andExpect(status().isNotFound());
		assertEquals(1, liveOperators());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-008, D-009</p>
	 */
	@Test
	@DisplayName("TC-007-43 | The four system accounts do not count as operators")
	void systemAccountsDoNotCountAsOperators() throws Exception {
		// Given a new server with its four system actors.
		assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE kind = 'system'", Long.class));

		// When the step runs.
		// Then it goes ahead.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-008, D-024</p>
	 */
	@Test
	@DisplayName("TC-007-44 | Two runs at the same moment leave exactly one operator")
	void twoRunsAtOnceLeaveOneOperator() throws Exception {
		// Given a new server, and two runs held at a starting line.
		CountDownLatch startingLine = new CountDownLatch(1);
		List<Future<Integer>> runs = new ArrayList<>();
		try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
			for (String name : List.of("amina.bello", "chidi.okafor")) {
				runs.add(threads.submit(() -> {
					startingLine.await();
					return createOperator(operator(FULL_NAME, name, name + "@primexhospital.ng", PASSWORD, PIN))
							.andReturn().getResponse().getStatus();
				}));
			}

			// When both start at once.
			startingLine.countDown();

			// Then each answers 201 or 409, and exactly one live operator account exists.
			for (Future<Integer> run : runs) {
				int status = run.get();
				assertTrue(status == 201 || status == 409, "Unexpected status " + status);
			}
		}
		assertEquals(1, liveOperators());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-024</p>
	 */
	@Test
	@DisplayName("TC-007-45 | A run abandoned before MFA was confirmed can be done again")
	void abandonedRunCanBeDoneAgain() throws Exception {
		// Given a run that created the operator but never confirmed MFA.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// When the step runs again with the same username and email.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// Then the first account is soft-deleted and the second is the only live operator.
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM user_account WHERE kind = 'person' AND deleted_at IS NOT NULL", Long.class));
		assertEquals(1, liveOperators());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-014, D-026, transcript of 01/11/2026</p>
	 */
	@Test
	@DisplayName("TC-007-46 | Authenticator setup answers with a QR image and a manual key, and nothing else")
	void authenticatorSetupAnswersWithAQrCodeAndAKey() throws Exception {
		// Given the operator has been created.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// When authenticator setup starts.
		// Then the answer is pending, holds a PNG data URI and a 32-character Base32 key, and no other method's fields.
		startSetup("totp")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.method").value("totp"))
				.andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
				.andExpect(jsonPath("$.qrCodeImage", startsWith("data:image/png;base64,")))
				.andExpect(jsonPath("$.manualEntryKey", matchesPattern("^[A-Z2-7]{32}$")))
				.andExpect(jsonPath("$.codeSentTo").doesNotExist())
				.andExpect(jsonPath("$.codeExpiresAt").doesNotExist())
				.andExpect(jsonPath("$.passkeyOptions").doesNotExist());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-014, D-027, A1</p>
	 */
	@Test
	@DisplayName("TC-007-47 | The QR code holds the operator's provisioning URI")
	void qrCodeHoldsTheProvisioningUri() throws Exception {
		// Given the setup answer.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		ResultActions setup = startSetup("totp").andExpect(status().isOk());
		String key = read(setup, "$.manualEntryKey");
		String image = read(setup, "$.qrCodeImage");

		// When the PNG is decoded with ZXing.
		byte[] png = Base64.getDecoder().decode(image.substring("data:image/png;base64,".length()));
		BufferedImage picture = ImageIO.read(new ByteArrayInputStream(png));
		int[] pixels = picture.getRGB(0, 0, picture.getWidth(), picture.getHeight(), null, 0, picture.getWidth());
		LuminanceSource luminance = new RGBLuminanceSource(picture.getWidth(), picture.getHeight(), pixels);
		String uri = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(luminance))).getText();

		// Then it is the operator's URI, with the manual key as its secret.
		assertEquals("otpauth://totp/e-Reach:amina.bello?secret=" + key
				+ "&issuer=e-Reach&algorithm=SHA1&digits=6&period=30", uri);
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2, D-013, D-014</p>
	 */
	@Test
	@DisplayName("TC-007-48 | Setup stores the secret encrypted, and the method stays pending")
	void setupStoresTheSecretEncryptedAndPending() throws Exception {
		// Given the setup answer.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");

		// When mfa_method is read.
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT type, secret_encrypted, confirmed_at, last_used_step, created_by
				FROM mfa_method WHERE deleted_at IS NULL
				""");

		// Then there is one pending authenticator, whose stored secret decrypts to the key the user was shown.
		assertEquals(1, rows.size());
		Map<String, Object> row = rows.getFirst();
		assertEquals("totp", row.get("type"));
		assertArrayEquals(AuthenticatorSecretGenerator.decodeBase32(key), secretCipher.decrypt((byte[]) row.get("secret_encrypted")));
		assertNull(row.get("confirmed_at"));
		assertNull(row.get("last_used_step"));
		assertEquals(SystemActors.INSTALLER.uuid(), row.get("created_by"));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-49 | Starting setup again replaces the pending secret")
	void startingSetupAgainReplacesThePendingSecret() throws Exception {
		// Given a pending authenticator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String oldKey = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");

		// When setup starts again.
		String newKey = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");

		// Then there is still one method, with a new key, and the old key's code no longer confirms.
		assertNotEquals(oldKey, newKey);
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mfa_method WHERE deleted_at IS NULL", Long.class));
		confirm(codeFor(oldKey, NOW_STEP))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2, D-024, D-029</p>
	 */
	@Test
	@DisplayName("TC-007-50 | A confirmed authenticator cannot be set up again through the step")
	void confirmedAuthenticatorCannotBeSetUpAgain() throws Exception {
		// Given the operator's confirmed authenticator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");
		confirm(codeFor(key, NOW_STEP)).andExpect(status().isOk());
		byte[] storedBefore = jdbcTemplate.queryForObject("SELECT secret_encrypted FROM mfa_method", byte[].class);

		// When setup starts again.
		startSetup("totp")
				// Then the step is closed, and the stored secret is unchanged.
				.andExpect(status().isNotFound());
		assertArrayEquals(storedBefore, jdbcTemplate.queryForObject("SELECT secret_encrypted FROM mfa_method", byte[].class));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-013, D-014, D-024</p>
	 */
	@Test
	@DisplayName("TC-007-51 | The first correct code confirms the authenticator and finishes the step")
	void firstCorrectCodeFinishesTheStep() throws Exception {
		// Given a pending authenticator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");

		// When the current code is confirmed.
		confirm(codeFor(key, NOW_STEP))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.method").value("totp"))
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		// Then the method records the moment and the step, so the same code can never be used again.
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT last_used_step FROM mfa_method");
		assertEquals(NOW_STEP, ((Number) row.get("last_used_step")).longValue());
		assertEquals(NOW, jdbcTemplate.queryForObject("SELECT confirmed_at FROM mfa_method", OffsetDateTime.class).toInstant());
		assertEquals(NOW, jdbcTemplate.queryForObject("SELECT last_used_at FROM mfa_method", OffsetDateTime.class).toInstant());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-014</p>
	 */
	@Test
	@DisplayName("TC-007-52 | A wrong code leaves the authenticator pending and the step open")
	void wrongCodeLeavesSetupPending() throws Exception {
		// Given a pending authenticator, and a six-digit code that no step in the drift window produces.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");
		List<String> window = List.of(codeFor(key, NOW_STEP - 1), codeFor(key, NOW_STEP), codeFor(key, NOW_STEP + 1));
		String wrongCode = "000000";
		for (int candidate = 1; window.contains(wrongCode); candidate++) {
			wrongCode = String.format("%06d", candidate);
		}

		// When it is confirmed.
		confirm(wrongCode)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));

		// Then nothing is confirmed, and setup can still be started.
		assertNull(jdbcTemplate.queryForObject("SELECT confirmed_at FROM mfa_method", OffsetDateTime.class));
		assertNull(jdbcTemplate.queryForObject("SELECT last_used_step FROM mfa_method", Long.class));
		startSetup("totp").andExpect(status().isOk());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-014 (one step of drift either way)</p>
	 */
	@Test
	@DisplayName("TC-007-53 | Confirmation accepts the previous step's code but not one two steps old")
	void confirmationAllowsOneStepOfDrift() throws Exception {
		// Given a pending authenticator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");

		// When the code from two steps ago is confirmed, then the code from the previous step.
		confirm(codeFor(key, NOW_STEP - 2))
				// Then the first is refused and the second confirms.
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
		confirm(codeFor(key, NOW_STEP - 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: PRD section 10</p>
	 */
	@Test
	@DisplayName("TC-007-55 | Confirming a code before any setup is a 404")
	void confirmingWithNoSetupIsNotFound() throws Exception {
		// Given the operator has been created, with no MFA method.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// When a code is confirmed.
		confirm("123456")
				// Then there is nothing to confirm.
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-026</p>
	 */
	@Test
	@DisplayName("TC-007-56 | Every MFA method can be asked for; the unbuilt ones answer 501, an unknown one 400")
	void everyMethodCanBeAskedFor() throws Exception {
		// Given the operator has been created.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());

		// When setup starts with email, with passkey, and with sms.
		// Then email and passkey are not available yet, and sms is not a method at all.
		startSetup("email")
				.andExpect(status().isNotImplemented())
				.andExpect(jsonPath("$.code").value("MFA_METHOD_NOT_AVAILABLE"));
		startSetup("passkey")
				.andExpect(status().isNotImplemented())
				.andExpect(jsonPath("$.code").value("MFA_METHOD_NOT_AVAILABLE"));
		startSetup("sms")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MFA_METHOD_NOT_SUPPORTED"));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2 acceptance criterion ("no password, PIN or TOTP secret appears in logs"), D-029</p>
	 */
	@Test
	@DisplayName("TC-007-57 | Nothing secret reaches the logs during the whole step")
	void nothingSecretReachesTheLogs(CapturedOutput output) throws Exception {
		// Given log output captured from here on, and a PIN unlikely to appear in a log by chance.
		String pin = "830197";

		// When the whole step runs, from the account to the confirmed authenticator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, pin)).andExpect(status().isCreated());
		ResultActions setup = startSetup("totp").andExpect(status().isOk());
		String key = read(setup, "$.manualEntryKey");
		String image = read(setup, "$.qrCodeImage");
		confirm(codeFor(key, NOW_STEP)).andExpect(status().isOk());

		// Then the captured output holds none of the secrets.
		String logs = output.getAll();
		assertFalse(logs.contains(PASSWORD), "The password reached the logs");
		assertFalse(logs.contains(pin), "The PIN reached the logs");
		assertFalse(logs.contains(SETUP_CODE), "The setup code reached the logs");
		assertFalse(logs.contains(key), "The authenticator key reached the logs");
		assertFalse(logs.contains("otpauth://"), "A provisioning URI reached the logs");
		assertFalse(logs.contains(image.substring(22, 82)), "The QR image reached the logs");
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F2</p>
	 */
	@Test
	@DisplayName("TC-007-58 | The creation answer holds no password, PIN, hash or setup code")
	void creationAnswerHoldsNoSecrets() throws Exception {
		// Given a new server.

		// When the step runs.
		String answer = createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		// Then none of the secrets, nor their hashes, are in it.
		assertFalse(answer.contains(PASSWORD));
		assertFalse(answer.contains("\"" + PIN + "\""));
		assertFalse(answer.contains("$argon2id$"));
		assertFalse(answer.toLowerCase().contains("hash"));
		assertFalse(answer.contains(SETUP_CODE));
	}

	// ------------------------------------------------------------------ part E: the way in

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: F1 acceptance criterion 1, D-029</p>
	 */
	@Test
	@DisplayName("TC-007-59 | Every setup endpoint answers 404 once the first operator is confirmed")
	void everySetupEndpointClosesAfterTheFirstOperator() throws Exception {
		// Given a confirmed operator.
		createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		String key = read(startSetup("totp").andExpect(status().isOk()), "$.manualEntryKey");
		confirm(codeFor(key, NOW_STEP)).andExpect(status().isOk());

		// When each setup endpoint is called with the right setup code.
		// Then each is not found.
		createOperator(operator("Chidi Okafor", "chidi.okafor", "chidi.okafor@primexhospital.ng", PASSWORD, PIN))
				.andExpect(status().isNotFound());
		startSetup("totp").andExpect(status().isNotFound());
		confirm(codeFor(key, NOW_STEP + 1)).andExpect(status().isNotFound());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-029 (setup answers hold secrets)</p>
	 */
	@Test
	@DisplayName("TC-007-60 | Every setup answer says Cache-Control: no-store")
	void setupAnswersAreNeverCached() throws Exception {
		// Given a new server.

		// When the step runs from the account to the confirmation.
		ResultActions created = createOperator(operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN)).andExpect(status().isCreated());
		ResultActions setup = startSetup("totp").andExpect(status().isOk());
		ResultActions confirmed = confirm(codeFor(read(setup, "$.manualEntryKey"), NOW_STEP)).andExpect(status().isOk());

		// Then no answer may be cached.
		for (ResultActions answer : List.of(created, setup, confirmed)) {
			String cacheControl = answer.andReturn().getResponse().getHeader("Cache-Control");
			assertNotNull(cacheControl);
			assertTrue(cacheControl.contains("no-store"), cacheControl);
		}
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: PRD section 10, ADR-019</p>
	 */
	@Test
	@DisplayName("TC-007-61 | A refused step answers with the shared RFC 9457 problem")
	void refusalsKeepTheSharedProblemShape() throws Exception {
		// Given a new server, whose system actor holds the username system.installer.

		// When the step runs with that username.
		createOperator(operator(FULL_NAME, "system.installer", EMAIL, PASSWORD, PIN))
				// Then the answer is one problem with every member.
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://ereach.ng/problems/username-taken"))
				.andExpect(jsonPath("$.title").isNotEmpty())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").isNotEmpty())
				.andExpect(jsonPath("$.instance", startsWith("urn:uuid:")))
				.andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-029</p>
	 */
	@Test
	@DisplayName("TC-007-65 | Without the setup code the step is refused")
	void noSetupCodeNoSetup() throws Exception {
		// Given a new server.

		// When the step runs without X-Setup-Code.
		postAsInstaller(SETUP, operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN), null)
				// Then it is refused, and nothing is saved.
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		assertEquals(0, personAccounts());
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-029</p>
	 */
	@Test
	@DisplayName("TC-007-66 | A wrong setup code is refused without revealing the right one")
	void wrongSetupCodeIsRefused() throws Exception {
		// Given a new server, and a code that is not the installer's.
		String wrongCode = newSetupCode();

		// When the step runs with it.
		String answer = postAsInstaller(SETUP, operator(FULL_NAME, USERNAME, EMAIL, PASSWORD, PIN), wrongCode)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
				.andReturn().getResponse().getContentAsString();

		// Then the right code is not in the answer, and nothing is saved.
		assertFalse(answer.contains(SETUP_CODE));
		assertEquals(0, personAccounts());
	}
}
