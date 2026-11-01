package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.EReachException;

/** An MFA method that can be chosen but is not built yet, such as email or passkey today (D-026). */
public class MfaMethodNotAvailableException extends EReachException {

	public MfaMethodNotAvailableException(String detail) {
		super(IamProblem.MFA_METHOD_NOT_AVAILABLE, detail);
	}
}
