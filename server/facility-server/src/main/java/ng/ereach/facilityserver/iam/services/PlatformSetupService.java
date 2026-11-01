package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.dtos.UserAccountResponse;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountKind;
import ng.ereach.facilityserver.iam.repositories.UserAccountRepository;
import ng.ereach.shared.errors.RecordNotFoundException;
import ng.ereach.shared.id.UserId;

import java.time.Instant;

public class PlatformSetupService {

    private final MFAService mfaService;
    private final UserAccountRepository userAccountRepository;

    public PlatformSetupService(UserAccountRepository userAccountRepository, MFAService mfaService){
        this.mfaService = mfaService;
        this.userAccountRepository = userAccountRepository;
    }

    public UserAccountResponse createPlatformOperator(String username, String fullName,  String phone, String password, String pin) {
        UserAccount userAccount = new UserAccount(UserAccountKind.PERSON, username, fullName, phone, password, pin);
        userAccount.setMfaRequiredBy(Instant.now());
        UserAccount savedEntity = userAccountRepository.save(userAccount);
        return UserAccountResponse.fromEntity(savedEntity);
    }

    public MfaResponse enrolMfa(UserId userId, String method, String email) {
        MfaResponse mfaSetupResponse = mfaService.setupMfa(findAccount(userId), method, email);
        // event logging and other things
        return mfaSetupResponse;
    }

    private UserAccount findAccount(UserId userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new RecordNotFoundException(String.format("No user account with ID %s.", userId)));
    }
}
