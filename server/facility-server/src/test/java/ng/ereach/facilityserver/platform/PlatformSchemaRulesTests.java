package ng.ereach.facilityserver.platform;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.shared.id.FacilityId;
import ng.ereach.shared.id.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PlatformSchemaRulesTests extends PostgresIntegrationTest {

	@Value("${spring.datasource.url}")
	private String databaseUrl;

	@Autowired
	JdbcTemplate database;

	@Autowired
	TransactionTemplate transaction;
	
	UUID currentUserId;
	private UUID defaultTestFacility;
	private UUID alternativeTestFacility;
	
	record ClinicRow (UUID facilityId, String code, String name) {}
	record FacilityRow (UUID id, String name, String type, String prefix) {}
	
	@BeforeEach
	void seedDatabase() {
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE facility_setting, clinic, facility, lga, state");
		owner.update("""
				INSERT INTO state (code, name)
				VALUES ('LA', 'Lagos'), ('OG', 'Ogun'), ('OY', 'Oyo'), ('ABJ', 'Abuja')
				""");
		owner.update("""
				INSERT INTO lga (code, state_code, name)
				VALUES ('LA-ALM', 'LA', 'Alimosho'), ('OG-ABN', 'OG', 'Abeokuta North'),
				       ('LA-ISL', 'LA', 'Isolo'), ('LA-LND', 'LA', 'Lagos Island'),
				       ('LA-MUS', 'LA', 'Mushin'), ('OY-IBD', 'OY', 'Ibadan'),
				       ('ABJ-ABJ', 'ABJ', 'Abuja'), ('LA-YAB', 'LA', 'Yaba'), ('LA-BAR', 'LA', 'Bariga')
				""");
		
		currentUserId = Ids.newId();
		defaultTestFacility = Ids.newId();
		alternativeTestFacility = Ids.newId();
		
		insertFacility(defaultTestFacility, "Isolo General Hospital", "secondary", "IGH", "NG-001", "LA", "LA-ISL");
		insertFacility(alternativeTestFacility, "Lagos Island General Hospital", "secondary", "LIGH", null, "LA", "LA-LND");

		runQueryAsFacility(defaultTestFacility, () -> {
			insertClinic(defaultTestFacility, "GOPD", "General Outpatient Department IGH");
			insertClinic(defaultTestFacility, "ANC", "Antenatal Clinic IGH");
			insertSetting(defaultTestFacility);
		});
		runQueryAsFacility(alternativeTestFacility, () -> insertClinic(alternativeTestFacility, "PAED", "PAED B"));
	}

	@Test
	@DisplayName("TC-006-14 | A facility cannot have two live clinics with the same code, such as two GOPDs")
	void testThatAFacilityCannotHaveTwoLiveClinicsWithTheSameCode() {
		// Given a facility A created with one clinic add to it
		FacilityId facilityId = FacilityId.newId();
		insertFacility(facilityId.uuid(), "Lagos University Teaching Hospital (LUTH)", "tertiary", "LUTH",
			"NG-002879", "LA", "LA-MUS");
		
		runQueryAsFacility(facilityId.uuid(), () -> insertClinic(facilityId.uuid(), "DENT", "Luth Dental Clinic"));
		
		// When we try to add another clinic with the same code as an already existing clinic in the facility,
		String sqlState = "";
		try {
			runQueryAsFacility(facilityId.uuid(), () -> insertClinic(facilityId.uuid(), "DENT", "Luth Dental Clinic"));
		} catch (DataAccessException exception) {
			sqlState = ((SQLException) exception.getMostSpecificCause()).getSQLState();
		}
		
		// The insert fails with an SQL state code 23505.
		assertEquals("23505", sqlState);
	}

	@Test
	@DisplayName("TC-006-15 | Two different facilities may each have their own clinic with the code GOPD")
	void testThatTwoDifferentFacilitiesMayEachHaveTheirOwnClinicWithTheSameCode() {
		// Given a facility A and another Facility
		FacilityId nhaId = FacilityId.newId();
		insertFacility(nhaId.uuid(), "National Hospital Abuja", "tertiary", "NHA",
				"NG-55467", "ABJ", "ABJ-ABJ");
		
		FacilityId uchId = FacilityId.newId();
		insertFacility(uchId.uuid(), "University College Hospital, Ibadan", "tertiary", "UCH",
				"NG-29008", "OY", "OY-IBD");
		
		// There currently exists zero clinics under both facilities
		assertEquals(0, countClinicsUnderFacility(nhaId.uuid(), "PAED"));
		assertEquals(0, countClinicsUnderFacility(uchId.uuid(), "PAED"));
		
		// When we add one clinic with the same code to both facilities.
		runQueryAsFacility(nhaId.uuid(), () -> insertClinic(nhaId.uuid(), "PAED", "NHA PAED Clinic"));
		runQueryAsFacility(uchId.uuid(), () -> insertClinic(uchId.uuid(), "PAED", "UCH PAED Clinic"));
		
		// The insert succeeds for both facilities, and both facilities now have one each clinic under them.
		assertEquals(1, countClinicsUnderFacility(nhaId.uuid(), "PAED"));
		assertEquals(1, countClinicsUnderFacility(uchId.uuid(), "PAED"));
	}
	
	@Test
	@DisplayName("TC-006-16 | After a clinic is soft-deleted, a new clinic may reuse its code in that facility")
	void testThatSoftDeletingAClinicFreesItsCodeForANewClinicInTheSameFacility() {
		// Given an existing clinic under a facility (Default Test Facility - Isolo General Hospital).
		String clinicCode = "ORTHO";
		String clinicName = "IGH Orthopaedic Clinic";
		runQueryAsFacility(defaultTestFacility, () -> insertClinic(defaultTestFacility, clinicCode, clinicName));
		
		// When it is deleted, a soft-delete happens, it can still be fetched by setting when it is deleted
		runQueryAsFacility(defaultTestFacility,
			() -> database.update("UPDATE clinic SET deleted_at = CURRENT_TIMESTAMP WHERE facility_id = ? AND code = 'ORTHO'", defaultTestFacility));
		
		runQueryAsFacility(defaultTestFacility, () -> {
			ClinicRow clinicRow = fetchClinic(defaultTestFacility, clinicCode, true);
			assertAll(()-> {
				assertEquals(defaultTestFacility, clinicRow.facilityId);
				assertEquals(clinicName, clinicRow.name());
				assertEquals(clinicCode, clinicRow.code());
			});
		});
		
		// When a new clinic is created under the same facility, with the same code as the soft-deleted facility
		Exception shouldNotThrow = null;
		try {
			runQueryAsFacility(defaultTestFacility, () -> insertClinic(defaultTestFacility, clinicCode, clinicName));
		}catch (DataAccessException exception){
			shouldNotThrow = exception;
		}
		
		// Then the insert succeeds because the clinic table has a partial unique index on facility_id, code
		assertNull(shouldNotThrow);
		runQueryAsFacility(defaultTestFacility, () -> {
			assertEquals(1, database.queryForObject(
					"SELECT COUNT(*) FROM clinic WHERE facility_id = ? AND code = ? AND deleted_at IS NULL",
					Integer.class, defaultTestFacility, clinicCode));
			ClinicRow clinicRow = fetchClinic(defaultTestFacility, clinicCode, false);
			assertAll(() -> {
				assertEquals(defaultTestFacility, clinicRow.facilityId());
				assertEquals(clinicName, clinicRow.name());
				assertEquals(clinicCode, clinicRow.code());
			});
		});
	}
	
	@Test
	@DisplayName("TC-006-17 | Clinic codes must be 2 to 20 capitals, digits or underscores; others are refused")
	void testThatClinicCodesOutsideTwoToTwentyCapitalLettersDigitsOrUnderscoresAreRefused() {
		// Given an existing facility (Alternative Test Facility - Lagos Island General Hospital)
		SQLException failureCausedBySmallCapsCode = null;
		SQLException failureCausedByTooSmallLengthCode = null;
		SQLException failureCausedByTooLargeLengthCode = null;
		
		// When we try to create a new clinic an invalid clinic code under the existing facility
		try {
			// code is 'ment' instead of 'MENT'
			runQueryAsFacility(alternativeTestFacility,
				() -> insertClinic(alternativeTestFacility, "ment", "LIGH Mental Health Clinic"));
		} catch (DataAccessException exception) {
			assertThat(exception).isExactlyInstanceOf(DataIntegrityViolationException.class);
			failureCausedBySmallCapsCode = (SQLException) exception.getMostSpecificCause();
			try {
				// code is 'M', has to be between 2 and 20 characters
				runQueryAsFacility(alternativeTestFacility,
					() -> insertClinic(alternativeTestFacility, "M", "LIGH Mental Health Clinic"));
			} catch (DataAccessException exception1) {
				assertThat(exception1).isExactlyInstanceOf(DataIntegrityViolationException.class);
				failureCausedByTooSmallLengthCode = (SQLException)  exception1.getMostSpecificCause();
				try {
					// code is of 21 letters long
					runQueryAsFacility(alternativeTestFacility,
						() -> insertClinic(alternativeTestFacility, "LIGH-Mental-Health-Clinic", "LIGH Mental Health Clinic"));
				} catch (DataAccessException exception2) {
					assertThat(exception2).isExactlyInstanceOf(DataIntegrityViolationException.class);
					failureCausedByTooLargeLengthCode = (SQLException) exception2.getMostSpecificCause();
				}
			}
		}
		
		assertNotNull(failureCausedBySmallCapsCode);
		assertNotNull(failureCausedByTooSmallLengthCode);
		assertNotNull(failureCausedByTooLargeLengthCode);
		
		// The insert for each of them fails with a 23514 SQL state exception
		assertEquals("23514", failureCausedBySmallCapsCode.getSQLState());
		assertEquals("23514", failureCausedByTooSmallLengthCode.getSQLState());
		assertEquals("23514", failureCausedByTooLargeLengthCode.getSQLState());
	}
	
	@Test
	@DisplayName("TC-006-18 | A clinic cannot be created for a facility that does not exist")
	void testThatAClinicCannotBeCreatedForAFacilityThatDoesNotExist() {
		// Given a non-existent facility (id does not point to any row on the facility table)
		UUID nonExistentFacilityId = Ids.newId();
		
		// When we try to insert to a new clinic with the id pointing to a non-existed facility
		SQLException shouldThrow = null;
		try {
			runQueryAsFacility(nonExistentFacilityId, () -> insertClinic(nonExistentFacilityId, "NEW", "Unknown"));
		} catch (DataAccessException exception) {
			shouldThrow = (SQLException) exception.getMostSpecificCause();
		}
		
		// Then the insert fails with a 23503 SQL state exception
		assertNotNull(shouldThrow);
		assertEquals("23503", shouldThrow.getSQLState());
	}
	
	@Test
	@DisplayName("TC-006-19 | The facility list is readable with no facility set, so sign-in can show it")
	void testThatTheFacilityListIsReadableWithNoFacilitySetSoSignInCanListAUsersFacilities() {
		// Given no facility context set (No `runQueryAsFacility()` method call).
		
		// When we fetch all facilities from the facility table
		List<FacilityRow> allFacilities = fetchAllFacilities();
		// Or count all facilities on the facility table
		Integer allFacilitiesCount = database.queryForObject("SELECT COUNT(*) FROM facility", Integer.class);
		
		// Then all non-deleted facilities in the facility table are listed out
		assertEquals(2, allFacilities.size());
		assertEquals(allFacilitiesCount, allFacilities.size());
	}
	
	@Test
	@DisplayName("TC-006-20 | Hospital number prefixes must be 2 to 6 capitals and never repeat across facilities")
	void testThatHospitalNumberPrefixesMustBeTwoToSixCapitalLettersAndUniqueAcrossFacilities() {
		// Given three types of facilities trying to be created with three 3 different types of violations on hosptial_number_prefix column
		FacilityRow facilityViolatingTooSmallPrefixLength =
			new FacilityRow(Ids.newId(), "Too Small Prefix Length Hospital", "phc", "T");
		FacilityRow facilityViolatingTooLargePrefixLength =
			new FacilityRow(Ids.newId(), "Too Large Prefix Length Hospital", "secondary", "TILAPIA");
		FacilityRow facilityViolatingDuplicateConstraint =
			new FacilityRow(Ids.newId(), "Isolo General Hospital", "clinic", "IGH");
		
		SQLException failureCausedByTooSmallPrefixLength = null;
		SQLException failureCausedByTooLargePrefixLength = null;
		SQLException failureCausedByDuplicatePrefix = null;
		
		// When we try to create new facilities with violations on the hosptial_number_prefix column.
		try {
			// code is 1 character long, has to be between 2 and 6 characters
			insertFacility(facilityViolatingTooSmallPrefixLength.id(), facilityViolatingTooSmallPrefixLength.name(),
				facilityViolatingTooSmallPrefixLength.type(), facilityViolatingTooSmallPrefixLength.prefix(),
				null, "LA", "LA-ALM");
		} catch (DataAccessException exception) {
			failureCausedByTooSmallPrefixLength = (SQLException) exception.getMostSpecificCause();
			try {
				// code is 7 characters long, it has to be between 2 and 6 characters
				insertFacility(facilityViolatingTooLargePrefixLength.id(), facilityViolatingTooLargePrefixLength.name(),
					facilityViolatingTooLargePrefixLength.type(), facilityViolatingTooLargePrefixLength.prefix(),
					null, "LA", "LA-ALM");
				assertThat(exception).isExactlyInstanceOf(DataIntegrityViolationException.class);
				
			} catch (DataAccessException exception1) {
				failureCausedByTooLargePrefixLength = (SQLException) exception1.getMostSpecificCause();
				try {
					// insert with same code twice
					insertFacility(facilityViolatingDuplicateConstraint.id(), facilityViolatingDuplicateConstraint.name(),
						facilityViolatingDuplicateConstraint.type(), facilityViolatingDuplicateConstraint.prefix(),
						null, "LA", "LA-ALM");
					insertFacility(facilityViolatingDuplicateConstraint.id(), facilityViolatingDuplicateConstraint.name(),
						facilityViolatingDuplicateConstraint.type(), facilityViolatingDuplicateConstraint.prefix(),
						null, "LA", "LA-ALM");
				} catch (DataAccessException exception2) {
					assertThat(exception2).isExactlyInstanceOf(DuplicateKeyException.class);
					failureCausedByDuplicatePrefix = (SQLException) exception2.getMostSpecificCause();
				}
			}
		}
		
		// Then we always expect 23514, 23514 and 23505.
		assertNotNull(failureCausedByTooSmallPrefixLength);
		assertNotNull(failureCausedByTooLargePrefixLength);
		assertNotNull(failureCausedByDuplicatePrefix);
		assertEquals("23514", failureCausedByTooSmallPrefixLength.getSQLState());
		assertEquals("23514", failureCausedByTooLargePrefixLength.getSQLState());
		assertEquals("23505", failureCausedByDuplicatePrefix.getSQLState());
	}
	
	@Test
	@DisplayName("TC-006-21 | The national facility code is optional, but no two facilities may share one")
	void testThatTheNationalFacilityCodeIsOptionalButCannotBeSharedByTwoFacilities() {
		FacilityRow gbagadaGeneralHospital =
				new FacilityRow(Ids.newId(), "Gbagada General Hospital", "secondary", "GGH");
		assertDoesNotThrow(() -> insertFacility(
			gbagadaGeneralHospital.id, gbagadaGeneralHospital.name(), gbagadaGeneralHospital.type(),
			gbagadaGeneralHospital.prefix(), null, "LA", "LA-BAR"
		));  
		
		FacilityRow unilagMedicalCentre =
				new FacilityRow(Ids.newId(), "UNILAG Medical Centre", "clinic", "UNIMC");
		insertFacility(
			unilagMedicalCentre.id, unilagMedicalCentre.name(), unilagMedicalCentre.type(), unilagMedicalCentre.prefix(),
				"NG-56783", "LA", "LA-BAR"
		);
		
		SQLException shouldThrow = null;
		try {
			FacilityRow phcBariga =
					new FacilityRow(Ids.newId(), "Primary Health Centre, Bariga", "phc", "PHCB");
			insertFacility(phcBariga.id, phcBariga.name(), phcBariga.type(), phcBariga.prefix(), "NG-56783", "LA", "LA-BAR");
		} catch(DataAccessException exception) {
			assertThat(exception).isExactlyInstanceOf(DuplicateKeyException.class);
			shouldThrow = (SQLException) exception.getMostSpecificCause();
		}
		assertNotNull(shouldThrow);
		assertEquals("23505", shouldThrow.getSQLState());
	}
	
	@Test
	@DisplayName("TC-006-22 | A national facility code made only of spaces is refused as blank")
	void testThatANationalFacilityCodeMadeOnlyOfSpacesIsRefusedAsBlank() {
		// Given a new facility.
		// When its code is just spaces.
		// Then we always expect 23514 failure.
		assertFailWithSqlState("23514", () -> insertFacility(Ids.newId(), "Blank code", "secondary", "CH", "   ", "LA", "LA-ALM"));
	}
	
	@Test
	@DisplayName("TC-006-23 | A state registration number and its issuing agency must be given together or not at all")
	void testThatStateRegistrationNumberAndIssuingAgencyMustBeGivenTogetherOrNotAtAll() {
		// Given a facility with only one side of the registration pair.
		// When it is inserted.
		// Then the insert fails with a 23514 SQL state exception.
		assertFailWithSqlState("23514", () -> insertFacilityWithRegistration(Ids.newId(), "Number only", "LSH/1", null));
		assertFailWithSqlState("23514", () -> insertFacilityWithRegistration(Ids.newId(), "Agency only", null, "HEFAMAA"));

		// Given a facility with both registration values.
		// When it is inserted.
		// Then the insert succeeds.
		UUID facilityId = Ids.newId();
		assertDoesNotThrow(() -> insertFacilityWithRegistration(facilityId, "Both", "LSH/1", "HEFAMAA"));
		assertEquals(1, database.queryForObject(
				"SELECT COUNT(*) FROM facility WHERE id = ? AND state_registration_number = ? AND state_registration_agency = ?",
				Integer.class, facilityId, "LSH/1", "HEFAMAA"));
	}
	
	@Test
	@DisplayName("TC-006-24 | A facility's LGA must belong to its state; Lagos with an Ogun LGA is refused")
	void testThatAFacilityLgaMustBelongToItsStateAndAMismatchedPairIsRefused() {
		// Given a facility whose state and LGA match.
		// When it is inserted.
		// Then the insert succeeds.
		UUID validFacilityId = Ids.newId();
		assertDoesNotThrow(() -> insertFacility(
				validFacilityId, "Valid Lagos Facility", "secondary", "VLF", null, "LA", "LA-ALM"));
		assertEquals(1, database.queryForObject(
				"SELECT COUNT(*) FROM facility WHERE id = ? AND state_code = ? AND lga_code = ?",
				Integer.class, validFacilityId, "LA", "LA-ALM"));

		// Given a facility whose state and LGA do not match.
		// When it is inserted.
		// Then the insert fails with a 23503 SQL state exception.
		assertFailWithSqlState("23503", () -> insertFacility(Ids.newId(), "Mismatched", "secondary", "CH", null, "LA", "OG-ABN"));
	}
	
	@Test
	@DisplayName("TC-006-25 | Facility type must be phc, clinic, secondary or tertiary; anything else is refused")
	void testThatFacilityTypeOutsidePhcClinicSecondaryAndTertiaryIsRefused() {
		// Given a facility, with its types set to primary which is not in (phc, clinic, secondary or tertiary)
		FacilityRow phcBariga =
				new FacilityRow(Ids.newId(), "Primary Health Centre, Bariga", "primary", "PHCB");
		
		// When we try to insert that facility
		SQLException shouldThrow = null;
		try {
			insertFacility(phcBariga.id, phcBariga.name(), phcBariga.type(), phcBariga.prefix(), "NG-56783", "LA", "LA-BAR");
		} catch (DataAccessException exception) {
			assertThat(exception).isExactlyInstanceOf(DataIntegrityViolationException.class);
			shouldThrow = (SQLException) exception.getMostSpecificCause();
		}
		
		// Then it fails with a 23514 SQL state failure.
		assertNotNull(shouldThrow);
		assertEquals("23514", shouldThrow.getSQLState());
	}
	
	@Test
	@DisplayName("TC-006-26 | A facility setting with a key outside the seven known keys is refused")
	void testThatAFacilitySettingWithAnUnknownKeyIsRefused() {
		assertFailWithSqlState("42703", () -> runQueryAsFacility(defaultTestFacility, () ->
				database.update("INSERT INTO facility_setting (id, facility_id, key_, value_) VALUES (?, ?, 'foo.bar', '{}'::jsonb)",
						Ids.newId(), defaultTestFacility)));
	}
	
	@Test
	@DisplayName("TC-006-27 | A facility cannot store two live values for the same setting key")
	void testThatAFacilityCannotStoreTwoLiveValuesForTheSameSettingKey() {
		// Given facility A already has lab.verification_required.
		// When a second live row with that key is inserted, then we always expect 23505. It fails.
		assertFailWithSqlState("23505", () -> runQueryAsFacility(defaultTestFacility, () -> insertSetting(defaultTestFacility)));
	}
	
	@Test
	@DisplayName("TC-006-28 | The app cannot insert into or update the state and LGA reference tables")
	void testThatTheApplicationRoleCannotInsertIntoOrUpdateTheStateAndLgaReferenceTables() {
		//TC-006-28: Given the app role: ereach_app.
		// When it tries to insert into state or updates lga.
		// Then we always expect a refusal (42501).
		assertFailWithSqlState("42501", () -> database.update("INSERT INTO state (code, name) VALUES ('KT', 'Katsina')"));
		assertFailWithSqlState("42501", () -> database.update("UPDATE lga SET name = 'Changed' WHERE code = 'LA-ALM' AND state_code = 'LA'"));
	}
	
	private void insertClinic(UUID facilityId, String code, String name) {
		database.update("""
				INSERT INTO clinic (id, facility_id, code, name, created_by, updated_by)
				VALUES (?, ?, ?, ?, ?, ?)
				""",
				Ids.newId(), facilityId, code, name, currentUserId, currentUserId);
	}
	
	private void insertSetting(UUID facilityId) {
		database.update("""
				INSERT INTO facility_setting (id, facility_id, key, value, created_by, updated_by)
				VALUES (?, ?, ?, 'true'::jsonb, ?, ?)
				""",
				Ids.newId(), facilityId, "lab.verification_required", currentUserId, currentUserId);
	}
	
	private void insertFacility(UUID id, String name, String type, String prefix, String nhfrCode, String state, String lga) {
		database.update("""
				INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix, nhfr_code, created_by, updated_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, name, type, state, lga, prefix, nhfrCode, currentUserId, currentUserId);
	}
	
	private void insertFacilityWithRegistration(UUID id, String name, String number, String issuer) {
		database.update("""
				INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix,
				                      state_registration_number, state_registration_agency, created_by, updated_by)
				VALUES (?, ?, 'secondary', 'LA', 'LA-ALM', ?, ?, ?, ?, ?)
				""", id, name, "CH", number, issuer, currentUserId, currentUserId);
	}
	
	private void runQueryAsFacility(UUID facilityId, Runnable work) {
		transaction.executeWithoutResult(_ -> {
			DataSource dataSource = Objects.requireNonNull(database.getDataSource());
			Connection connection = DataSourceUtils.getConnection(dataSource);
			try (Statement statement = connection.createStatement()) {
				statement.execute("SELECT set_config('app.facility_id', '" + facilityId + "', true)");
			} catch (SQLException exception) {
				throw new RuntimeException("Failed to set facility session context", exception);
			} finally {
				DataSourceUtils.releaseConnection(connection, dataSource);
			}
			work.run();
		});
	}
	
	private int countClinicsUnderFacility(UUID facilityId, String code) {
		return transaction.execute(_ -> {
			database.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class, facilityId.toString());
			return database.queryForObject("SELECT COUNT(*) FROM clinic WHERE code = ?", Integer.class, code);
		});
	}
	
	private void assertFailWithSqlState(String expected, Runnable operation) {
		DataAccessException exception = assertThrows(DataAccessException.class, operation::run);
		assertEquals(expected, ((SQLException) exception.getMostSpecificCause()).getSQLState());
	}
	
	private List<FacilityRow> fetchAllFacilities(){
		return database.query(
			"""
			SELECT id, name, type, hospital_number_prefix
			FROM facility
			ORDER BY name
			""",
			(resultSet, _) -> new FacilityRow(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("type"),
				resultSet.getString("hospital_number_prefix")
			)
		);
	}
	
	private ClinicRow fetchClinic(UUID facilityId, String clinicCode, boolean isDeleted) {
		String queryForSoftDeletedRow = """
				SELECT facility_id, code, name
				FROM clinic
				WHERE facility_id = ?
					AND code = ?
					AND deleted_at IS NOT NULL
				""";
		String queryForExistingRow = """
				SELECT facility_id, code, name
				FROM clinic
				WHERE facility_id = ?
					AND code = ?
					AND deleted_at IS NULL
				""";
		
		return database.queryForObject(
				isDeleted ? queryForSoftDeletedRow : queryForExistingRow,
				(resultSet, _) -> new ClinicRow(
						resultSet.getObject("facility_id", UUID.class),
						resultSet.getString("code"),
						resultSet.getString("name")
				),
				facilityId, clinicCode
		);
	}
}
