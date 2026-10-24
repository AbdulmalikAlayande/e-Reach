package ng.ereach.facilityserver.platform;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.shared.id.FacilityId;
import ng.ereach.shared.id.Ids;
import ng.ereach.shared.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TransactionContextHookTest extends PostgresIntegrationTest {
	
	FacilityId primexHospitalId;
	FacilityId noviaHospitalId;
	UserId currentUserId;
	
	@Value("${spring.datasource.url}")
	String databaseUrl;
	
	@Autowired
	TransactionTemplate transaction;
	
	JdbcTemplate databaseTableOwner;
	
	@Autowired
	JdbcTemplate jdbcTemplate;
	
	
	@BeforeEach
	public void configureDatabaseAccessAndSeedDatabase() {
		databaseTableOwner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		
		databaseTableOwner.execute("TRUNCATE facility_setting, clinic, facility, lga, state");
		databaseTableOwner.update("INSERT INTO state (code, name) VALUES ('LA', 'Lagos'), ('OG', 'Ogun')");
		databaseTableOwner.update("""
			INSERT INTO lga (code, state_code, name)
			VALUES
			 	('LA-ALM', 'LA', 'Alimosho'),
			    ('OG-ODT', 'OG', 'Ado-Odo Ota'),
			    ('LA-YAB', 'LA', 'Yaba'),
			    ('OG-YWN', 'OG', 'Yewa North')
		""");
		
		currentUserId = UserId.newId();
		noviaHospitalId = new FacilityId(Ids.newId());
		primexHospitalId = new FacilityId(Ids.newId());
		
		jdbcTemplate.update("""
			INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix, nhfr_code)
			VALUES
			    (?, 'Primex Hospital', 'secondary', 'LA', 'LA-ALM', 'AH', 'NG-01515'),
			    (?, 'Novia Hospital', 'secondary', 'OG', 'OG-YWN', 'FT', 'NG-97762')
		""", primexHospitalId.uuid(), noviaHospitalId.uuid());
		
		seedDataIntoClinicTable(primexHospitalId.uuid(), List.of("ORTHO/OOPD", "CARD"),
				List.of("Orthopedic Outpatient Department", "Primex Cardiology Clinic"));
		
		seedDataIntoClinicTable(noviaHospitalId.uuid(), List.of("PSYCH/MH", "DENT"),
				List.of("Novia Psychiatry/Mental Health Clinic", "Novia Dental Clinic"));
	}
	
	private void seedDataIntoClinicTable(UUID facilityId, List<String> codes, List<String> names) {
		FacilityContext setupContext = FacilityContext.forFacility(new FacilityId(facilityId), currentUserId);
		FacilityContext.runWithContext(setupContext, ()->transaction.executeWithoutResult(_ -> {
			jdbcTemplate.execute("SELECT set_config('app.facility_id', '" + facilityId + "',true)");
			
			jdbcTemplate.update("""
						INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, ?, ?)
					""", Ids.newId(), facilityId, codes.getFirst(), names.getFirst());
			jdbcTemplate.update("""
						INSERT INTO clinic (id, facility_id, code, name) VALUES  (?, ?, ?, ?)
					""", Ids.newId(), facilityId, codes.get(1), names.get(1));
		}));
	}
	
	@Nested
	@DisplayName("")
	class ReadingWithAContext {
		
		@Test
		@DisplayName("TC-006-29 | Reading from the database with a transaction context should succeed with only the expected results")
		void __() {
			record ClinicRow(UUID facilityId, String code, String name) {
			}
			//Given the context is Primex Hospital (Facility A).
			FacilityContext context = FacilityContext.forFacility(primexHospitalId, currentUserId);
			
			// When a transaction reads all clinics.
			FacilityContext.runWithContext(context, () -> transaction.executeWithoutResult(_ -> {
				String sqlQuery = """
					SELECT facility_id, code, name
					FROM clinic
					ORDER BY code
				""";
				List<ClinicRow> clinics = jdbcTemplate.query(sqlQuery, (resultSet, _) -> new ClinicRow(
					resultSet.getObject("facility_id", UUID.class),
					resultSet.getString("code"),
					resultSet.getString("name")
				));
				
				// Then only the clinics related to that facility is returned.
				assertEquals(List.of(
					new ClinicRow(primexHospitalId.uuid(), "CARD", "Primex Cardiology Clinic"),
					new ClinicRow(primexHospitalId.uuid(), "ORTHO/OOPD", "Orthopedic Outpatient Department")
				), clinics);
			}));
		}
		
		@Test
		@DisplayName("TC-006-30")
		void ___() {
			record ClinicRow(UUID facilityId, String code, String name) {
			}
			// Given the context is for Novia Hospital (Facility B).
			FacilityContext context = FacilityContext.forFacility(noviaHospitalId, currentUserId);
			
			// When a transaction reads all clinics.
			FacilityContext.runWithContext(context, () -> transaction.executeWithoutResult(_ -> {
				String sqlQuery = """
					SELECT facility_id, code, name
					FROM clinic
					ORDER BY code
				""";
				List<ClinicRow> clinics = jdbcTemplate.query(sqlQuery, (resultSet, _) -> new ClinicRow(
						resultSet.getObject("facility_id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name")
				));
				
				// Then only the clinics related to that facility is returned.
				assertEquals(List.of(
					new ClinicRow(noviaHospitalId.uuid(), "DENT", "Novia Dental Clinic"),
					new ClinicRow(noviaHospitalId.uuid(), "PSYCH/MH", "Novia Psychiatry/Mental Health Clinic")
				), clinics);
			}));
		}
		
		//. TC-006-32.
		@Test
		@DisplayName("TC-006-31 | The transaction can read the active facility context parameter directly from current_setting")
		void ____() {
			// Given the context is for Primex Hospital (Facility A).
			FacilityContext facilityContext = FacilityContext.forFacility(primexHospitalId, currentUserId);
			
			FacilityContext.runWithContext(facilityContext, () -> transaction.executeWithoutResult(_ -> {
				// When the transaction reads current_setting('app.facility_id')
				String actualFacilityId = jdbcTemplate.queryForObject("SELECT current_setting('app.facility_id')", String.class);
				
				// Then it equals facility A's ID as text.
				assertEquals(primexHospitalId.uuid().toString(), actualFacilityId);
			}));
		}
		
		@Test
		@DisplayName("TC-006-32 | The transaction can read the active facility user context parameter directly from current_setting")
		void _____() {
			// Given the context has user U,
			FacilityContext facilityContext = FacilityContext.forFacility(noviaHospitalId, currentUserId);
			
			FacilityContext.runWithContext(facilityContext, () -> transaction.executeWithoutResult(_ -> {
				// When the transaction reads current_setting('app.user_id')
				String actualUserId = jdbcTemplate.queryForObject("SELECT current_setting('app.user_id')", String.class);
				
				// Then it equals U's ID as text.
				assertEquals(currentUserId.toString(), actualUserId);
			}));
		}
	}
	
	@Nested
	@DisplayName("Missing and Partial Context Behavior Policies")
	class MissingContextTests {
		
		@Test
		@DisplayName("TC-006-33")
		void ______(){
			// Given no context at all.
			
			record ClinicRow(UUID facilityId, String code, String name) {}
			transaction.executeWithoutResult(_ -> {
				
				// When a transaction reads all clinics.
				String sqlQuery = """
					SELECT facility_id, code, name
					FROM clinic
					ORDER BY code
				""";
				List<ClinicRow> clinicRows = jdbcTemplate.query(sqlQuery, (resultSet, _)-> new ClinicRow(
					resultSet.getObject("facility_id", UUID.class),
					resultSet.getString("code"),
					resultSet.getString("name"))
				);
				
				// Then zero rows come back and no error is thrown.
				assertEquals(0, clinicRows.size());
			});

		}
		
		@Test
		@DisplayName("TC-006-34")
		void _______(){
			// Given no context at all.
			
			// When the transaction reads current_setting('app.facility_id', true).
			String actualFacilityId = jdbcTemplate.queryForObject("SELECT current_setting('app.facility_id', true)", String.class);
			boolean isUnsetOrBlank = actualFacilityId == null || actualFacilityId.isBlank();
			
			// Then it is empty or null.
			assertTrue(isUnsetOrBlank, "Expected session context parameter to be blank or null");
		}
		
		@Test
		@DisplayName("TC-006-35")
		void ________(){
			record ClinicRow(UUID facilityId, String code, String name) {}
				
				// Given only user U and no facility (the sign-in case),
			FacilityContext partialContext = FacilityContext.forUserOnly(currentUserId);
			
			FacilityContext.runWithContext(partialContext, () -> transaction.executeWithoutResult(_ -> {
				// When a transaction runs, read current settings configuration
				String actualUserId = jdbcTemplate.queryForObject("SELECT current_setting('app.user_id', true)", String.class);
				String actualFacilityId = jdbcTemplate.queryForObject("SELECT current_setting('app.facility_id', true)", String.class);
				
				List<ClinicRow> clinicRows = jdbcTemplate.query("SELECT facility_id, code, name FROM clinic", (resultSet, _) -> new ClinicRow(
						resultSet.getObject("facility_id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name")
				));
				
				// Then app.user_id equals U, app.facility_id is empty, and reading clinics returns zero rows.
				assertEquals(currentUserId.uuid().toString(), actualUserId);
				assertEquals("", actualFacilityId);
				assertEquals(0, clinicRows.size());
			}));
		}
		
		@Test
		@DisplayName("TC-006-36")
		void _________(){
			// Given no context at all.
			// When a transaction inserts a clinic stamped with Primex Hospital (facility A)
			
			// Note: A big note actually, this shit passes because of what I commented out below, I think that is one of
			//  the main reason why we have facility contexts and custom transaction manager, to set the context before
			//  a transaction runs. So, uncommenting the set_config query below will fail the test with
			//  "Expected DataAccessException to be thrown, but nothing was thrown."
			DataAccessException exception = assertThrows(DataAccessException.class, () -> transaction.executeWithoutResult(_-> {
				// jdbcTemplate.execute("SELECT set_config('app.facility_id', '" + primexHospitalId.uuid() + "',true)");

				jdbcTemplate.update("""
						INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, ?, ?)
					""", Ids.newId(), primexHospitalId.uuid(), "GOPD", "GOPD Primex");
				
				jdbcTemplate.update("""
						INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, ?, ?)
					""", Ids.newId(), primexHospitalId.uuid(), "ANC", "ANC Primex");
			}));
			
			// Then the insert fails with SQLState 42501
			SQLException sqlException = (SQLException) exception.getMostSpecificCause();
			assertEquals("42501", sqlException.getSQLState());
			
		}
	}
	
	@Nested
	@DisplayName("Writing, Updating, and Deleting With An Active Context Policies")
	class WritingWithAContextTests {
		
		@Test
		@DisplayName("TC-006-37 | Given the context is facility A, inserting a new clinic for A succeeds and is retrievable")
		void __________() {
			FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, currentUserId);
			UUID newClinicId = Ids.newId();
			
			// 1. When a transaction inserts a new clinic for A
			FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, ?, ?)
			""", newClinicId, primexHospitalId.uuid(), "PAED", "Pediatrics Clinic")));
			
			// 2. Then a later read in facility A finds it
			FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> {
				Integer count = jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM clinic WHERE id = ?", Integer.class, newClinicId
				);
				assertEquals(1, count, "The newly inserted clinic should be visible under Facility A's context");
			}));
		}
		
		@Test
		@DisplayName("TC-006-38 | Given the context is facility A, inserting a clinic stamped with facility B fails with 42501")
		void ___________() {
			FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, currentUserId);
			UUID newClinicId = Ids.newId();
			
			// When a transaction inserts a clinic stamped with facility B
			DataAccessException exception = assertThrows(DataAccessException.class, () -> FacilityContext.runWithContext(
				contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
					INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, ?, ?)
				""", newClinicId, noviaHospitalId.uuid(), "PAED", "Malicious Cross Facility Insert"))
			));
			
			// Then it fails with SQLState 42501 (Insufficient Privilege due to WITH CHECK policy)
			java.sql.SQLException sqlException = (java.sql.SQLException) exception.getMostSpecificCause();
			assertEquals("42501", sqlException.getSQLState(), "Expected an RLS check violation (42501)");
		}
		
		@Test
		@DisplayName("TC-006-39 | Given the context is facility A, updating an existing clinic's facility_id to B fails with 42501")
		void ____________() {
			FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, currentUserId);
			
			// When a transaction changes an existing Facility A clinic's facility_id to B
			DataAccessException exception = assertThrows(DataAccessException.class, () -> FacilityContext.runWithContext(
				contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
					UPDATE clinic
					SET facility_id = ?
					WHERE facility_id = ? AND code = ?
				""", noviaHospitalId.uuid(), primexHospitalId.uuid(), "ORTHO/OOPD")))
			);
			
			// Then it fails with SQLState 42501 (WITH CHECK constraint prevents mutating the tenant discriminator column)
			java.sql.SQLException sqlException = (java.sql.SQLException) exception.getMostSpecificCause();
			assertEquals("42501", sqlException.getSQLState(), "Expected an RLS policy mutation check violation (42501)");
		}
		
		@Test
		@DisplayName("TC-006-40 | Given context A, updates to B affect 0 rows, and hard deletes are explicitly refused with a 42501 permission error")
		void _____________() {
			FacilityContext contextA = FacilityContext.forFacility(primexHospitalId, currentUserId);
			FacilityContext contextB = FacilityContext.forFacility(noviaHospitalId, currentUserId);
			
			// 1. Verify that updates to Facility B's records under Context A quietly affects 0 rows due to RLS
			FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> {
				int rowsUpdated = jdbcTemplate.update("""
					UPDATE clinic SET name = 'Defaced Name' WHERE facility_id = ? AND code = ?
				""", noviaHospitalId.uuid(), "PSYCH/MH");
				
				assertEquals(0, rowsUpdated, "Update must affect 0 rows because RLS completely masks the foreign tenant's record.");
			}));
			
			// 2. Verify that hard deletes are fundamentally refused by the database schema permissions
			org.springframework.dao.DataAccessException exception = assertThrows(
					org.springframework.dao.DataAccessException.class,
					() -> FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
					DELETE FROM clinic WHERE facility_id = ? AND code = ?
				""", noviaHospitalId.uuid(), "PSYCH/MH")))
			);
			
			// Assert that the delete failure is due to explicit missing DML permissions (SQLState 42501)
			java.sql.SQLException sqlException = (java.sql.SQLException) exception.getMostSpecificCause();
			assertEquals("42501", sqlException.getSQLState(), "Expected hard delete to be blocked with permission denied code 42501.");
			
			// 3. Verify that the row remains completely un-mutated under Facility B's view
			FacilityContext.runWithContext(contextB, () -> transaction.executeWithoutResult(_ -> {
				String currentName = jdbcTemplate.queryForObject("""
					SELECT name FROM clinic WHERE facility_id = ? AND code = ?
				""", String.class, noviaHospitalId.uuid(), "PSYCH/MH");
				
				assertEquals("Novia Psychiatry/Mental Health Clinic", currentName, "The record must remain pristine and unchanged.");
			}));
		}
	}
	
	@Nested
	@DisplayName("Tests Against Leaks Between Transactions")
	class TestsAgainstLeaksBetweenTransactions {
		
		@Test
		@DisplayName("TC-006-41 | Given a transaction with facility A has committed, when the next transaction runs with no context, then session parameters are cleared and clinics return zero rows")
		void __(){
			// Given a transaction with Primex Hospital (facility A) has committed.
			FacilityContext contextA = new FacilityContext(primexHospitalId, currentUserId);
			FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> {
				// Run a query to ensure context is pushed to the connection
				jdbcTemplate.execute("SELECT 1");
			}));
			
			// When the next transaction runs with no context.
			transaction.executeWithoutResult(_ -> {
				String actualFacilityId = jdbcTemplate.queryForObject("SELECT current_setting('app.facility_id', true)", String.class);
				Integer visibleClinicsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clinic", Integer.class);
				
				// Then app.facility_id is empty/null and clinics return zero rows.
				assertTrue(actualFacilityId == null || actualFacilityId.isBlank(), "The facility context should not leak into the next transaction");
				assertEquals(0, visibleClinicsCount, "No rows should be returned from an unbound transaction context");
			});
		}
		
		@Test
		@DisplayName("TC-006-42 | Given a transaction with facility A rolled back after an exception, when the next transaction runs with no context, then app.facility_id is empty")
		void ___(){
			// Given a transaction with facility A rolled back after an exception.
			FacilityContext contextA = new FacilityContext(primexHospitalId, currentUserId);
			try {
				FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> {
					jdbcTemplate.execute("SELECT 1");
					throw new RuntimeException("Simulated application failure to force structural rollback");
				}));
			} catch (Exception ignored) {
				// Exception is swallowed deliberately to allow checking the sequential transaction flow
			}
			
			// When the next transaction runs with no context.
			transaction.executeWithoutResult(_ -> {
				String actualFacilityId = jdbcTemplate.queryForObject("SELECT current_setting('app.facility_id', true)", String.class);
				
				// Then app.facility_id is empty.
				assertTrue(actualFacilityId == null || actualFacilityId.isBlank(), "The transaction session context must not leak after an explicit database rollback");
			});
		}
		
		@Test
		@DisplayName("TC-006-43 | Given a transaction with facility A has finished, when the next transaction runs with facility B, then only facility B rows come back")
		void ____(){
			record ClinicRow(UUID facilityId, String code, String name) {}
			
			// Given a transaction with facility A has finished.
			FacilityContext contextA = new FacilityContext(primexHospitalId, currentUserId);
			FacilityContext.runWithContext(contextA, () -> transaction.executeWithoutResult(_ -> {
				jdbcTemplate.execute("SELECT 1");
			}));
			
			// When the next transaction runs with facility B (Novia Hospital).
			FacilityContext contextB = new FacilityContext(noviaHospitalId, currentUserId);
			FacilityContext.runWithContext(contextB, () -> transaction.executeWithoutResult(_ -> {
				String sqlQuery = """
					SELECT facility_id, code, name
					FROM clinic
					ORDER BY code
				""";
				List<ClinicRow> clinics = jdbcTemplate.query(sqlQuery, (resultSet, _) -> new ClinicRow(
						resultSet.getObject("facility_id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name")
				));
				
				// Then only Novia's rows sort and come back cleanly (Alphabetical: DENT, then PSYCH/MH)
				assertEquals(List.of(
						new ClinicRow(noviaHospitalId.uuid(), "DENT", "Novia Dental Clinic"),
						new ClinicRow(noviaHospitalId.uuid(), "PSYCH/MH", "Novia Psychiatry/Mental Health Clinic")
				), clinics, "The transaction must only yield rows corresponding to the active context binding");
			}));
		}
		
		@Test
		@DisplayName("TC-006-44 | Given a transaction with user U has finished, when the next transaction runs with no user context, then app.user_id evaluates to empty")
		void _____(){
			// Given a transaction with user U has finished.
			FacilityContext contextWithUser = new FacilityContext(primexHospitalId, currentUserId);
			FacilityContext.runWithContext(contextWithUser,
					() -> transaction.executeWithoutResult(_ -> jdbcTemplate.execute("SELECT 1")));
			
			// When the next transaction runs with no user context active.
			FacilityContext contextNoUser = new FacilityContext(primexHospitalId, null);
			FacilityContext.runWithContext(contextNoUser, () -> transaction.executeWithoutResult(_ -> {
				String actualUserId = jdbcTemplate.queryForObject("SELECT current_setting('app.user_id', true)", String.class);
				
				// Then app.user_id is empty or null.
				assertTrue(actualUserId == null || actualUserId.isBlank(),
						"User configurations from prior sessions should not poison current unauthenticated scopes");
			}));
		}
	}
}
