package ng.ereach.facilityserver.platform.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.platform.entities.enums.ClinicStatus;

@Converter(autoApply = true)
public class ClinicStatusConverter implements AttributeConverter<ClinicStatus, String> {

    @Override
    public String convertToDatabaseColumn(ClinicStatus attribute) {
        return attribute == null ? null : attribute.getStatus();
    }

    @Override
    public ClinicStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ClinicStatus.fromStatus(dbData);
    }
}
