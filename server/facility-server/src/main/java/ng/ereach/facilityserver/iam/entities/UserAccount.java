package ng.ereach.facilityserver.iam.entities;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountStatus;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountKind;
import ng.ereach.shared.converters.UserIdConverter;
import ng.ereach.shared.entities.BaseEntity;
import ng.ereach.shared.id.UserId;

import java.time.Instant;

@Entity
public class UserAccount extends BaseEntity {

    @Id
    @Convert(converter = UserIdConverter.class)
    private UserId id;

    private UserAccountKind kind;

    @Pattern(regexp = "^[a-z0-9._-]{3,40}$")
    private String username;

    @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private String email;

    private String fullName;

    @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$")
    private String phone;
    private String passwordHash;

    private Boolean mustChangePassword = Boolean.TRUE;
    private String pinHash;
    private byte[] mfaSecretEncrypted;
    private Instant mfaEnrolledAt;
    private Instant mfaRequiredBy;
    private int mfaLastUsedStep;

    private UserAccountStatus status;

    protected UserAccount() {}

    public UserAccount(UserAccountKind kind, String username, String fullName, String phone, String password, String pin) {
        assignId();
        this.kind = kind;
        this.username = username;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordHash = hashPassword(password);
        this.pinHash = hashPin(pin);
        setStatus(UserAccountStatus.ACTIVE);
    }

    private String hashPin(String pin) {
        // Argon2Id hash implementation coming soon
        return null;
    }

    private String hashPassword(String password) {
        // Argon2Id implementation coming soon
        return null;
    }

    private void assignId(){
        if (id == null)
            id = UserId.newId();
    }

    public void setStatus(UserAccountStatus status) {
        this.status = status;
    }

    public void setMfaRequiredBy(Instant mfaRequiredBy) {
        this.mfaRequiredBy = mfaRequiredBy;
    }

    public UserAccountKind getKind() {
        return kind;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public UserAccountStatus getStatus() {
        return status;
    }
}
