package ng.ereach.facilityserver.platform.entities.enums;

public enum ClinicStatus {

    ACTIVE("active"),
    INACTIVE("inactive");

    private final String status;

    ClinicStatus(String status){
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public static ClinicStatus fromStatus(String status){
        for (ClinicStatus clinicStatus: values()){
            if (clinicStatus.status.equalsIgnoreCase(status)){
                return clinicStatus;
            }
        }
        throw new IllegalArgumentException(String.format("Unknown Facility Status: '%s' ", status));
    }
}
