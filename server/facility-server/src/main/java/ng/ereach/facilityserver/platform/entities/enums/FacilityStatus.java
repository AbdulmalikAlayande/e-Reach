package ng.ereach.facilityserver.platform.entities.enums;

public enum FacilityStatus {

    ACTIVE("active"),
    INACTIVE("inactive");

    private final String status;

    FacilityStatus(String status){
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public static FacilityStatus fromStatus(String status){
        for (FacilityStatus facilityStatus: values()){
            if (facilityStatus.status.equalsIgnoreCase(status)){
                return facilityStatus;
            }
        }
        throw new IllegalArgumentException(String.format("Unknown Facility Status: '%s' ", status));
    }
}
