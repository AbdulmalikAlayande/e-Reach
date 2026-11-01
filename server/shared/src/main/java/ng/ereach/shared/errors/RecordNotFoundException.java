package ng.ereach.shared.errors;

/** A record that does not exist, or that the user may not know exists (PRD section 10: 404). */
public class RecordNotFoundException extends EReachException {

	public RecordNotFoundException(String detail) {
		super(CommonProblem.NOT_FOUND, detail);
	}
}