package ng.ereach.facilityserver.iam.repositories;

import ng.ereach.facilityserver.iam.entities.MfaMethod;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Soft-deleted methods are never returned: MfaMethod carries @SoftDelete. */
public interface MfaMethodRepository extends JpaRepository<MfaMethod, UUID> {

    Optional<MfaMethod> findByUserAccountAndType(UserAccount account, MfaMethodType mfaMethodType);

    List<MfaMethod> findByUserAccount(UserAccount account);

    /** Whether a live platform operator has confirmed an MFA method, which closes the set-up step (D-024). */
    boolean existsByUserAccountPlatformOperatorTrueAndConfirmedAtIsNotNull();
}
