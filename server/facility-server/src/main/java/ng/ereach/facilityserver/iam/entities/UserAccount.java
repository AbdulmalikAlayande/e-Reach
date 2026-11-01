package ng.ereach.facilityserver.iam.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountKind;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountStatus;
import ng.ereach.shared.converters.UserIdJavaType;
import ng.ereach.shared.entities.BaseEntity;
import ng.ereach.shared.id.UserId;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Locale;

@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class UserAccount extends BaseEntity {

    @Id
    @JavaType(UserIdJavaType.class)
    @JdbcTypeCode(SqlTypes.UUID)
    private UserId id;

    private UserAccountKind kind;

    /** Stored exactly as typed; unique ignoring case (D-021). */
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,40}$")
    private String username;

    /** Every person has one, in lower case (D-022). */
    @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private String email;

    private String fullName;

    @Pattern(regexp = "^\\+[1-9][0-9]{6,14}$")
    private String phone;

    /** Argon2id, made by the PasswordEncoder bean; never the password itself (ADR-007). */
    private String passwordHash;

    private Boolean mustChangePassword = Boolean.FALSE; // true only after an admin forces a reset (D-023)

    /** Argon2id, made by the PasswordEncoder bean; never the PIN itself (ADR-007). */
    private String pinHash;

    private Instant mfaRequiredBy;

    @Column(name = "is_platform_operator", nullable = false)
    private boolean platformOperator = false;

    private UserAccountStatus status = UserAccountStatus.ACTIVE;

    protected UserAccount() {}

    /**
     * A person account. The caller hashes the password and PIN, because hashing needs the PasswordEncoder bean,
     * which an entity cannot reach. The email is stored in lower case, as V3 requires.
     */
    public UserAccount(String username, String fullName, String email, String phone, String passwordHash, String pinHash) {
        assignId();
        this.kind = UserAccountKind.PERSON;
        this.username = username;
        this.fullName = fullName;
        this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.pinHash = pinHash;
    }

    private void assignId() {
        if (id == null)
            id = UserId.newId();
    }

    /** Makes this account a platform operator (D-009). V3 refuses it for a system account. */
    public void makePlatformOperator() {
        this.platformOperator = true;
    }

    public void setStatus(UserAccountStatus status) {
        this.status = status;
    }

    public void setMfaRequiredBy(Instant mfaRequiredBy) {
        this.mfaRequiredBy = mfaRequiredBy;
    }

    public UserId getId() {
        return id;
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

    public boolean isPlatformOperator() {
        return platformOperator;
    }

    public Instant getMfaRequiredBy() {
        return mfaRequiredBy;
    }
}
