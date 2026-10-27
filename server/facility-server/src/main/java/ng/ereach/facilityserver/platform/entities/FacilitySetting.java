package ng.ereach.facilityserver.platform.entities;

import jakarta.persistence.*;
import ng.ereach.facilityserver.platform.entities.enums.FacilitySettingKey;
import ng.ereach.shared.id.Ids;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class FacilitySetting extends BaseEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private Facility facility;

    private FacilitySettingKey key;

    @JdbcTypeCode(value = SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    private JsonNode value;

    protected FacilitySetting(){}

    public FacilitySetting(Facility facility, FacilitySettingKey key, JsonNode value){
        assignId();
        this.facility = facility;
        this.key = key;
        setValue(value);
    }

    private void assignId(){
        if (id == null) {
            id = Ids.newId();
        }
    }

    public void setValue(JsonNode value) {
        if (!key.type().matches(value)) {
            throw new IllegalArgumentException(String.format("'%s' expects '%s' but got '%s'", key.key(), key.type(), value));
        }
        this.value = value;
    }
}

