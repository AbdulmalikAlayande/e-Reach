package ng.ereach.facilityserver;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build if one module uses another module's internal classes,
 * or if two modules depend on each other in a loop (ADR-001).
 */
class ModularityTests {

	@Test
	void modulesRespectTheirBoundaries() {
		ApplicationModules.of(FacilityServerApplication.class).verify();
	}

}
