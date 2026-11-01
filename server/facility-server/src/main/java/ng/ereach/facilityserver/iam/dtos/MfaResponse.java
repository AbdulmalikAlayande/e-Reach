package ng.ereach.facilityserver.iam.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MfaResponse(
        String method,
        Status status,
        @Nullable String qrCodeImage,
        @Nullable String manualEntryKey,
        @Nullable String codeSentTo,
        @Nullable Instant codeExpiresAt,
        @JsonRawValue @Nullable String passkeyOptions
) {

    public enum Status {
        PENDING_CONFIRMATION,
        CHALLENGE_ISSUED,
        CONFIRMED,
        VERIFIED
    }

    public static MfaResponse totpSetup(String qrCodeImage, String manualEntryKey) {
        return new MfaResponse(MfaMethodType.TOTP.getMethodType(), Status.PENDING_CONFIRMATION,
                qrCodeImage, manualEntryKey, null, null, null);
    }

    public static MfaResponse emailSetup(String maskedEmail, Instant codeExpiresAt) {
        return new MfaResponse(MfaMethodType.EMAIL.getMethodType(), Status.PENDING_CONFIRMATION,
                null, null, maskedEmail, codeExpiresAt, null);
    }

    public static MfaResponse passkeySetup(String creationOptionsJson) {
        return new MfaResponse(MfaMethodType.PASSKEY.getMethodType(), Status.PENDING_CONFIRMATION,
                null, null, null, null, creationOptionsJson);
    }

    public static MfaResponse emailChallenge(String maskedEmail, Instant codeExpiresAt) {
        return new MfaResponse(MfaMethodType.EMAIL.getMethodType(), Status.CHALLENGE_ISSUED,
                null, null, maskedEmail, codeExpiresAt, null);
    }

    public static MfaResponse passkeyChallenge(String requestOptionsJson) {
        return new MfaResponse(MfaMethodType.PASSKEY.getMethodType(), Status.CHALLENGE_ISSUED,
                null, null, null, null, requestOptionsJson);
    }

    public static MfaResponse confirmed(MfaMethodType type) {
        return new MfaResponse(type.getMethodType(), Status.CONFIRMED,
                null, null, null, null, null);
    }

    public static MfaResponse verified(MfaMethodType type) {
        return new MfaResponse(type.getMethodType(), Status.VERIFIED,
                null, null, null, null, null);
    }
}