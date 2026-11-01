package ng.ereach.facilityserver.iam.errors;

import ng.ereach.shared.errors.ProblemType;

/** Problems only iam reports. */
public enum IamProblem implements ProblemType {

	MFA_METHOD_NOT_SUPPORTED(400, "This MFA method is not supported"),
	MFA_ALREADY_CONFIGURED(409, "This MFA method is already set up"),
	MFA_NOT_CONFIRMED(409, "This MFA method has not finished setup"),
	INVALID_MFA_CODE(422, "The MFA code is not valid");

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