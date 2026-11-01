package ng.ereach.shared.errors.web;

import ng.ereach.shared.errors.CommonProblem;
import ng.ereach.shared.errors.EReachException;
import ng.ereach.shared.errors.EReachProblems;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns every exception from a controller into an RFC 9457 problem. It runs last, so a module's
 * own handler (for example identity and access management, for Spring Security) is asked first.
 */
@RestControllerAdvice
@Order(value = Ordered.LOWEST_PRECEDENCE)
public class EReachExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(EReachExceptionHandler.class);

	/** One invalid field. Only the field name and the rule it broke: never the value, which may be patient data. */
	public record InvalidField(String field, String message) {
	}

	@ExceptionHandler(EReachException.class)
	public ResponseEntity<ProblemDetail> handleEReachException(EReachException exception) {
		ProblemDetail problem = EReachProblems.from(exception);
		record(problem, exception);
		return EReachProblems.toResponse(problem);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ProblemDetail> handleStaleRecord(OptimisticLockingFailureException exception) {
		ProblemDetail problem = EReachProblems.create(CommonProblem.STALE_RECORD,
				"Reload the record and make your change again.");
		record(problem, exception);
		return EReachProblems.toResponse(problem);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
		ProblemDetail problem = EReachProblems.create(CommonProblem.INTERNAL_ERROR, "The error was recorded.");
		problem.setDetail("The error was recorded with reference " + problem.getInstance()
				+ ". Try again, and quote the reference if it keeps happening.");
		record(problem, exception);
		return EReachProblems.toResponse(problem);
	}

	/** A failed @Valid: one problem, with every invalid field listed (RFC 9457, section 3). */
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception, @NonNull HttpHeaders headers,
			@NonNull HttpStatusCode status, @NonNull WebRequest request) {
		List<InvalidField> errors = exception.getBindingResult().getAllErrors().stream()
				.map(error -> new InvalidField(
						error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
						String.valueOf(error.getDefaultMessage())))
				.toList();

		ProblemDetail problem = EReachProblems.create(CommonProblem.VALIDATION_FAILED,
				errors.size() == 1 ? "1 field is invalid." : errors.size() + " fields are invalid.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(exception, problem, headers, HttpStatusCode.valueOf(problem.getStatus()), request);
	}

	/** Unreadable JSON. The parser's own message is not sent, because it can echo what the client typed. */
	@Override
	protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException exception, @NonNull HttpHeaders headers,
			@NonNull HttpStatusCode status, @NonNull WebRequest request) {
		ProblemDetail problem = EReachProblems.create(CommonProblem.MALFORMED_REQUEST,
				"The request body is not valid JSON, or does not have the expected shape.");
		return handleExceptionInternal(exception, problem, headers, HttpStatusCode.valueOf(problem.getStatus()), request);
	}

	/** Every problem Spring raises itself passes through here: give it a code and an instance. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception exception, @Nullable Object body, @NonNull HttpHeaders headers,
			@NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			EReachProblems.complete(problem);
			record(problem, exception);
		}
		return super.handleExceptionInternal(exception, body, headers, statusCode, request);
	}

	private static void record(ProblemDetail problem, Exception exception) {
		if (problem.getStatus() >= 500) {
			log.error("Problem {} {}", problem.getInstance(), problem.getProperties(), exception);
		} else {
			log.debug("Problem {} {}: {}", problem.getInstance(), problem.getStatus(), exception.toString());
		}
	}
}