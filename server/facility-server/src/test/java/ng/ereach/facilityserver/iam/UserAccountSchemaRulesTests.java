package ng.ereach.facilityserver.iam;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.facilityserver.platform.FacilityContext;
import ng.ereach.shared.id.FacilityId;
import ng.ereach.shared.id.Ids;
import ng.ereach.shared.id.SystemActors;
import ng.ereach.shared.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Part C of ER-007, part 1: what V3 itself guarantees about the platform operator's account, whatever
 * the Java code does. Every write runs as ereach_app inside the installer's context (D-008), unless the
 * case says otherwise.
 */
@SpringBootTest
class UserAccountSchemaRulesTests extends PostgresIntegrationTest {

	/** A syntactically plausible Argon2id hash; these cases test the table, not the hashing. */
	private static final String PASSWORD_HASH = "$argon2id$v=19$m=16384,t=2,p=1$c2FsdHNhbHRzYWx0$aGFzaGhhc2hoYXNoaGFzaA";
	private static final String PIN_HASH = "$argon2id$v=19$m=16384,t=2,p=1$cGlucGlucGlucGlu$cGluaGFzaHBpbmhhc2g";

	@Value("${spring.datasource.url}")
	String databaseUrl;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TransactionTemplate transaction;

	@BeforeEach
	void emptyTheIdentityTables() {
		// As the table owner: empty everything that refers to a person or a facility, then remove every
		// person account, so only the four system actors remain (D-017; user_account is never truncated).
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE mfa_challenge, mfa_method, practitioner_profile, role_assignment, facility_membership, "
				+ "user_security_key, facility_setting, clinic, facility, lga, state");
		owner.update("DELETE FROM user_account WHERE kind = 'person'");
		owner.update("INSERT INTO state (code, name) VALUES ('LA', 'Lagos')");
		owner.update("INSERT INTO lga (code, state_code, name) VALUES ('LA-01', 'LA', 'Agege')");
	}

	private static SQLException sqlCause(DataAccessException exception) {
		return (SQLException) exception.getMostSpecificCause();
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: D-008, D-009, ADR-018</p>
	 */
	@Test
	@DisplayName("TC-007-25 | An operator account is a person, active, flagged as operator, and authored by the installer")
	void operatorAccountIsAPersonAuthoredByTheInstaller() {
		// Given the installer's context.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		UUID operatorId = UserId.newId().uuid();

		// When a person flagged as platform operator is inserted, without author columns.
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO user_account (id, kind, username, full_name, email, password_hash, pin_hash,
				                          must_change_password, is_platform_operator, mfa_required_by)
				VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?, ?, false, true, now())
				""", operatorId, PASSWORD_HASH, PIN_HASH)));

		// Then it is stored as an active person who is an operator, written by the INSTALLER.
		Map<String, Object> row = jdbcTemplate.queryForMap("""
				SELECT kind, status, is_platform_operator, created_by, updated_by FROM user_account WHERE id = ?
				""", operatorId);
		assertEquals("person", row.get("kind"));
		assertEquals("active", row.get("status"));
		assertEquals(true, row.get("is_platform_operator"));
		assertEquals(SystemActors.INSTALLER.uuid(), row.get("created_by"));
		assertEquals(SystemActors.INSTALLER.uuid(), row.get("updated_by"));
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: F2, V3 (ck_user_account_password)</p>
	 */
	@Test
	@DisplayName("TC-007-26 | A person account without a password hash is refused")
	void personMustHaveAPassword() {
		// Given the installer's context.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);

		// When a person with no password hash is inserted.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, pin_hash)
						VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
						""", UserId.newId().uuid(), PIN_HASH))));

		// Then the password check refuses it.
		assertEquals("23514", sqlCause(exception).getSQLState());
		assertTrue(sqlCause(exception).getMessage().contains("ck_user_account_password"), sqlCause(exception).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: F2, D-021 (usernames are stored as typed), V3</p>
	 */
	@Test
	@DisplayName("TC-007-27 | Usernames are 3 to 40 letters of either case, digits, dots, underscores or hyphens, stored as typed")
	void usernamesFollowTheAllowedPattern() {
		// Given the installer's context, and usernames with too few characters, too many, a space and a "!".
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		String[] refused = {"ab", "a".repeat(41), "amina bello", "amina!"};
		UUID typedWithCapitalsId = UserId.newId().uuid();

		// When a person is inserted with each, then one named Amina.Bello.
		// Then each of the four is refused by the username check.
		assertAll(Arrays.stream(refused).map(username -> (Executable) () -> {
			DataAccessException exception = assertThrows(DataAccessException.class,
					() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
							INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
							VALUES (?, 'person', ?, 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
							""", UserId.newId().uuid(), username, PASSWORD_HASH))),
					"\"" + username + "\" must be refused");
			assertEquals("23514", sqlCause(exception).getSQLState(), username);
		}));

		// And Amina.Bello is stored exactly as typed.
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
				VALUES (?, 'person', 'Amina.Bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
				""", typedWithCapitalsId, PASSWORD_HASH)));
		assertEquals("Amina.Bello",
				jdbcTemplate.queryForObject("SELECT username FROM user_account WHERE id = ?", String.class, typedWithCapitalsId));
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: D-021 (unique ignoring case), V3 (ux_user_account_username on lower(username))</p>
	 */
	@Test
	@DisplayName("TC-007-62 | Two usernames cannot differ only by case")
	void usernamesCannotDifferOnlyByCase() {
		// Given a person named Amina.Bello.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
				VALUES (?, 'person', 'Amina.Bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
				""", UserId.newId().uuid(), PASSWORD_HASH)));

		// When a person named amina.bello is inserted.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
						VALUES (?, 'person', 'amina.bello', 'Another Amina', 'another.amina@primexhospital.ng', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH))));

		// Then the case-insensitive unique index refuses it.
		assertEquals("23505", sqlCause(exception).getSQLState());
		assertTrue(sqlCause(exception).getMessage().contains("ux_user_account_username"), sqlCause(exception).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: F2 (usernames are unique across the platform), V3 (ux_user_account_username)</p>
	 */
	@Test
	@DisplayName("TC-007-28 | A username already used by a live account is refused")
	void usernameIsUsedOnce() {
		// Given the installer's context; the system actor system.installer already exists.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);

		// When a person with the username system.installer is inserted.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
						VALUES (?, 'person', 'system.installer', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH))));

		// Then the unique username index refuses it.
		assertEquals("23505", sqlCause(exception).getSQLState());
		assertTrue(sqlCause(exception).getMessage().contains("ux_user_account_username"), sqlCause(exception).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: V3 (phone in E.164 form)</p>
	 */
	@Test
	@DisplayName("TC-007-29 | A phone number must be in international form")
	void phoneIsInInternationalForm() {
		// Given the installer's context.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		UUID storedId = UserId.newId().uuid();

		// When a person with a local number, then one with an international number, is inserted.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, phone, password_hash)
						VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', '08031234567', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH))));
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO user_account (id, kind, username, full_name, email, phone, password_hash)
				VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', '+2348031234567', ?)
				""", storedId, PASSWORD_HASH)));

		// Then the local number is refused and the international one is stored.
		assertEquals("23514", sqlCause(exception).getSQLState());
		assertEquals("+2348031234567",
				jdbcTemplate.queryForObject("SELECT phone FROM user_account WHERE id = ?", String.class, storedId));
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: F2 (system accounts can never be platform operators), D-009</p>
	 */
	@Test
	@DisplayName("TC-007-30 | A system account can never be a platform operator")
	void systemAccountIsNeverAnOperator() {
		// Given the installer's context.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);

		// When a system account flagged as operator is inserted, and the INSTALLER account is made an operator.
		DataAccessException inserted = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, must_change_password, is_platform_operator)
						VALUES (?, 'system', 'system.extra', 'Extra job', false, true)
						""", UserId.newId().uuid()))));
		DataAccessException updated = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update(
						"UPDATE user_account SET is_platform_operator = true WHERE id = ?", SystemActors.INSTALLER.uuid()))));

		// Then the system-secrets check refuses both.
		assertEquals("23514", sqlCause(inserted).getSQLState());
		assertTrue(sqlCause(inserted).getMessage().contains("ck_user_account_system_secrets"), sqlCause(inserted).getMessage());
		assertEquals("23514", sqlCause(updated).getSQLState());
		assertTrue(sqlCause(updated).getMessage().contains("ck_user_account_system_secrets"), sqlCause(updated).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: PRD section 8 rule 19, ADR-018</p>
	 */
	@Test
	@DisplayName("TC-007-31 | With no user in the context, creating an account is refused with 23502")
	void noAuthorNoAccount() {
		// Given no context at all.

		// When a person is inserted without author columns.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
						VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH)));

		// Then the database refuses it because created_by has no value.
		assertEquals("23502", sqlCause(exception).getSQLState());
		assertTrue(sqlCause(exception).getMessage().contains("created_by"), sqlCause(exception).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: D-009, ADR-021, PRD section 8 rule 1</p>
	 */
	@Test
	@DisplayName("TC-007-32 | An operator with no membership sees no facility's clinics, memberships or roles")
	void operatorSeesNoFacilityData() {
		// Given a facility with a clinic, another person's membership and doctor role there, and an operator.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		FacilityId facilityId = FacilityId.newId();
		FacilityContext inFacility = FacilityContext.forFacility(facilityId, SystemActors.INSTALLER);
		UUID doctorId = UserId.newId().uuid();
		UUID membershipId = Ids.newId();
		UserId operatorId = UserId.newId();
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> {
			jdbcTemplate.update("""
					INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix)
					VALUES (?, 'Primex Hospital', 'secondary', 'LA', 'LA-01', 'PH')
					""", facilityId.uuid());
			jdbcTemplate.update("""
					INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
					VALUES (?, 'person', 'chidi.okafor', 'Chidi Okafor', 'chidi.okafor@primexhospital.ng', ?)
					""", doctorId, PASSWORD_HASH);
			jdbcTemplate.update("""
					INSERT INTO user_account (id, kind, username, full_name, email, password_hash, is_platform_operator)
					VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?, true)
					""", operatorId.uuid(), PASSWORD_HASH);
		}));
		FacilityContext.runWithContext(inFacility, () -> transaction.executeWithoutResult(_ -> {
			jdbcTemplate.update("INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'GOPD', 'General Outpatients')",
					Ids.newId(), facilityId.uuid());
			jdbcTemplate.update("""
					INSERT INTO facility_membership (id, facility_id, user_account_id, valid_from)
					VALUES (?, ?, ?, current_date)
					""", membershipId, facilityId.uuid(), doctorId);
			jdbcTemplate.update("INSERT INTO role_assignment (id, facility_id, membership_id, role_code) VALUES (?, ?, ?, 'doctor')",
					Ids.newId(), facilityId.uuid(), membershipId);
		}));

		// When the operator's context, a user with no facility, counts the facility tables.
		long[] counts = FacilityContext.callWithContext(FacilityContext.forUserOnly(operatorId), () -> transaction.execute(_ -> new long[]{
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clinic", Long.class),
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM facility_membership", Long.class),
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_assignment", Long.class)}));

		// Then the operator sees none of them, while the facility itself does hold one clinic.
		assertArrayEquals(new long[]{0, 0, 0}, counts);
		Long clinicsInFacility = FacilityContext.callWithContext(inFacility,
				() -> transaction.execute(_ -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clinic", Long.class)));
		assertEquals(1L, clinicsInFacility);
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: D-009 (platform_operator leaves the role list)</p>
	 */
	@Test
	@DisplayName("TC-007-33 | platform_operator can no longer be given as a facility role")
	void platformOperatorIsNotAFacilityRole() {
		// Given a facility and a person's membership in it.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		FacilityId facilityId = FacilityId.newId();
		FacilityContext inFacility = FacilityContext.forFacility(facilityId, SystemActors.INSTALLER);
		UUID personId = UserId.newId().uuid();
		UUID membershipId = Ids.newId();
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> {
			jdbcTemplate.update("""
					INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix)
					VALUES (?, 'Primex Hospital', 'secondary', 'LA', 'LA-01', 'PH')
					""", facilityId.uuid());
			jdbcTemplate.update("""
					INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
					VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
					""", personId, PASSWORD_HASH);
		}));
		FacilityContext.runWithContext(inFacility, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO facility_membership (id, facility_id, user_account_id, valid_from) VALUES (?, ?, ?, current_date)
				""", membershipId, facilityId.uuid(), personId)));

		// When, in that facility's context so row-level security allows the row, the role platform_operator is assigned.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(inFacility, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update(
						"INSERT INTO role_assignment (id, facility_id, membership_id, role_code) VALUES (?, ?, ?, 'platform_operator')",
						Ids.newId(), facilityId.uuid(), membershipId))));

		// Then the role check refuses it.
		assertEquals("23514", sqlCause(exception).getSQLState());
		assertTrue(sqlCause(exception).getMessage().contains("role_code"), sqlCause(exception).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: D-022 (every person has an email), V3 (ck_user_account_person_email)</p>
	 */
	@Test
	@DisplayName("TC-007-63 | A person account without an email is refused")
	void personMustHaveAnEmail() {
		// Given the installer's context.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);

		// When a person with a password but no email is inserted.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, password_hash)
						VALUES (?, 'person', 'amina.bello', 'Amina Bello', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH))));

		// Then the email check refuses it.
		assertEquals("23514", sqlCause(exception).getSQLState());
		assertTrue(sqlCause(exception).getMessage().contains("ck_user_account_person_email"), sqlCause(exception).getMessage());
	}

	/**
	 * <p>Level: Integration</p>
	 * <p>Traces to: D-022, V3 (email in lower case, ux_user_account_email)</p>
	 */
	@Test
	@DisplayName("TC-007-64 | An email is stored in lower case and belongs to one live account")
	void emailIsLowerCaseAndUsedOnce() {
		// Given the installer's context and a person with amina.bello@primexhospital.ng.
		FacilityContext installer = FacilityContext.forUserOnly(SystemActors.INSTALLER);
		FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
				VALUES (?, 'person', 'amina.bello', 'Amina Bello', 'amina.bello@primexhospital.ng', ?)
				""", UserId.newId().uuid(), PASSWORD_HASH)));

		// When a person with Chidi.Okafor@PrimexHospital.ng, then another with Amina's address, is inserted.
		DataAccessException capitals = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
						VALUES (?, 'person', 'chidi.okafor', 'Chidi Okafor', 'Chidi.Okafor@PrimexHospital.ng', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH))));
		DataAccessException reused = assertThrows(DataAccessException.class,
				() -> FacilityContext.runWithContext(installer, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
						INSERT INTO user_account (id, kind, username, full_name, email, password_hash)
						VALUES (?, 'person', 'chidi.okafor', 'Chidi Okafor', 'amina.bello@primexhospital.ng', ?)
						""", UserId.newId().uuid(), PASSWORD_HASH))));

		// Then the capitals are refused by the email check, and the reused address by the unique email index.
		assertEquals("23514", sqlCause(capitals).getSQLState());
		assertEquals("23505", sqlCause(reused).getSQLState());
		assertTrue(sqlCause(reused).getMessage().contains("ux_user_account_email"), sqlCause(reused).getMessage());
	}
}
