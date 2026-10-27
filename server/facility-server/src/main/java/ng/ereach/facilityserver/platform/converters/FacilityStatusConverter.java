package ng.ereach.facilityserver.platform.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.platform.entities.enums.FacilityStatus;

@Converter(autoApply = true)
public class FacilityStatusConverter implements AttributeConverter<FacilityStatus, String> {

    @Override
    public String convertToDatabaseColumn(FacilityStatus attribute) {
        return attribute == null ? null : attribute.getStatus();
    }

    @Override
    public FacilityStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FacilityStatus.fromStatus(dbData);
    }
}
