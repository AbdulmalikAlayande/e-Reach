package ng.ereach.facilityserver.platform.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import ng.ereach.shared.converters.ClinicIdJavaType;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ng.ereach.facilityserver.platform.entities.enums.ClinicStatus;
import ng.ereach.shared.entities.BaseEntity;
import ng.ereach.shared.id.ClinicId;
import ng.ereach.shared.converters.FacilityIdConverter;
import ng.ereach.shared.id.FacilityId;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;


@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Clinic extends BaseEntity {

    @Id
    @JavaType(ClinicIdJavaType.class)
    @JdbcTypeCode(SqlTypes.UUID)
    private ClinicId id;

    /** The facility by ID only; a to-one association to Facility cannot be lazy, because Facility has @SoftDelete. */
    @Column(name = "facility_id", nullable = false, updatable = false)
    @Convert(converter = FacilityIdConverter.class)
    private FacilityId facilityId;

    @Pattern(regexp = "^[A-Z0-9_]{2,20}$")
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private ClinicStatus status = ClinicStatus.ACTIVE;


    protected Clinic() {}

    public Clinic(FacilityId facilityId, String code, String name, ClinicStatus status) {
        assignId();
        this.facilityId = facilityId;
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
