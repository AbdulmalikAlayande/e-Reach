package ng.ereach.facilityserver.iam.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the start screen's "set up a new facility server" path sends (D-031): the first platform operator.
 * A broken rule comes back as one VALIDATION_FAILED problem listing the fields, never their values.
 */
public record CreatePlatformOperatorRequest(
		@NotBlank
		String fullName,

		/* Stored as typed, unique ignoring case (D-021); the same pattern as V3. */
		@NotBlank
		@Pattern(regexp = "^[A-Za-z0-9._-]{3,40}$", message = "must be 3 to 40 letters, digits, dots, underscores or hyphens")
		String username,

		/* Required for every person (D-022); stored in lower case. */
		@NotBlank
		@Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "must be an email address")
		String email,

		/* Optional; international form, as V3 requires. */
		@Pattern(regexp = "^\\+[1-9][0-9]{6,14}$", message = "must be in international form, such as +2348031234567")
		String phone,

		/* At least 10 characters (PRD F2). */
		@NotNull
		@Size(min = 10, message = "must be at least 10 characters")
		String password,

		/* A 4 to 6 digit unlock PIN (PRD F2). */
		@NotNull
		@Pattern(regexp = "^[0-9]{4,6}$", message = "must be 4 to 6 digits")
		String pin
) {
}
