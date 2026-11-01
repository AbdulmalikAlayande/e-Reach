package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;

/**
 * One MFA method (D-026). {@link MFAService} picks the handler for the method a user chose, so email codes and
 * passkeys plug in as new handlers without changing the flow. A method with no handler answers 501.
 */
public interface MfaMethodHandler {

	/** The method this handler serves. */
	MfaMethodType type();

	/** Starts or restarts setup; the method stays pending until confirmed. */
	MfaResponse setup(UserAccount account);

	/** Checks the first proof, which finishes setup. */
	MfaResponse confirm(UserAccount account, String code);

	/** Checks a proof at sign-in or step-up. */
	MfaResponse verify(UserAccount account, String code);
}
