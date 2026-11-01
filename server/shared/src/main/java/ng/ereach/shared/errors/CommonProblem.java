package ng.ereach.shared.errors;

/** Problems that any module, in either server, can report. */
public enum CommonProblem implements ProblemType {

	VALIDATION_FAILED(400, "The request has invalid fields"),
	MALFORMED_REQUEST(400, "The request body could not be read"),
	AUTHENTICATION_REQUIRED(401, "Sign-in is required"),
	ACCESS_DENIED(403, "This action is not allowed"),
	NOT_FOUND(404, "The requested record does not exist"),

	// RFC 9110, section 13.1.1: a failed If-Match is 412. RFC 6585, section 3: a missing one is 428.
	STALE_RECORD(412, "Someone else changed this record first"),
	PRECONDITION_REQUIRED(428, "An If-Match header is required"),

	// IETF draft "The Idempotency-Key HTTP Header Field" (ER-005).
	IDEMPOTENCY_KEY_MISSING(400, "An Idempotency-Key header is required"),
	IDEMPOTENCY_KEY_REUSED(422, "This idempotency key was already used for a different request"),
	IDEMPOTENCY_REQUEST_IN_PROGRESS(409, "A request with this idempotency key is still being processed"),

	INTERNAL_ERROR(500, "Something went wrong on the server");

	private final int status;
	private final String title;

	CommonProblem(int status, String title) {
		this.status = status;
		this.title = title;
	}

	@Override public String code() { return name(); }
	@Override public int status() { return status; }
	@Override public String title() { return title; }
}