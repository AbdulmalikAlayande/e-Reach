package ng.ereach.shared.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.shared.id.ClinicId;

import java.util.UUID;

@Converter(autoApply = true)
public class ClinicIdConverter implements AttributeConverter<ClinicId, UUID> {


    @Override
    public UUID convertToDatabaseColumn(ClinicId attribute) {
        return attribute == null ? null : attribute.uuid();
    }

    @Override
    public ClinicId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new ClinicId(dbData);
    }
}
