package ng.ereach.facilityserver.iam.repositories;

import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.shared.id.UserId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UserId> {


}
