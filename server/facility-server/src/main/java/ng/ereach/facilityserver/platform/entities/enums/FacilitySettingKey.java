package ng.ereach.facilityserver.platform.entities.enums;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum FacilitySettingKey {

    GATE_CONSULTATION_REQUIRES_PAYMENT("gate.consultation_requires_payment", FacilitySettingKeyType.BOOLEAN, BooleanNode.FALSE),
    GATE_LAB_REQUIRES_PAYMENT("gate.lab_requires_payment", FacilitySettingKeyType.BOOLEAN, BooleanNode.TRUE),
    GATE_PHARMACY_REQUIRES_PAYMENT("gate.pharmacy_requires_payment", FacilitySettingKeyType.BOOLEAN, BooleanNode.TRUE),

    LAB_VERIFICATION_REQUIRED("lab.verification_required", FacilitySettingKeyType.BOOLEAN, BooleanNode.TRUE),

    SESSION_IDLE_LOCK_MINUTES("session.idle_lock_minutes", FacilitySettingKeyType.INTEGER, IntNode.valueOf(10)),
    SESSION_MAX_HOURS("session.max_hours", FacilitySettingKeyType.INTEGER, IntNode.valueOf(12)),
    RECEIPT_GAPLESS("receipt.gapless", FacilitySettingKeyType.BOOLEAN, BooleanNode.TRUE);

    private static final Map<String, FacilitySettingKey> KEY_MAP =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(FacilitySettingKey::key, Function.identity()));

    private final String key;
    private final FacilitySettingKeyType keyType;
    private final JsonNode defaultValue;

    FacilitySettingKey(String key, FacilitySettingKeyType keyType, JsonNode defaultValue) {
        if (!keyType.matches(defaultValue)) {
            throw new IllegalArgumentException("Default for " + key + " is not " + keyType);
        }
        this.key = key;
        this.keyType = keyType;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public FacilitySettingKeyType type() {
        return keyType;
    }

    public JsonNode defaultValue() {
        return defaultValue;
    }

    public static Optional<FacilitySettingKey> fromKey(String key){
        return Optional.ofNullable(KEY_MAP.get(key));
    }
}
