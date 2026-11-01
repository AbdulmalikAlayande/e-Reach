package ng.ereach.facilityserver.iam.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;

@Converter(autoApply = true)
public class MfaMethodTypeConverter implements AttributeConverter<MfaMethodType, String> {

    @Override
    public String convertToDatabaseColumn(MfaMethodType attribute) {
        return attribute == null ? null : attribute.getMethodType();
    }

    @Override
    public MfaMethodType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MfaMethodType.fromMethodType(dbData);
    }
}
