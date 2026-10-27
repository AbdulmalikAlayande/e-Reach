package ng.ereach.facilityserver.platform.repositories;

import ng.ereach.facilityserver.platform.entities.Facility;
import ng.ereach.shared.id.FacilityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityRepository extends JpaRepository<Facility, FacilityId> {
}
