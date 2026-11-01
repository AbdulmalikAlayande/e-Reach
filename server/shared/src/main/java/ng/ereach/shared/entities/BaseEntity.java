package ng.ereach.shared.entities;

import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import ng.ereach.shared.converters.UserIdConverter;
import ng.ereach.shared.id.UserId;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;


@MappedSuperclass
public abstract class BaseEntity {

    @Version
    private Long rowVersion;

    @Generated
    private Instant createdAt;

    @Generated
    @Convert(converter = UserIdConverter.class)
    private UserId createdBy;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Convert(converter = UserIdConverter.class)
    private UserId updatedBy;


    public Long getRowVersion() {
        return rowVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UserId getCreatedBy() {
        return createdBy;
    }

    public UserId getUpdatedBy() {
        return updatedBy;
    }
}
