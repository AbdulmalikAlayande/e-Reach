package ng.ereach.shared.config;

import ng.ereach.shared.errors.web.EReachExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Gives every Spring MVC application that depends on shared the same problem handling. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

	@Bean
	public EReachExceptionHandler eReachProblemHandler() {
		return new EReachExceptionHandler();
	}
}