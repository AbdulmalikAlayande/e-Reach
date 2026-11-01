package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.EReachException;

public class InvalidMfaCodeException extends EReachException {

    public InvalidMfaCodeException(String detail) {
        super(IamProblem.INVALID_MFA_CODE, detail);
    }
}