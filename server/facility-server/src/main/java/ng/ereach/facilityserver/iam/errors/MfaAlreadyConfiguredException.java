package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.EReachException;

public class MfaAlreadyConfiguredException extends EReachException {

    public MfaAlreadyConfiguredException(String detail) {
        super(IamProblem.MFA_ALREADY_CONFIGURED, detail);
    }
}