package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.entities.MfaMethod;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;
import ng.ereach.facilityserver.iam.errors.InvalidMfaCodeException;
import ng.ereach.facilityserver.iam.errors.MfaAlreadyConfiguredException;
import ng.ereach.facilityserver.iam.errors.MfaMethodNotConfirmedException;
import ng.ereach.facilityserver.iam.repositories.MfaMethodRepository;
import ng.ereach.facilityserver.iam.security.MfaSecretCipher;
import ng.ereach.shared.errors.RecordNotFoundException;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static ng.ereach.facilityserver.iam.entities.enums.MfaMethodType.TOTP;
import static ng.ereach.facilityserver.iam.services.AuthenticatorSecretGenerator.*;

public class MFAService {

    private static final String ISSUER = "e-Reach";
    private final MfaMethodRepository mfaMethodRepository;
    private final MfaSecretCipher secretCipher;
    private final Clock clock;

    public MFAService(MfaMethodRepository mfaMethodRepository,
                      MfaSecretCipher secretCipher,
                      Clock clock) {
        this.mfaMethodRepository = mfaMethodRepository;
        this.secretCipher = secretCipher;
        this.clock = clock;
    }

    public MfaResponse setupMfa(UserAccount account, String method, @Nullable String email) {
        MfaMethodType type = MfaMethodType.fromMethodType(method);
        return switch (type) {
            case TOTP -> handleAppTotpMFASetup(account);
            case EMAIL -> handleEmailMFASetup(account, email);
            case PASSKEY -> handlePasskeyMFASetup(account);
        };
    }

    private MfaResponse handleEmailMFASetup(UserAccount account, @Nullable String email) {
        throw new UnsupportedOperationException("Email MFA setup is not implemented yet");
    }

    private MfaResponse handlePasskeyMFASetup(UserAccount account) {
        throw new UnsupportedOperationException("Passkey MFA setup is not implemented yet");
    }

    private MfaResponse handleAppTotpMFASetup(UserAccount account) {

        Optional<MfaMethod> optionalMfaMethod = mfaMethodRepository.findByUserAccountAndType(account, TOTP);
        if (optionalMfaMethod.isPresent() && optionalMfaMethod.get().isConfirmed()) {
            throw new MfaAlreadyConfiguredException("An authenticator app is already set up for this account.");
        }

        MfaMethod existingMfaMethod = optionalMfaMethod.orElseGet(() -> new MfaMethod(account, TOTP));

        byte[] secret = generateSecret(DEFAULT_ALGORITHM);
        existingMfaMethod.setSecretEncrypted(secretCipher.encrypt(secret));
        mfaMethodRepository.save(existingMfaMethod);
        String base32Secret = encodeBase32NoPadding(secret);
        String otpAuthUri = buildOtpAuthUri(ISSUER, account.getEmail(), base32Secret);
        return MfaResponse.totpSetup("", "");
    }



    public MfaResponse confirmMfa(UserAccount account, String method, String email, String code){
        MfaMethodType type = MfaMethodType.fromMethodType(method);
        return switch (type) {
            case TOTP -> handleAppTotpMFAConfirmation(account, code);
            case EMAIL -> handleEmailMFAConfirmation(account, email);
            case PASSKEY -> handlePasskeyConfirmation(account);
        };
    }

    private MfaResponse handleAppTotpMFAConfirmation(UserAccount account, String code) {
        MfaMethod mfaMethod = mfaMethodRepository.findByUserAccountAndType(account, TOTP)
                .orElseThrow(() -> new RecordNotFoundException("No authenticator app has been set up for this account."));

        if (mfaMethod.isConfirmed()) {
            throw new MfaAlreadyConfiguredException("The authenticator app is already confirmed for this account.");
        }

        Instant now = Instant.now(clock);
        byte[] secret = secretCipher.decrypt(mfaMethod.getSecretEncrypted());
        long matchedStep =  verifyCode(secret, code, now.getEpochSecond(), mfaMethod.lastUsedStepOrNone())
                .orElseThrow(() -> new InvalidMfaCodeException("The code is wrong, or has already been used. Enter the current code from your app."));

        mfaMethod.confirm(matchedStep, now);
        mfaMethodRepository.save(mfaMethod);
        return MfaResponse.confirmed(TOTP);
    }

    private MfaResponse handleEmailMFAConfirmation(UserAccount account, String code) {
        throw new UnsupportedOperationException("Email MFA confirmation is not implemented yet");
    }

    private MfaResponse handlePasskeyConfirmation(UserAccount account) {
        throw new UnsupportedOperationException("Passkey MFA confirmation is not implemented yet");
    }

    public MfaResponse verifyMfa(UserAccount account, String method, String code) {
        MfaMethodType type = MfaMethodType.fromMethodType(method);
        return switch (type) {
            case TOTP -> handleAppTotpMFAVerification(account, code);
            case EMAIL -> handleEmailMFAVerification(account, code);
            case PASSKEY -> handlePasskeyVerification(account);
        };
    }

    private MfaResponse handleAppTotpMFAVerification(UserAccount account, String code) {
        MfaMethod mfaMethod = mfaMethodRepository.findByUserAccountAndType(account, TOTP)
                .orElseThrow(() -> new RecordNotFoundException("No authenticator app has been set up for this account."));

        if (!mfaMethod.isConfirmed()) {
            throw new MfaMethodNotConfirmedException("Finish setting up the authenticator app before using it.");
        }

        Instant now = Instant.now(clock);
        byte[] secret = secretCipher.decrypt(mfaMethod.getSecretEncrypted());
        long matchedStep =  verifyCode(secret, code, now.getEpochSecond(), mfaMethod.lastUsedStepOrNone())
                .orElseThrow(() -> new InvalidMfaCodeException("The code is wrong, or has already been used. Enter the current code from your app."));

        mfaMethod.recordUse(matchedStep, now);
        mfaMethodRepository.save(mfaMethod);
        return MfaResponse.verified(TOTP);
    }

    private MfaResponse handleEmailMFAVerification(UserAccount account, String code) {
        throw new UnsupportedOperationException("Email MFA verification is not implemented yet");
    }

    private MfaResponse handlePasskeyVerification(UserAccount account) {
        throw new UnsupportedOperationException("Passkey MFA verification is not implemented yet");
    }

}
