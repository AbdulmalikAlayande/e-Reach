package ng.ereach.facilityserver.platform.entities.enums;

public enum FacilityType {
    PHC("phc"),
    CLINIC("clinic"),
    SECONDARY("secondary"),
    TERTIARY("tertiary");

    private final String type;

    FacilityType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static FacilityType fromType(String type) {
        for (FacilityType facilityType : values()) {
            if (facilityType.type.equalsIgnoreCase(type)) {
                return facilityType;
            }
        }
        throw new IllegalArgumentException("Unknown facility type: " + type);
    }
}