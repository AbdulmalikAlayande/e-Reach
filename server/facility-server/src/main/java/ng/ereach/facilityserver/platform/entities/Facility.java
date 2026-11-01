package ng.ereach.facilityserver.platform.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import ng.ereach.facilityserver.platform.entities.enums.FacilityStatus;
import ng.ereach.facilityserver.platform.entities.enums.FacilityType;
import ng.ereach.shared.converters.FacilityIdConverter;
import ng.ereach.shared.entities.BaseEntity;
import ng.ereach.shared.id.FacilityId;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Facility extends BaseEntity {

    @Id
    @Convert(converter = FacilityIdConverter.class)
    private FacilityId id;

    private String name;

    private FacilityType type;

    @Column(name = "state_code", nullable = false)
    private String state;

    @Column(name = "lga_code", nullable = false)
    private String LGA;

    private String address;

    private FacilityStatus status = FacilityStatus.ACTIVE;

    private String phoneNumber;
    private String nhfrCode;
    private String stateRegistrationNumber;
    private String stateRegistrationAgency;
    private String hospitalNumberPrefix;

    protected Facility() {}

    public Facility(String name, FacilityType type, String state, String LGA, String hospitalNumberPrefix) {
        assignId();
        this.name = name;
        this.type = type;
        this.state = state;
        this.LGA = LGA;
        this.hospitalNumberPrefix = hospitalNumberPrefix;
    }

    private void assignId(){
        if (id == null) {
            id = FacilityId.newId();
        }
    }
}
