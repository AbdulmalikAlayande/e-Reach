package ng.ereach.facilityserver.iam.entities.enums;

public enum UserAccountKind {

    PERSON("person"),
    SYSTEM("system");

    private final String kind;

    UserAccountKind(String kind){
        this.kind = kind;
    }

    public String getKind() {
        return kind;
    }

    public static UserAccountKind fromKind(String kind){
        for (UserAccountKind userAccountKind: values()){
            if (userAccountKind.kind.equalsIgnoreCase(kind)){
                return userAccountKind;
            }
        }
        throw new IllegalArgumentException(String.format("Unknown User Account Kind: '%s' ", kind));
    }
}
