package ng.ereach.facilityserver.iam.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

/** The sign-in side of the facility server: who may call what, how secrets are hashed, and the clock. */
@Configuration
class IamConfiguration {

	/**
	 * Only the "set up a new facility server" path is open without sign-in, and it closes itself once the first
	 * operator is confirmed (D-031). Everything else needs a signed-in user; sign-in itself is items 2 to 4.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(requests -> requests
						.requestMatchers("/api/v1/setup/**").permitAll()
						.anyRequest().authenticated())
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/** Passwords and PINs are hashed with Argon2id (ADR-007). Needs BouncyCastle on the classpath. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

	/** The server's clock is the only clock that orders events (PRD rule 9); tests replace it with a fixed one. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
