package ng.ereach.facilityserver.iam;

import ng.ereach.facilityserver.PostgresIntegrationTest;
import ng.ereach.shared.id.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-007-67 of ER-007, part 1: a server started without a setup code has no setup step at all (D-029).
 * It needs its own application context, because every other setup case runs with a code configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ereach.setup.code=")
class PlatformOperatorSetupClosedTests extends PostgresIntegrationTest {

	@Value("${spring.datasource.url}")
	String databaseUrl;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void startFromANewServer() {
		// As the table owner: keep only the four system actors (D-017, D-030).
		JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "ereach_owner", "ereach_owner"));
		owner.execute("TRUNCATE mfa_challenge, mfa_method, practitioner_profile, role_assignment, facility_membership, "
				+ "user_security_key, facility_setting, clinic, facility, lga, state");
		owner.update("DELETE FROM user_account WHERE kind = 'person'");
	}

	/**
	 * <p>Level: API</p>
	 * <p>Traces to: D-029 (without EREACH_SETUP_CODE the setup endpoints do not exist)</p>
	 */
	@Test
	@DisplayName("TC-007-67 | With no setup code configured, the setup step does not exist")
	void withoutAConfiguredCodeTheStepDoesNotExist() throws Exception {
		// Given a server started with ereach.setup.code blank.
		String body = """
				{"fullName": "Amina Bello", "username": "amina.bello", "email": "amina.bello@primexhospital.ng",
				 "phone": "+2348031234567", "password": "Ikeja-General-26", "pin": "4821"}
				""";

		// When the step runs with any code.
		mockMvc.perform(post("/api/v1/setup/platform-operator").contentType(MediaType.APPLICATION_JSON).content(body)
						.header("X-Setup-Code", "ANYCODE").header("Idempotency-Key", Ids.newId().toString()).with(csrf()))
				// Then it is not found, and nothing is saved.
				.andExpect(status().isNotFound());
		assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE kind = 'person'", Long.class));
	}
}
