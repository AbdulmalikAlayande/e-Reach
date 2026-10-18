package ng.ereach.shared.identity;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public record PersonId(UUID uuid) {
	
	public PersonId {
		if (uuid == null) {
			throw new IllegalArgumentException("UUID cannot be null");
		}
	}
	
	public static PersonId newId() {
		return new PersonId(UuidCreator.getTimeOrderedEpoch());
	}
	
	public static PersonId of(String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("UUID text cannot be null or blank");
		}
		try {
			return new PersonId(UUID.fromString(text.trim()));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid UUID string format: " + text, e);
		}
	}
	@Override
	public String toString() {
		return uuid.toString();
	}
}