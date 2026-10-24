package ng.ereach.facilityserver.platform;

import jakarta.persistence.EntityManagerFactory;
import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.shared.id.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FacilityIsolationTests extends PostgresIntegrationTest {
	
	@Value("${spring.datasource.url}")
	private String databaseUrl;
	
	@Autowired
	TransactionTemplate transaction;
	@Autowired
	EntityManagerFactory entityManagerFactory;
	
	JdbcTemplate databaseTableOwner;
	
	@Autowired
	JdbcTemplate appUsingDatabaseTables;
	
	UUID primexHospitalId;
	UUID unilagMedicalCenterId;
	UUID otaGeneralHospitalId;
	UUID noviaHospitalId;
	UUID userId;
	
	@BeforeEach
	public void configureDatabaseAccess(){
		transaction = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
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
		
		userId = Ids.newId();
		primexHospitalId = Ids.newId();
		unilagMedicalCenterId = Ids.newId();
		otaGeneralHospitalId = Ids.newId();
		noviaHospitalId = Ids.newId();
		
		appUsingDatabaseTables.update("""
			INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix, nhfr_code)
			VALUES
			    (?, 'Primex Hospital', 'secondary', 'LA', 'LA-ALM', 'AH', 'NG-01515'),
			    (?, 'Unilag Medical Center', 'secondary', 'LA', 'LA-YAB', 'GH', 'NG-34122'),
			    (?, 'Ota General Hospital', 'primary', 'OG', 'OG-ODT', 'PB', 'NG-024497'),
			    (?, 'Novia Hospital', 'secondary', 'OG', 'OG-YWN', 'FT', 'NG-97762')
		""", primexHospitalId, unilagMedicalCenterId, otaGeneralHospitalId, noviaHospitalId);
		
		indicateFacilityIdentity(primexHospitalId, () -> {
			appUsingDatabaseTables.update("""
   				INSERT INTO facility_setting (id, facility_id, key_, value_)
   				VALUES (?, ?, 'lab.verification_required', 'true'::jsonb)
   			""", Ids.newId(), primexHospitalId);
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'GOPD', 'GOPD Primex')
			""", Ids.newId(), primexHospitalId);
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'ANC', 'ANC Primex')
			""", Ids.newId(), primexHospitalId);
		});
		
		indicateFacilityIdentity(unilagMedicalCenterId, () -> {
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'PAED', 'PAED Unilag Medical Centre')
			""", Ids.newId(), unilagMedicalCenterId);
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES  (?, ?, 'MOPD', 'MOPD Unilag Medical Centre')
			""", Ids.newId(), unilagMedicalCenterId);
			appUsingDatabaseTables.update("""
				INSERT INTO facility_setting (id, facility_id, key_, value_)
				VALUES (?, ?, 'lab.verification_required', 'false'::jsonb)
			""", Ids.newId(), unilagMedicalCenterId);
		});
		
		indicateFacilityIdentity(otaGeneralHospitalId, () -> {
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'ORTHO/OOPD', 'Orthopedic Outpatient Department')
			""", Ids.newId(), otaGeneralHospitalId);
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES  (?, ?, 'CARD', 'Cardiology Clinic Ota')
			""", Ids.newId(),  otaGeneralHospitalId);
			appUsingDatabaseTables.update("""
				INSERT INTO facility_setting (id, facility_id, key_, value_)
				VALUES (?, ?, 'lab.verification_required', 'false'::jsonb)
			""", Ids.newId(), otaGeneralHospitalId);
		});
		
		indicateFacilityIdentity(noviaHospitalId, () -> {
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'PSYCH/MH', 'Novia Psychiatry/Mental Health Clinic')
			""", Ids.newId(), noviaHospitalId);
			appUsingDatabaseTables.update("""
				INSERT INTO clinic (id, facility_id, code, name) VALUES  (?, ?, 'DENT', 'Novia Dental Clinic')
			""", Ids.newId(),  noviaHospitalId);
			appUsingDatabaseTables.update("""
				INSERT INTO facility_setting (id, facility_id, key_, value_)
				VALUES (?, ?, 'lab.verification_required', 'false'::jsonb)
			""", Ids.newId(), noviaHospitalId);
		});
	}
	
	private void indicateFacilityIdentity(UUID facilityId, Runnable work) {
		transaction.executeWithoutResult(_ -> {
			DataSource dataSource = Objects.requireNonNull(appUsingDatabaseTables.getDataSource());
			Connection connection = DataSourceUtils.getConnection(dataSource);
			
			try (Statement statement = connection.createStatement()) {
				statement.execute("SELECT set_config('app.facility_id', '" + facilityId + "',true)");
			} catch (SQLException e) {
				throw new RuntimeException("Failed to set facility session context", e);
			} finally {
				DataSourceUtils.releaseConnection(connection, dataSource);
			}
			work.run();
		});
	}
	
	@Test
	@DisplayName("TC-006-01 | With no facility set, the app reads zero clinics and zero facility settings")
	void testThatNoRowsAreReadFromFacilityTablesWhenNoFacilityIsSetOnTheTransaction() {
		// Given: No facility set
		
		// When: clinic and facility_setting are counted
		String clinicsSqlQuery = "SELECT COUNT(*) FROM clinic as f";
		String facilitySettingsSqlQuery = "SELECT COUNT(*) FROM facility_setting as fs";
		
		Long clinics = appUsingDatabaseTables.queryForObject(clinicsSqlQuery, Long.class);
		Long facilitySettings = appUsingDatabaseTables.queryForObject(facilitySettingsSqlQuery, Long.class);
		
		// Then: Both counts are 0
		assertEquals(0, clinics);
		assertEquals(0, facilitySettings);
	}
	
	@Test
	@DisplayName("TC-006-02 | An empty facility setting is treated as no facility and reads zero rows")
	void testThatAnEmptyFacilitySettingIsTreatedAsMissingAndReturnsZeroRows() {
		// Given: app.facility_id set to the empty text ''
		
		transaction.executeWithoutResult(_ -> {
			appUsingDatabaseTables.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class, "");
			
			// When: clinic and facility_setting are counted
			String clinicsSqlQuery = "SELECT COUNT(*) FROM clinic as f";
			String facilitySettingsSqlQuery = "SELECT COUNT(*) FROM facility_setting as fs";
			
			Long clinics = appUsingDatabaseTables.queryForObject(clinicsSqlQuery, Long.class);
			Long facilitySettings = appUsingDatabaseTables.queryForObject(facilitySettingsSqlQuery, Long.class);
			
			// Then: Both counts are 0
			assertEquals(0, clinics);
			assertEquals(0, facilitySettings);
		});
	}
	
	@Test
	@DisplayName("TC-006-03 | With facility A set, the app sees only A's clinics and settings, never B's")
	void testThatFacilityASeesOnlyItsOwnClinicsAndSettingsAndNothingFromFacilityB() {
		record ClinicRow(UUID facilityId, String code, String name) {}
		record SettingRow(String key, String value) {}
		record VisibleRows(List<ClinicRow> clinics, List<SettingRow> settings) {}

		VisibleRows visibleRows = transaction.execute(_ -> {
			// Given: A set
			appUsingDatabaseTables.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class, primexHospitalId.toString());

			// When: clinics are listed and settings counted
			List<ClinicRow> clinics = appUsingDatabaseTables.query("""
				SELECT facility_id, code, name
				FROM clinic
				ORDER BY code
			""", (resultSet, _) -> new ClinicRow(
					resultSet.getObject("facility_id", UUID.class),
					resultSet.getString("code"),
					resultSet.getString("name")));
			
			List<SettingRow> settings = appUsingDatabaseTables.query("""
				SELECT key_, value_::text AS value
				FROM facility_setting
				ORDER BY key_
				""",
				(resultSet, _) -> new SettingRow(
					resultSet.getString("key_"),
					resultSet.getString("value")));

			return new VisibleRows(clinics, settings);
		});

		// Then: Exactly GOPD A and ANC A; 1 setting
		assertEquals(List.of(
				new ClinicRow(primexHospitalId, "ANC", "ANC Primex"),
				new ClinicRow(primexHospitalId, "GOPD", "GOPD Primex")),
				visibleRows.clinics());
		assertEquals(List.of(
				new SettingRow("lab.verification_required", "true")),
				visibleRows.settings());
	}
	
	@Test
	@DisplayName("TC-006-04 | A facility ID that belongs to no facility reads zero clinics")
	void testThatAFacilityIdWithNoMatchingFacilityReadsZeroClinics() {
		
		transaction.executeWithoutResult(_ -> {
			// Given: app.facility_id set to a UUID that no facility has
			UUID nonExistentId = Ids.newId();
			appUsingDatabaseTables.queryForObject("SELECT set_config('app.facility_id', ? ,true)", String.class , nonExistentId.toString());
			
			// When: clinics are counted
			String clinicsSqlQuery = "SELECT COUNT(*) FROM clinic as f";
			Long clinics = appUsingDatabaseTables.queryForObject(clinicsSqlQuery, Long.class);
			
			// Then: The count is 0
			assertEquals(0L, clinics, "Non Existent Id should cause the query to return 0 rows");
		});
	}
	
	@Test
	@DisplayName("TC-006-05 | A malformed facility setting makes the query fail instead of returning rows")
	void testThatAMalformedFacilitySettingFailsTheQueryWithInvalidUuidErrorInsteadOfLeakingRows() {
		// Given: app.facility_id set to 'not-a-uuid'
		
		// When: clinics are selected
		
		// Then: The query fails with an invalid-UUID error; no rows are returned
	}
	
	@Test
	@DisplayName("TC-006-06 | The facility setting ends at commit, so a reused connection sees nothing after")
	void testThatTheFacilitySettingEndsWithTheTransactionSoTheSameConnectionSeesZeroRowsAfterCommit() {
		DataSource dataSource = Objects.requireNonNull(appUsingDatabaseTables.getDataSource());
		// Given: One connection: A set, clinics counted, transaction committed
		try (Connection connection = dataSource.getConnection()) {
			JdbcTemplate oneConnection = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
			
			connection.setAutoCommit(false);
			oneConnection.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class, noviaHospitalId.toString());
			// When: clinics are counted inside the transaction before commit
			// Then: 2 inside the transaction
			assertEquals(2L, oneConnection.queryForObject("SELECT COUNT(*) FROM clinic", Long.class));
			connection.commit();
			
			connection.setAutoCommit(true);
			// When: clinics are counted again on the same connection after commit
			// Then: 0 after it
			assertEquals(0L, oneConnection.queryForObject("SELECT COUNT(*) FROM clinic", Long.class));
		}catch (SQLException exception){
			throw new RuntimeException(exception);
		}
	}
	
	@Test
	@DisplayName("TC-006-07 | While set as facility A, inserting a clinic stamped with facility B is refused")
	void testThatFacilityACannotInsertAClinicStampedWithFacilityB() {
		// Given: A set

		DataAccessException exception = assertThrows(DataAccessException.class, () ->
			transaction.executeWithoutResult(_ -> {
				appUsingDatabaseTables.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class,
					primexHospitalId.toString());

				// When: a clinic stamped with B is inserted
				appUsingDatabaseTables.update("""
					INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, 'NEW', 'New Unilag Clinic')
					""", Ids.newId(), unilagMedicalCenterId);
			}));

			// Then: Refused: new row violates row-level security policy
			SQLException sqlException = (SQLException) exception.getMostSpecificCause();
			assertEquals("42501", sqlException.getSQLState(),
					"The insert should be refused with PostgreSQL's insufficient-privilege SQL state");
	}
	
	@Test
	@DisplayName("TC-006-08 | With no facility set, inserting any clinic is refused by the database")
	void testThatInsertingAClinicWithNoFacilitySetIsRefusedByRowLevelSecurity() {
		// Given: No facility set
		DataAccessException exception = assertThrows(DataAccessException.class, () ->
				transaction.executeWithoutResult(_ -> {
					// When: a clinic stamped with A is inserted
					appUsingDatabaseTables.update("""
							INSERT INTO clinic (id, facility_id, code, name)
							VALUES (?, ?, 'NEW', 'New Primex Clinic')
							""",
							Ids.newId(),
							primexHospitalId);
				}));

		// Then: Refused: new row violates row-level security policy
		SQLException sqlException = (SQLException) exception.getMostSpecificCause();
		assertEquals("42501", sqlException.getSQLState(),
				"The insert should be refused when no facility is set");
	}
	
	@Test
	@DisplayName("TC-006-09 | Facility A cannot move its own clinic into facility B by changing facility_id")
	void testThatFacilityACannotMoveItsOwnClinicToFacilityBByUpdatingFacilityId() {
		// Given: A set
		DataAccessException exception = assertThrows(DataAccessException.class, () ->
				transaction.executeWithoutResult(_ -> {
					appUsingDatabaseTables.queryForObject(
							"SELECT set_config('app.facility_id', ?, true)",
							String.class,
							primexHospitalId.toString());
					UUID gopdId = appUsingDatabaseTables.queryForObject(
							"SELECT id FROM clinic WHERE facility_id = ? AND code = 'GOPD'",
							(rs, _) -> rs.getObject("id", UUID.class),
							primexHospitalId);

					// When: GOPD A's facility_id is updated to B
					appUsingDatabaseTables.update(
							"UPDATE clinic SET facility_id = ? WHERE id = ?",
							unilagMedicalCenterId,
							gopdId);
				}));

		// Then: Refused: new row violates row-level security policy
		SQLException sqlException = (SQLException) exception.getMostSpecificCause();
		assertEquals("42501", sqlException.getSQLState(),
				"The update should be refused when moving a clinic to another facility");
	}
	
	@Test
	@DisplayName("TC-006-10 | A facility updating another facility's clinic by its exact ID changes 0 rows and the facility's data stays")
	void testThatFacilityAUpdatingFacilityBClinicByIdChangesZeroRowsAndLeavesItUnchanged() {
		// Given: A set
		record ClinicReference(UUID id, String name) {}
		ClinicReference paed = transaction.execute(_ -> {
			appUsingDatabaseTables.queryForObject(
					"SELECT set_config('app.facility_id', ?, true)",
					String.class,
					unilagMedicalCenterId.toString());
			return appUsingDatabaseTables.queryForObject(
					"SELECT id, name FROM clinic WHERE facility_id = ? AND code = 'PAED'",
					(rs, _) -> new ClinicReference(
							rs.getObject("id", UUID.class),
							rs.getString("name")),
					unilagMedicalCenterId);
		});

		Integer updatedRows = transaction.execute(_ -> {
			appUsingDatabaseTables.queryForObject(
					"SELECT set_config('app.facility_id', ?, true)",
					String.class,
					primexHospitalId.toString());

			// When: PAED B is renamed by its id
			return appUsingDatabaseTables.update(
					"UPDATE clinic SET name = ? WHERE id = ?",
					"Renamed by Primex",
					paed.id());
		});

		// Then: 0 rows updated; read as B, its name is unchanged
		assertEquals(0, updatedRows);
		String nameAfterAttempt = transaction.execute(_ -> {
			appUsingDatabaseTables.queryForObject(
					"SELECT set_config('app.facility_id', ?, true)",
					String.class,
					unilagMedicalCenterId.toString());
			return appUsingDatabaseTables.queryForObject(
					"SELECT name FROM clinic WHERE id = ?",
					String.class,
					paed.id());
		});
		assertEquals(paed.name(), nameAfterAttempt);
	}
	
	@Test
	@DisplayName("TC-006-11 | The app cannot hard-delete clinics; DELETE is refused with permission denied")
	void testThatTheApplicationRoleCannotHardDeleteClinicsAndGetsPermissionDenied() {
		transaction.executeWithoutResult(_ -> {
			// Given: A facility settings is made
			appUsingDatabaseTables.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class, unilagMedicalCenterId.toString());
			
			// When: The facility runs a `DELETE FROM clinic` query
			DataAccessException dae = assertThrows(DataAccessException.class,
					() -> appUsingDatabaseTables.update("DELETE FROM clinic"));
			
			SQLException sqlException = (SQLException) dae.getMostSpecificCause();
			
			// Then: The database refuses the query with "permission denied for table clinic"
			assertEquals("42501", sqlException.getSQLState(),
					"The delete should be refused because the app has no DELETE permission on clinic");
		});
	}
	
	@Test
	@DisplayName("TC-006-12 | The table owner with no facility set also reads zero clinics, because RLS is forced")
	void testThatTheTableOwnerAlsoReadsZeroClinicsWithNoFacilitySetBecauseRowLevelSecurityIsForced() {
		// Given: Connected as ereach_owner, no facility set
		
		// When: clinics are counted
		Long clinics = databaseTableOwner.queryForObject("SELECT COUNT(*) FROM clinic", Long.class);
		
		// Then: The count is 0, because the tables are forced
		assertEquals(0L, clinics, "The owner must see no clinics when no facility is set");
	}
	
	@Test
	@DisplayName("TC-006-13 | Every table with a facility_id column has RLS enabled, forced and at least one policy")
	void testThatEveryTableWithAFacilityIdColumnHasRowLevelSecurityEnabledForcedAndAPolicy() {
		// Given: The migrated schema
		
		// When: the catalogue is read: every table in public with a facility_id column
		List<String> facilityTables = databaseTableOwner.queryForList("""
				SELECT c.relname
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = 'public'
				  AND c.relkind = 'r'
				  AND EXISTS (SELECT 1 FROM pg_attribute a
				              WHERE a.attrelid = c.oid AND a.attname = 'facility_id' AND NOT a.attisdropped)
				ORDER BY c.relname
				""", String.class);
		List<String> unguardedTables = databaseTableOwner.queryForList("""
				SELECT c.relname
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = 'public'
				  AND c.relkind = 'r'
				  AND EXISTS (SELECT 1 FROM pg_attribute a
				              WHERE a.attrelid = c.oid AND a.attname = 'facility_id' AND NOT a.attisdropped)
				  AND (NOT c.relrowsecurity
				       OR NOT c.relforcerowsecurity
				       OR NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid))
				ORDER BY c.relname
				""", String.class);
		
		// Then: Each one has row-level security enabled and forced and at least one policy; the list of offenders is empty
		assertTrue(facilityTables.containsAll(List.of("clinic", "facility_setting")));
		assertEquals(List.of(), unguardedTables);
	}
}
