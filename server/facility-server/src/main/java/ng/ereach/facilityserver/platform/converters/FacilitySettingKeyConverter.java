package ng.ereach.facilityserver.platform.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ng.ereach.facilityserver.platform.entities.enums.FacilitySettingKey;

@Converter(autoApply = true)
public class FacilitySettingKeyConverter implements AttributeConverter<FacilitySettingKey, String> {

    @Override
    public String convertToDatabaseColumn(FacilitySettingKey attribute) {
        return attribute == null ? null : attribute.key();
    }

    @Override
    public FacilitySettingKey convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FacilitySettingKey.fromKey(dbData)
                .orElseThrow(() -> new IllegalStateException("Unknown setting key: " + dbData));
    }
}