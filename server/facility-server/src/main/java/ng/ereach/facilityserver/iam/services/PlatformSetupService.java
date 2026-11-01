package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.CreatePlatformOperatorRequest;
import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.dtos.UserAccountResponse;

/**
 * The "set up a new facility server" path of the start screen (item 1, D-008, D-024, D-029, D-031): creates the
 * first platform operator and sets up their MFA. It works only while the server has no confirmed operator; once
 * the operator's first MFA code is confirmed, every step answers 404 for good.
 */
public interface PlatformSetupService {

	/**
	 * Creates the first operator. An earlier run that never confirmed MFA is replaced: its account and pending
	 * method are soft-deleted first, so the same username and email can be used again (D-024). A username already
	 * held, ignoring case, is 409 USERNAME_TAKEN (D-025).
	 */
	UserAccountResponse createPlatformOperator(CreatePlatformOperatorRequest request);

	/** Starts, or restarts, the operator's MFA setup with the method they chose (D-026). */
	MfaResponse startMfaSetup(String method);

	/** Confirms the operator's first MFA proof, which finishes and closes the set-up step (D-024). */
	MfaResponse confirmMfa(String method, String code);
}
