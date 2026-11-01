package ng.ereach.facilityserver.iam.dtos;

import jakarta.validation.constraints.NotBlank;

/** Confirms an MFA method with its first proof, such as the current authenticator code. */
public record MfaConfirmRequest(@NotBlank String method, @NotBlank String code) {
}
