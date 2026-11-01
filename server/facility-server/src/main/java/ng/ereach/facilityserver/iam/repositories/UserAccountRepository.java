package ng.ereach.facilityserver.iam.repositories;

import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.shared.id.UserId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Soft-deleted accounts are never returned: UserAccount carries @SoftDelete. */
public interface UserAccountRepository extends JpaRepository<UserAccount, UserId> {

    /** Whether a live account already holds this username, ignoring case (D-021). */
    boolean existsByUsernameIgnoreCase(String username);

    /** Every live platform operator, confirmed or not. */
    List<UserAccount> findByPlatformOperatorTrue();
}
