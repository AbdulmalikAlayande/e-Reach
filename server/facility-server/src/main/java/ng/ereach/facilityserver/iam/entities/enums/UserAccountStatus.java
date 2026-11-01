package ng.ereach.facilityserver.iam.entities.enums;

public enum UserAccountStatus {

    ACTIVE("active"),
    SUSPENDED("suspended");

    private final String status;

    UserAccountStatus(String status){
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public static UserAccountStatus fromStatus(String status){
        for (UserAccountStatus userAccountStatus: values()){
            if (userAccountStatus.status.equalsIgnoreCase(status)){
                return userAccountStatus;
            }
        }
        throw new IllegalArgumentException(String.format("Unknown User Account Status: '%s' ", status));
    }
}
