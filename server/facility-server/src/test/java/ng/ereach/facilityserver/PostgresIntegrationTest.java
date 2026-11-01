package ng.ereach.facilityserver;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Base class for every test that needs a real PostgreSQL database.
 *
 * <p>One container is started for the whole test run and shared by all test classes,
 * because starting PostgreSQL takes seconds and every test class would otherwise pay
 * for it again.
 *
 * <p>The application connects as {@code ereach_app} and Flyway as {@code ereach_owner},
 * exactly as in production. We do not use {@code @ServiceConnection}: it would connect
 * as the container's superuser, and PostgreSQL never applies row-level security to a
 * superuser, so tenancy tests would pass when they should fail.
 */
public abstract class PostgresIntegrationTest {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
			.withDatabaseName("ereach")
			.withInitScript("ereach-test-roles.sql");

	/** A fresh MFA encryption key for this test run, so no real key is ever in the repository (D-014). */
	static final String MFA_ENCRYPTION_KEY = newMfaEncryptionKey();

	static {
		POSTGRES.start();
	}

	private static String newMfaEncryptionKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "ereach_app");
		registry.add("spring.datasource.password", () -> "ereach_app");
		registry.add("spring.flyway.user", () -> "ereach_owner");
		registry.add("spring.flyway.password", () -> "ereach_owner");
		registry.add("ereach.mfa.encryption-key", () -> MFA_ENCRYPTION_KEY);
	}
}
