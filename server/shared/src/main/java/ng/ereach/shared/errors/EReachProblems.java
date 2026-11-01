package ng.ereach.shared.errors;

import ng.ereach.shared.id.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;

/** Builds RFC 9457 problem details the same way everywhere. */
public final class EReachProblems {

	/** The PRD's stable code, an extension member on every problem. */
	public static final String CODE = "code";

	private EReachProblems() {
	}

	/** A problem of a known e-Reach type. */
	public static ProblemDetail create(ProblemType type, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(type.status()), detail);
		problem.setType(type.type());
		problem.setTitle(type.title());
		problem.setInstance(newInstance());
		problem.setProperty(CODE, type.code());
		return problem;
	}

	/** A problem from a thrown e-Reach exception, with its extension members. */
	public static ProblemDetail from(EReachException exception) {
		ProblemDetail problem = create(exception.problemType(), exception.detail());
		exception.extensions().forEach(problem::setProperty);
		return problem;
	}

	/**
	 * Completes a problem Spring built itself. Its type stays "about:blank" (RFC 9457, section 4.2.1),
	 * so its title is the HTTP status phrase and its code is HTTP_<status>.
	 */
	public static ProblemDetail complete(ProblemDetail problem) {
		HttpStatus status = HttpStatus.resolve(problem.getStatus());
		if (problem.getTitle() == null && status != null) {
			problem.setTitle(status.getReasonPhrase());
		}
		if (problem.getInstance() == null) {
			problem.setInstance(newInstance());
		}
		Map<String, Object> properties = problem.getProperties();
		if (properties == null || !properties.containsKey(CODE)) {
			problem.setProperty(CODE, "HTTP_" + problem.getStatus());
		}
		return problem;
	}

	/** The response: the status matches the body (RFC 9457, section 3.1.2), as application/problem+json. */
	public static ResponseEntity<ProblemDetail> toResponse(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problem);
	}

	/** RFC 9457, section 3.1.5: identifies this one occurrence; the same value is written to the log. */
	private static URI newInstance() {
		return URI.create("urn:uuid:" + Ids.newId());
	}
}