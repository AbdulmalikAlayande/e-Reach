package ng.ereach.facilityserver.platform;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.shared.id.FacilityId;
import ng.ereach.shared.id.Ids;
import ng.ereach.shared.id.SystemActors;
import ng.ereach.shared.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthorColumnsTests extends PostgresIntegrationTest {

	@Value("${spring.datasource.url}")
	String databaseUrl;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TransactionTemplate transaction;

	FacilityId primexHospitalId;
	UserId userA;
	UserId userB;

	record AuthorColumns(UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {}

	/** Reads one clinic's author columns. Call it inside a transaction with that clinic's facility context. */
	private AuthorColumns readAuthorColumns(UUID clinicId) {
		return jdbcTemplate.queryForObject("""
			SELECT created_by, updated_by, created_at, updated_at FROM clinic WHERE id = ?
		""", (resultSet, _) -> new AuthorColumns(
				resultSet.getObject("created_by", UUID.class),
				resultSet.getObject("updated_by", UUID.class),
				resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
				resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
		), clinicId);
	}

	@BeforeEach
	void seedDatabase() {
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE role_assignment, facility_membership, user_security_key, facility_setting, clinic, facility, lga, state");
		owner.update("INSERT INTO state (code, name) VALUES ('LA', 'Lagos')");
		owner.update("INSERT INTO lga (code, state_code, name) VALUES ('LA-01', 'LA', 'Agege')");

		primexHospitalId = FacilityId.newId();
		userA = UserId.newId();
		userB = UserId.newId();

		// The facility is written the way the installer will write it: as INSTALLER, with no author columns in the SQL.
		FacilityContext.runWithContext(FacilityContext.forUserOnly(SystemActors.INSTALLER),
				() -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
					INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix)
					VALUES (?, 'Primex Hospital', 'secondary', 'LA', 'LA-01', 'PH')
				""", primexHospitalId.uuid())));
	}

	@Test
	@DisplayName("TC-006-56 | With no user in the context, inserting a facility is refused with 23502")
	void tc_006_56_noAuthorNoWrite() {
		// Given no user in the context (no FacilityContext at all).
		// When a transaction inserts a facility without author columns.
		DataAccessException exception = assertThrows(DataAccessException.class,
				() -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
					INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix)
					VALUES (?, 'Novia Hospital', 'secondary', 'LA', 'LA-01', 'NH')
				""", FacilityId.newId().uuid())));

		// Then the database refuses it because created_by has no value.
		SQLException sqlException = (SQLException) exception.getMostSpecificCause();
		assertEquals("23502", sqlException.getSQLState());
		assertTrue(sqlException.getMessage().contains("created_by"), sqlException.getMessage());
	}

	@Test
	@DisplayName("TC-006-57 | With user A in the context, an inserted clinic gets A as creator and editor, at one time")
	void tc_006_57_databaseFillsAuthorColumns() {
		// Given user A in facility A's context.
		FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, userA);
		UUID clinicId = Ids.newId();

		// When a transaction inserts a clinic without author columns.
		FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
			INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'ORTHO', 'Orthopaedic Clinic')
		""", clinicId, primexHospitalId.uuid())));

		// Then created_by and updated_by are A, and created_at equals updated_at.
		AuthorColumns columns = FacilityContext.callWithContext(contextA, () -> transaction.execute(_ -> readAuthorColumns(clinicId)));
		assertEquals(userA.uuid(), columns.createdBy());
		assertEquals(userA.uuid(), columns.updatedBy());
		assertEquals(columns.createdAt(), columns.updatedAt());
	}

	@Test
	@DisplayName("TC-006-58 | When user B updates A's clinic, updated_by is B and updated_at is later, while created_by stays A")
	void tc_006_58_everyUpdateRecordsItsAuthor() {
		// Given a clinic created by A.
		FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, userA);
		FacilityContext contextB = FacilityContext.forFacility(primexHospitalId, userB);
		UUID clinicId = Ids.newId();
		FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
			INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'ORTHO', 'Orthopaedic Clinic')
		""", clinicId, primexHospitalId.uuid())));
		AuthorColumns before = FacilityContext.callWithContext(contextA, () -> transaction.execute(_ -> readAuthorColumns(clinicId)));

		// When user B renames it in a later transaction.
		FacilityContext.runWithContext(contextB, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
			UPDATE clinic SET name = 'Orthopaedics and Trauma Clinic' WHERE id = ?
		""", clinicId)));

		// Then updated_by is B and updated_at is later, while created_by and created_at are unchanged.
		AuthorColumns after = FacilityContext.callWithContext(contextB, () -> transaction.execute(_ -> readAuthorColumns(clinicId)));
		assertEquals(userB.uuid(), after.updatedBy());
		assertTrue(after.updatedAt().isAfter(before.updatedAt()), before.updatedAt() + " -> " + after.updatedAt());
		assertEquals(userA.uuid(), after.createdBy());
		assertEquals(before.createdAt(), after.createdAt());
	}

	@Test
	@DisplayName("TC-006-59 | An update that tries to rewrite created_by and created_at leaves both unchanged")
	void tc_006_59_creatorCannotBeRewritten() {
		// Given a clinic created by A.
		FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, userA);
		FacilityContext contextB = FacilityContext.forFacility(primexHospitalId, userB);
		UUID clinicId = Ids.newId();
		FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
			INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'ORTHO', 'Orthopaedic Clinic')
		""", clinicId, primexHospitalId.uuid())));
		AuthorColumns before = FacilityContext.callWithContext(contextA, () -> transaction.execute(_ -> readAuthorColumns(clinicId)));

		// When user B sets created_by to B and moves created_at a day earlier.
		FacilityContext.runWithContext(contextB, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
			UPDATE clinic SET created_by = ?, created_at = created_at - interval '1 day' WHERE id = ?
		""", userB.uuid(), clinicId)));

		// Then created_by is still A and created_at has not moved.
		AuthorColumns after = FacilityContext.callWithContext(contextB, () -> transaction.execute(_ -> readAuthorColumns(clinicId)));
		assertEquals(userA.uuid(), after.createdBy());
		assertEquals(before.createdAt(), after.createdAt());
	}
}