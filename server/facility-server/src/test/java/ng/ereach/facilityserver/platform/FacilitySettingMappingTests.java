package ng.ereach.facilityserver.platform;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.facilityserver.platform.entities.FacilitySetting;
import ng.ereach.facilityserver.platform.entities.enums.FacilitySettingKey;
import ng.ereach.facilityserver.platform.repositories.FacilitySettingRepository;
import ng.ereach.shared.id.FacilityId;
import ng.ereach.shared.id.SystemActors;
import ng.ereach.shared.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FacilitySettingMappingTests extends PostgresIntegrationTest {

	@Value("${spring.datasource.url}")
	String databaseUrl;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	FacilitySettingRepository settingRepository;

	FacilityId primexHospitalId;
	UserId currentUserId;

	@BeforeEach
	void seedDatabase() {
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE role_assignment, facility_membership, user_security_key, facility_setting, clinic, facility, lga, state");
		owner.update("INSERT INTO state (code, name) VALUES ('LA', 'Lagos')");
		owner.update("INSERT INTO lga (code, state_code, name) VALUES ('LA-01', 'LA', 'Agege')");

		primexHospitalId = FacilityId.newId();
		currentUserId = UserId.newId();

		FacilityContext.runWithContext(FacilityContext.forUserOnly(SystemActors.INSTALLER),
				() -> transaction.executeWithoutResult(_ -> jdbcTemplate.update("""
					INSERT INTO facility (id, name, type, state_code, lga_code, hospital_number_prefix)
					VALUES (?, 'Primex Hospital', 'secondary', 'LA', 'LA-01', 'PH')
				""", primexHospitalId.uuid())));
	}

	@Test
	@DisplayName("TC-006-61 | A setting saved through JPA is stored as real JSON and loads back with the same value and its author columns")
	void settingSurvivesSaveAndLoad() {
		FacilityContext context = FacilityContext.forFacility(primexHospitalId, currentUserId);

		// Given a true/false setting and a whole-number setting saved through the repository.
		List<UUID> ids = FacilityContext.callWithContext(context, () -> transaction.execute(_ -> {
			FacilitySetting gate = settingRepository.saveAndFlush(
					new FacilitySetting(primexHospitalId, FacilitySettingKey.GATE_LAB_REQUIRES_PAYMENT, BooleanNode.FALSE));
			FacilitySetting idleLock = settingRepository.saveAndFlush(
					new FacilitySetting(primexHospitalId, FacilitySettingKey.SESSION_IDLE_LOCK_MINUTES, IntNode.valueOf(15)));

			// Hibernate leaves the author columns to the database and reads them back after the insert.
			assertEquals(currentUserId, gate.getCreatedBy(), "created_by should be read back after the insert");
			assertNotNull(gate.getCreatedAt(), "created_at should be read back after the insert");
			return List.of(gate.getId(), idleLock.getId());
		}));

		FacilityContext.runWithContext(context, () -> transaction.executeWithoutResult(_ -> {
			// When the rows are read with plain SQL.
			// Then PostgreSQL holds a JSON boolean and a JSON number, not text or an object.
			assertEquals("boolean false", jdbcTemplate.queryForObject(
					"SELECT jsonb_typeof(value) || ' ' || value::text FROM facility_setting WHERE id = ?", String.class, ids.get(0)));
			assertEquals("number 15", jdbcTemplate.queryForObject(
					"SELECT jsonb_typeof(value) || ' ' || value::text FROM facility_setting WHERE id = ?", String.class, ids.get(1)));

			// When the same rows are loaded through JPA in a new transaction.
			FacilitySetting gate = settingRepository.findById(ids.get(0)).orElseThrow();
			FacilitySetting idleLock = settingRepository.findById(ids.get(1)).orElseThrow();

			// Then the keys and values come back exactly as saved.
			assertEquals(FacilitySettingKey.GATE_LAB_REQUIRES_PAYMENT, gate.getKey());
			JsonNode gateValue = gate.getValue();
			assertTrue(gateValue.isBoolean(), "Expected a boolean but got " + gateValue);
			assertFalse(gateValue.booleanValue());

			assertEquals(FacilitySettingKey.SESSION_IDLE_LOCK_MINUTES, idleLock.getKey());
			JsonNode idleValue = idleLock.getValue();
			assertTrue(idleValue.isInt(), "Expected a whole number but got " + idleValue);
			assertEquals(15, idleValue.intValue());

			// And the author columns load through the UserId converter.
			assertEquals(currentUserId, gate.getCreatedBy());
			assertEquals(currentUserId, gate.getUpdatedBy());
			assertEquals(0L, gate.getRowVersion());
		}));
	}
}