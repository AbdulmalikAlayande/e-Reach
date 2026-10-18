package ng.ereach.shared.utils;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public class Ids {
    
    private Ids() {}

    /**
     * Generates a time-ordered, database-friendly UUIDv7
     */
    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
