package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.ProblemType;

/** Problems only iam reports. */
public enum IamProblem implements ProblemType {

	MFA_METHOD_NOT_SUPPORTED(400, "This MFA method is not supported"),
	MFA_ALREADY_CONFIGURED(409, "This MFA method is already set up"),
	MFA_NOT_CONFIRMED(409, "This MFA method has not finished setup"),
	INVALID_MFA_CODE(422, "The MFA code is not valid"),
	/** An MFA method that exists but is not built yet (D-026). */
	MFA_METHOD_NOT_AVAILABLE(501, "This MFA method is not available yet"),
	/** Only where someone chooses a username; sign-in never says this (D-025). */
	USERNAME_TAKEN(409, "This username is not available");

	private final int status;
	private final String title;

	IamProblem(int status, String title) {
		this.status = status;
		this.title = title;
	}

	@Override public String code() { return name(); }
	@Override public int status() { return status; }
	@Override public String title() { return title; }
}