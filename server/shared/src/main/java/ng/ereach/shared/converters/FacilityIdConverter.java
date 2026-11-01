package ng.ereach.shared.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.shared.id.FacilityId;

import java.util.UUID;

@Converter(autoApply = true)
public class FacilityIdConverter implements AttributeConverter<FacilityId, UUID> {


    @Override
    public UUID convertToDatabaseColumn(FacilityId attribute) {
        return attribute == null ? null : attribute.uuid();
    }

    @Override
    public FacilityId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new FacilityId(dbData);
    }
}
