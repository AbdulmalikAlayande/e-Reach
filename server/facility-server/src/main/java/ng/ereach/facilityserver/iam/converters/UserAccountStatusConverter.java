package ng.ereach.facilityserver.iam.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountStatus;

@Converter(autoApply = true)
public class UserAccountStatusConverter implements AttributeConverter<UserAccountStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserAccountStatus attribute) {
        return attribute == null ? null : attribute.getStatus();
    }

    @Override
    public UserAccountStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserAccountStatus.fromStatus(dbData);
    }
}
