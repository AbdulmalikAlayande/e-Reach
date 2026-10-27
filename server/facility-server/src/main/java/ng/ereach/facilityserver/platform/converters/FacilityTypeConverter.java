package ng.ereach.facilityserver.platform.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.platform.entities.enums.FacilityType;

@Converter(autoApply = true)
public class FacilityTypeConverter implements AttributeConverter<FacilityType, String> {

    @Override
    public String convertToDatabaseColumn(FacilityType attribute) {
        return attribute == null ? null : attribute.getType();
    }

    @Override
    public FacilityType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FacilityType.fromType(dbData);
    }
}
