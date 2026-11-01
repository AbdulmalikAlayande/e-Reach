package ng.ereach.facilityserver.platform.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import ng.ereach.facilityserver.platform.converters.ClinicIdConverter;
import ng.ereach.facilityserver.platform.entities.enums.ClinicStatus;
import ng.ereach.shared.entities.BaseEntity;
import ng.ereach.shared.id.ClinicId;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;


@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Clinic extends BaseEntity {

    @Id
    @Convert(converter = ClinicIdConverter.class)
    private ClinicId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private Facility facility;

    @Pattern(regexp = "^[A-Z0-9_]{2,20}$")
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private ClinicStatus status = ClinicStatus.ACTIVE;


    protected Clinic() {}

    public Clinic(Facility facility, String code, String name, ClinicStatus status) {
        assignId();
        this.facility = facility;
        this.code = code;
        this.name = name;
        this.status = status;
    }

    private void assignId(){
        if (id == null) {
            id = ClinicId.newId();
        }
    }


    public ClinicId getId() {
        return id;
    }

    public Facility getFacility() {
        return facility;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public ClinicStatus getStatus() {
        return status;
    }
}
