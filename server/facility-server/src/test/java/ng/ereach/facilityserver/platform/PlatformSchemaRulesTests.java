package ng.ereach.facilityserver.platform;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.shared.id.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PlatformSchemaRulesTests extends PostgresIntegrationTest {

	@Value("${spring.datasource.url}")
	private String databaseUrl;

	@Autowired
	JdbcTemplate database;

	@Autowired
	TransactionTemplate transaction;

	private UUID facilityA;
	private UUID facilityB;

	@BeforeEach
	void seedDatabase() {
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE facility_setting, clinic, facility, lga, state");
		owner.update("INSERT INTO state (code, name) VALUES ('LA', 'Lagos'), ('OG', 'Ogun')");
		owner.update("""
				INSERT INTO lga (code, state_code, name)
				VALUES ('LA-ALM', 'LA', 'Alimosho'), ('OG-ABN', 'OG', 'Abeokuta North')
				""");

		facilityA = Ids.newId();
		facilityB = Ids.newId();
		insertFacility(facilityA, "Facility A", "secondary", "AH", "NG-001", "LA", "LA-ALM");
		insertFacility(facilityB, "Facility B", "secondary", "BH", null, "OG", "OG-ABN");

		asFacility(facilityA, () -> {
			insertClinic(facilityA, "GOPD", "GOPD A");
			insertClinic(facilityA, "ANC", "ANC A");
			insertSetting(facilityA, "lab.verification_required");
		});
		asFacility(facilityB, () -> insertClinic(facilityB, "PAED", "PAED B"));
	}

	@Test
	@DisplayName("TC-006-14 | A facility cannot have two live clinics with the same code, such as two GOPDs")
	void testThatAFacilityCannotHaveTwoLiveClinicsWithTheSameCode() {
		assertSqlState("23505", () -> asFacility(facilityA, () -> insertClinic(facilityA, "GOPD", "Duplicate")));
	}

	@Test
	@DisplayName("TC-006-15 | Two different facilities may each have their own clinic with the code GOPD")
	void testThatTwoDifferentFacilitiesMayEachHaveTheirOwnClinicWithTheSameCode() {
		asFacility(facilityB, () -> insertClinic(facilityB, "GOPD", "GOPD B"));
		assertEquals(1, countClinics(facilityA, "GOPD"));
		assertEquals(1, countClinics(facilityB, "GOPD"));
	}

	@Test
	@DisplayName("TC-006-16 | After a clinic is soft-deleted, a new clinic may reuse its code in that facility")
	void testThatSoftDeletingAClinicFreesItsCodeForANewClinicInTheSameFacility() {
		asFacility(facilityA, () -> {
			database.update("UPDATE clinic SET deleted_at = CURRENT_TIMESTAMP WHERE facility_id = ? AND code = 'ANC'", facilityA);
			insertClinic(facilityA, "ANC", "Replacement ANC");
		});
		assertEquals(1, database.queryForObject(
				"SELECT COUNT(*) FROM clinic WHERE facility_id = ? AND code = 'ANC' AND deleted_at IS NULL",
				Integer.class, facilityA));
	}

	@Test
	@DisplayName("TC-006-17 | Clinic codes must be 2 to 20 capitals, digits or underscores; others are refused")
	void testThatClinicCodesOutsideTwoToTwentyCapitalLettersDigitsOrUnderscoresAreRefused() {
		for (String code : new String[]{"gopd", "G", "ABCDEFGHIJKLMNOPQRSTU"}) {
			assertSqlState("23514", () -> asFacility(facilityA, () -> insertClinic(facilityA, code, "Invalid")));
		}
	}

	@Test
	@DisplayName("TC-006-18 | A clinic cannot be created for a facility that does not exist")
	void testThatAClinicCannotBeCreatedForAFacilityThatDoesNotExist() {
		UUID unknown = Ids.newId();
		assertSqlState("23503", () -> asFacility(unknown, () -> insertClinic(unknown, "NEW", "Unknown")));
	}

	@Test
	@DisplayName("TC-006-19 | The facility list is readable with no facility set, so sign-in can show it")
	void testThatTheFacilityListIsReadableWithNoFacilitySetSoSignInCanListAUsersFacilities() {
		assertEquals(2, database.queryForObject("SELECT COUNT(*) FROM facility", Integer.class));
	}

	@Test
	@DisplayName("TC-006-20 | Hospital number prefixes must be 2 to 6 capitals and never repeat across facilities")
	void testThatHospitalNumberPrefixesMustBeTwoToSixCapitalLettersAndUniqueAcrossFacilities() {
		assertSqlState("23514", () -> insertFacility(Ids.newId(), "Lower", "secondary", "gh", null, "LA", "LA-ALM"));
		assertSqlState("23514", () -> insertFacility(Ids.newId(), "Long", "secondary", "ABCDEFG", null, "LA", "LA-ALM"));
		assertSqlState("23505", () -> insertFacility(Ids.newId(), "Duplicate", "secondary", "AH", null, "LA", "LA-ALM"));
	}

	@Test
	@DisplayName("TC-006-21 | The national facility code is optional, but no two facilities may share one")
	void testThatTheNationalFacilityCodeIsOptionalButCannotBeSharedByTwoFacilities() {
		assertSqlState("23505", () -> insertFacility(Ids.newId(), "Duplicate code", "secondary", "CH", "NG-001", "LA", "LA-ALM"));
		insertFacility(Ids.newId(), "No code", "secondary", "CH", null, "LA", "LA-ALM");
	}

	@Test
	@DisplayName("TC-006-22 | A national facility code made only of spaces is refused as blank")
	void testThatANationalFacilityCodeMadeOnlyOfSpacesIsRefusedAsBlank() {
		assertSqlState("23514", () -> insertFacility(Ids.newId(), "Blank code", "secondary", "CH", "   ", "LA", "LA-ALM"));
	}

	@Test
	@DisplayName("TC-006-23 | A state registration number and its issuing agency must be given together or not at all")
	void testThatStateRegistrationNumberAndIssuingAgencyMustBeGivenTogetherOrNotAtAll() {
		assertSqlState("23514", () -> insertFacilityWithRegistration(Ids.newId(), "Number only", "LSH/1", null));
		assertSqlState("23514", () -> insertFacilityWithRegistration(Ids.newId(), "Agency only", null, "HEFAMAA"));
		insertFacilityWithRegistration(Ids.newId(), "Both", "LSH/1", "HEFAMAA");
	}

	@Test
	@DisplayName("TC-006-24 | A facility's LGA must belong to its state; Lagos with an Ogun LGA is refused")
	void testThatAFacilityLgaMustBelongToItsStateAndAMismatchedPairIsRefused() {
		assertSqlState("23503", () -> insertFacility(Ids.newId(), "Mismatched", "secondary", "CH", null, "LA", "OG-ABN"));
	}

	@Test
	@DisplayName("TC-006-25 | Facility type must be phc, clinic, secondary or tertiary; anything else is refused")
	void testThatFacilityTypeOutsidePhcClinicSecondaryAndTertiaryIsRefused() {
		assertSqlState("23514", () -> insertFacility(Ids.newId(), "Invalid type", "hospital", "CH", null, "LA", "LA-ALM"));
	}

	@Test
	@DisplayName("TC-006-26 | A facility setting with a key outside the seven known keys is refused")
	void testThatAFacilitySettingWithAnUnknownKeyIsRefused() {
		assertSqlState("23514", () -> asFacility(facilityA, () ->
				database.update("INSERT INTO facility_setting (id, facility_id, key_, value_) VALUES (?, ?, 'foo.bar', '{}'::jsonb)",
						Ids.newId(), facilityA)));
	}

	@Test
	@DisplayName("TC-006-27 | A facility cannot store two live values for the same setting key")
	void testThatAFacilityCannotStoreTwoLiveValuesForTheSameSettingKey() {
		assertSqlState("23505", () -> asFacility(facilityA, () -> insertSetting(facilityA, "lab.verification_required")));
	}

	@Test
	@DisplayName("TC-006-28 | The app cannot insert into or update the state and LGA reference tables")
	void testThatTheApplicationRoleCannotInsertIntoOrUpdateTheStateAndLgaReferenceTables() {
		assertSqlState("42501", () -> database.update("INSERT INTO state (code, name) VALUES ('KT', 'Katsina')"));
		assertSqlState("42501", () -> database.update("UPDATE lga SET name = 'Changed' WHERE code = 'LA-ALM' AND state_code = 'LA'"));
	}

	private void insertClinic(UUID facilityId, String code, String name) {
		database.update("INSERT INTO clinic (id, facility_id, code, name) VALUES (?, ?, ?, ?)",
				Ids.newId(), facilityId, code, name);
	}

	private void insertSetting(UUID facilityId, String key) {
		database.update("INSERT INTO facility_setting (id, facility_id, key_, value_) VALUES (?, ?, ?, 'true'::jsonb)",
				Ids.newId(), facilityId, key);
	}

	private void insertFacility(UUID id, String name, String type, String prefix, String nhfrCode, String state, String lga) {
		database.update("""
				INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix, nhfr_code)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, name, type, state, lga, prefix, nhfrCode);
	}

	private void insertFacilityWithRegistration(UUID id, String name, String number, String issuer) {
		database.update("""
				INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix,
				                      state_reg_number, state_reg_number_issuer)
				VALUES (?, ?, 'secondary', 'LA', 'LA-ALM', ?, ?, ?)
				""", id, name, "CH", number, issuer);
	}

	private void asFacility(UUID facilityId, Runnable work) {
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

	private int countClinics(UUID facilityId, String code) {
		return transaction.execute(_ -> {
			database.queryForObject("SELECT set_config('app.facility_id', ?, true)", String.class, facilityId.toString());
			return database.queryForObject("SELECT COUNT(*) FROM clinic WHERE code = ?", Integer.class, code);
		});
	}

	private void assertSqlState(String expected, Runnable operation) {
		DataAccessException exception = assertThrows(DataAccessException.class, operation::run);
		assertEquals(expected, ((SQLException) exception.getMostSpecificCause()).getSQLState());
	}
}
