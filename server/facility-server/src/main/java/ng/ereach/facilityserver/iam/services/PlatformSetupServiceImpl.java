package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.CreatePlatformOperatorRequest;
import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.dtos.UserAccountResponse;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.errors.UsernameTakenException;
import ng.ereach.facilityserver.iam.repositories.MfaMethodRepository;
import ng.ereach.facilityserver.iam.repositories.UserAccountRepository;
import ng.ereach.facilityserver.platform.FacilityContext;
import ng.ereach.shared.errors.RecordNotFoundException;
import ng.ereach.shared.id.SystemActors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Each step runs as the INSTALLER system actor (ADR-018), in one transaction that first takes a PostgreSQL
 * advisory lock, so two set-up attempts at the same moment run one after the other and leave one operator.
 */
@Service
class PlatformSetupServiceImpl implements PlatformSetupService {

	/** The advisory lock every set-up step takes; any fixed number that nothing else uses. */
	private static final long SETUP_LOCK = 0x45_52_45_41_43_48_31L; // "EREACH1"

	private final UserAccountRepository userAccountRepository;
	private final MfaMethodRepository mfaMethodRepository;
	private final MFAService mfaService;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate transaction;
	private final JdbcTemplate jdbcTemplate;
	private final Clock clock;

	PlatformSetupServiceImpl(UserAccountRepository userAccountRepository, MfaMethodRepository mfaMethodRepository,
								MFAService mfaService, PasswordEncoder passwordEncoder, TransactionTemplate transaction,
								JdbcTemplate jdbcTemplate, Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.mfaMethodRepository = mfaMethodRepository;
		this.mfaService = mfaService;
		this.passwordEncoder = passwordEncoder;
		this.transaction = transaction;
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
	}

	@Override
	public UserAccountResponse createPlatformOperator(CreatePlatformOperatorRequest request) {
		return asInstaller(() -> {
			List<UserAccount> unconfirmed = userAccountRepository.findByPlatformOperatorTrue();
			for (UserAccount abandoned : unconfirmed) {
				mfaMethodRepository.deleteAll(mfaMethodRepository.findByUserAccount(abandoned));
				userAccountRepository.delete(abandoned);
			}
			// Soft deletes run after inserts when Hibernate flushes, so flush now, before the new account
			// takes the same username and email.
			userAccountRepository.flush();

			if (userAccountRepository.existsByUsernameIgnoreCase(request.username())) {
				throw new UsernameTakenException();
			}

			UserAccount operator = new UserAccount(request.username(), request.fullName().trim(), request.email(),
					blankToNull(request.phone()), passwordEncoder.encode(request.password()), passwordEncoder.encode(request.pin()));
			operator.makePlatformOperator();
			operator.setMfaRequiredBy(Instant.now(clock)); // an elevated role needs MFA at once (D-028)
			return UserAccountResponse.fromEntity(userAccountRepository.saveAndFlush(operator));
		});
	}

	@Override
	public MfaResponse startMfaSetup(String method) {
		return asInstaller(() -> mfaService.setupMfa(unconfirmedOperator(), method));
	}

	@Override
	public MfaResponse confirmMfa(String method, String code) {
		return asInstaller(() -> mfaService.confirmMfa(unconfirmedOperator(), method, code));
	}

	/** Runs one step as the INSTALLER, in one transaction, after the lock and the "still open" check. */
	private <T> T asInstaller(Supplier<T> step) {
		return FacilityContext.callWithContext(FacilityContext.forUserOnly(SystemActors.INSTALLER),
				() -> transaction.execute(_ -> {
					jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", resultSet -> null, SETUP_LOCK);
					if (mfaMethodRepository.existsByUserAccountPlatformOperatorTrueAndConfirmedAtIsNotNull()) {
						throw new RecordNotFoundException("This server is already set up.");
					}
					return step.get();
				}));
	}

	private UserAccount unconfirmedOperator() {
		return userAccountRepository.findByPlatformOperatorTrue().stream().findFirst()
				.orElseThrow(() -> new RecordNotFoundException("Create the platform operator first."));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
