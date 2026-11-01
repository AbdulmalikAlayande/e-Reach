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
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

import static ng.ereach.facilityserver.iam.entities.enums.MfaMethodType.TOTP;
import static ng.ereach.facilityserver.iam.services.AuthenticatorSecretGenerator.*;

/**
 * The authenticator app (TOTP): SHA-1, 6 digits, 30-second steps, one step of drift, no step used twice; the
 * secret is stored only encrypted (D-014). Runs inside the caller's transaction.
 */
@Component
class AuthenticatorAppHandler implements MfaMethodHandler {

	/** The name the authenticator app shows above the account (transcript of 01/11/2026). */
	private static final String ISSUER = "e-Reach";

	private final MfaMethodRepository mfaMethodRepository;
	private final MfaSecretCipher secretCipher;
	private final Clock clock;

	AuthenticatorAppHandler(MfaMethodRepository mfaMethodRepository, MfaSecretCipher secretCipher, Clock clock) {
		this.mfaMethodRepository = mfaMethodRepository;
		this.secretCipher = secretCipher;
		this.clock = clock;
	}

	@Override
	public MfaMethodType type() {
		return TOTP;
	}

	/** A new secret each time; a pending method is reused, so a user never has two (D-014). */
	@Override
	public MfaResponse setup(UserAccount account) {
		MfaMethod method = mfaMethodRepository.findByUserAccountAndType(account, TOTP).orElse(null);
		if (method != null && method.isConfirmed()) {
			throw new MfaAlreadyConfiguredException("An authenticator app is already set up for this account.");
		}
		if (method == null) {
			method = new MfaMethod(account, TOTP);
		}

		byte[] secret = generateSecret(DEFAULT_ALGORITHM);
		method.setSecretEncrypted(secretCipher.encrypt(secret));
		mfaMethodRepository.save(method);

		// The label is the username (decision A1 of ER-007 part 1); the URI and the image hold the secret,
		// so neither is ever logged.
		String manualEntryKey = encodeBase32NoPadding(secret);
		String provisioningUri = buildOtpAuthUri(ISSUER, account.getUsername(), manualEntryKey);
		return MfaResponse.totpSetup(AuthenticatorQrCode.toPngDataUri(provisioningUri), manualEntryKey);
	}

	@Override
	public MfaResponse confirm(UserAccount account, String code) {
		MfaMethod method = mfaMethodRepository.findByUserAccountAndType(account, TOTP)
				.orElseThrow(() -> new RecordNotFoundException("No authenticator app has been set up for this account."));
		if (method.isConfirmed()) {
			throw new MfaAlreadyConfiguredException("The authenticator app is already confirmed for this account.");
		}

		Instant now = Instant.now(clock);
		method.confirm(matchingStep(method, code, now), now);
		mfaMethodRepository.save(method);
		return MfaResponse.confirmed(TOTP);
	}

	@Override
	public MfaResponse verify(UserAccount account, String code) {
		MfaMethod method = mfaMethodRepository.findByUserAccountAndType(account, TOTP)
				.orElseThrow(() -> new RecordNotFoundException("No authenticator app has been set up for this account."));
		if (!method.isConfirmed()) {
			throw new MfaMethodNotConfirmedException("Finish setting up the authenticator app before using it.");
		}

		Instant now = Instant.now(clock);
		method.recordUse(matchingStep(method, code, now), now);
		mfaMethodRepository.save(method);
		return MfaResponse.verified(TOTP);
	}

	private long matchingStep(MfaMethod method, String code, Instant now) {
		byte[] secret = secretCipher.decrypt(method.getSecretEncrypted());
		return verifyCode(secret, code, now.getEpochSecond(), method.lastUsedStepOrNone())
				.orElseThrow(() -> new InvalidMfaCodeException("The code is wrong, or has already been used. Enter the current code from your app."));
	}
}
