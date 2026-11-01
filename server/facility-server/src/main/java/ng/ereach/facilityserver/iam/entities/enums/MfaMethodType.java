package ng.ereach.facilityserver.iam.entities.enums;

import ng.ereach.facilityserver.iam.errors.MfaMethodNotSupportedException;

import java.util.Arrays;

public enum MfaMethodType {

    EMAIL("email"), TOTP("totp"), PASSKEY("passkey");

    private final String type;

    MfaMethodType(String type) {
        this.type = type;
    }

    public String getMethodType() {
        return type;
    }

    public static MfaMethodType fromMethodType(String type){
        return Arrays.stream(values())
                .filter(mfaMethodType -> mfaMethodType.type.equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new MfaMethodNotSupportedException("Unsupported MFA method: " + type));
    }

}
