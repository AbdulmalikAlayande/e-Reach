package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.EReachException;

public class MfaMethodNotConfirmedException extends EReachException {

    public MfaMethodNotConfirmedException(String detail) {
        super(IamProblem.MFA_NOT_CONFIRMED, detail);
    }
}