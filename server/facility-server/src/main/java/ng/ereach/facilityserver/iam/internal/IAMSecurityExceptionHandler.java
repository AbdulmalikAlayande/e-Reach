package ng.ereach.facilityserver.iam.internal;

import ng.ereach.shared.errors.CommonProblem;
import ng.ereach.shared.errors.EReachProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IAMSecurityExceptionHandler {

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied() {
		return EReachProblems.toResponse(EReachProblems.create(CommonProblem.ACCESS_DENIED,
				"Your roles in this facility do not allow this action."));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication() {
		return EReachProblems.toResponse(EReachProblems.create(CommonProblem.AUTHENTICATION_REQUIRED,
				"Sign in, then try again."));
	}
}