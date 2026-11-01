package ng.ereach.facilityserver.iam.entities;

import jakarta.persistence.*;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;
import ng.ereach.shared.entities.BaseEntity;
import ng.ereach.shared.id.Ids;

import java.time.Instant;
import java.util.UUID;

@Entity
public class MfaMethod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private UserAccount userAccount;

    private MfaMethodType type;

    private byte[] secretEncrypted;
    private Long lastUsedStep;
    private Instant confirmedAt;
    private Instant lastUsedAt;

    protected MfaMethod() {}

    public MfaMethod(UserAccount userAccount, MfaMethodType type) {
        assignId();
        this.userAccount = userAccount;
        this.type = type;
    }

    private void assignId(){
        if (id == null)
            id = Ids.newId();
    }

    public void setSecretEncrypted(byte[] secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public MfaMethodType getType() {
        return type;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public byte[] getSecretEncrypted() {
        return secretEncrypted;
    }

    public long lastUsedStepOrNone() {
        return lastUsedStep == null ? -1 : lastUsedStep;
    }

    public void confirm(long step, Instant now) {
        this.confirmedAt = now;
        recordUse(step, now);
    }

    public void recordUse(long step, Instant now) {
        this.lastUsedStep = step;
        this.lastUsedAt = now;
    }
}
