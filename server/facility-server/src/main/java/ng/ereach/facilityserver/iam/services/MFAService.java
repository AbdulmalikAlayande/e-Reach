package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.entities.UserAccount;

/**
 * MFA steps for one account, whatever the method (D-026). The method is the word the client sends: an unknown
 * one is 400 MFA_METHOD_NOT_SUPPORTED, and a known one that is not built yet, such as email or passkey today,
 * is 501 MFA_METHOD_NOT_AVAILABLE. Every step runs inside the caller's transaction.
 */
public interface MFAService {

    /** Starts, or restarts, setup of the method; it stays pending until confirmed. */
    MfaResponse setupMfa(UserAccount account, String method);

    /** Checks the first proof, which finishes setup of the method. */
    MfaResponse confirmMfa(UserAccount account, String method, String code);

    /** Checks a proof at sign-in or step-up. */
    MfaResponse verifyMfa(UserAccount account, String method, String code);
}
