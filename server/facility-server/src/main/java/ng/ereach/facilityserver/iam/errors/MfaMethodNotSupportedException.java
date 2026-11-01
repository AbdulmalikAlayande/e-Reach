package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.EReachException;

public class MfaMethodNotSupportedException extends EReachException {

	public MfaMethodNotSupportedException(String detail) {
		super(IamProblem.MFA_METHOD_NOT_SUPPORTED, detail);
	}
}