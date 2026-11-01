package ng.ereach.facilityserver.iam.dtos;

import jakarta.validation.constraints.NotBlank;

/** Starts setup of one MFA method: totp, email or passkey (D-026). */
public record MfaSetupRequest(@NotBlank String method) {
}
