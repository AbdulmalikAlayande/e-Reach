package ng.ereach.facilityserver.platform.repositories;

import ng.ereach.facilityserver.platform.entities.FacilitySetting;
import ng.ereach.facilityserver.platform.entities.enums.FacilitySettingKey;
import ng.ereach.shared.id.FacilityId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FacilitySettingRepository extends JpaRepository<FacilitySetting, UUID> {
    Optional<FacilitySetting> findByFacilityIdAndKey(FacilityId facilityId, FacilitySettingKey key);
}