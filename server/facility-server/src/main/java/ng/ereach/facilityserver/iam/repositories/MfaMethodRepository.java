package ng.ereach.facilityserver.iam.repositories;

import ng.ereach.facilityserver.iam.entities.MfaMethod;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MfaMethodRepository extends JpaRepository<MfaMethod, UUID> {

    Optional<MfaMethod> findByUserAccountAndType(UserAccount account, MfaMethodType mfaMethodType);
}
