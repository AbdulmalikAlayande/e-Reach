package ng.ereach.facilityserver.iam.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountKind;

@Converter(autoApply = true)
public class UserAccountKindConverter implements AttributeConverter<UserAccountKind, String> {

    @Override
    public String convertToDatabaseColumn(UserAccountKind attribute) {
        return attribute == null ? null : attribute.getKind();
    }

    @Override
    public UserAccountKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserAccountKind.fromKind(dbData);
    }
}
