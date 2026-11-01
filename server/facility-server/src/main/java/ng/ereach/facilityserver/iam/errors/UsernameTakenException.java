package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.EReachException;

/**
 * A username already held by a live account, ignoring case. The detail never repeats the username (D-025).
 */
public class UsernameTakenException extends EReachException {

	public UsernameTakenException() {
		super(IamProblem.USERNAME_TAKEN, "That username is not available. Choose another.");
	}
}
