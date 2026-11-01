package ng.ereach.shared.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.shared.id.UserId;

import java.util.UUID;

@Converter(autoApply = true)
public class UserIdConverter implements AttributeConverter<UserId, UUID> {


    @Override
    public UUID convertToDatabaseColumn(UserId attribute) {
        return attribute == null ? null : attribute.uuid();
    }

    @Override
    public UserId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : new UserId(dbData);
    }
}
