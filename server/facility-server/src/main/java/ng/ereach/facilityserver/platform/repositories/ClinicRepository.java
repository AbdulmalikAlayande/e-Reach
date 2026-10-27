package ng.ereach.facilityserver.platform.repositories;

import ng.ereach.facilityserver.platform.entities.Clinic;
import ng.ereach.shared.id.ClinicId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, ClinicId> {
}
