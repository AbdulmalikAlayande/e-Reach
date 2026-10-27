package ng.ereach.facilityserver.platform.entities.enums;

import tools.jackson.databind.JsonNode;

public enum FacilitySettingKeyType {

    BOOLEAN {
        @Override
        public boolean matches(JsonNode value) {
            return value != null && value.isBoolean();
        }
    },

    INTEGER {
        @Override
        public boolean matches(JsonNode value) {
            return value != null && value.isInt();
        }
    },

    OBJECT {
        @Override
        public boolean matches(JsonNode value) {
            return value != null && value.isObject();
        }
    };

    public abstract boolean matches(JsonNode value);
}
