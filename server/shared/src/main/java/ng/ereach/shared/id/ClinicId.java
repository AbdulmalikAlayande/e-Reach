package ng.ereach.shared.id;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public record ClinicId(UUID uuid) {

    public ClinicId {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
    }

    public static ClinicId newId() {
        return new ClinicId(UuidCreator.getTimeOrderedEpoch());
    }

    public static ClinicId of(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("UUID text cannot be null or blank");
        }
        try {
            return new ClinicId(UUID.fromString(text.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID string format: " + text, e);
        }
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
