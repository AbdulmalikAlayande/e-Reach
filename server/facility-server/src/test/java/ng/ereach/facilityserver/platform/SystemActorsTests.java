package ng.ereach.facilityserver.platform;

import ng.ereach.shared.id.SystemActors;
import ng.ereach.shared.id.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemActorsTests {

	/**
	 * <p>Level: Unit</p>
	 * <p>Traces to: Rule 19, ADR-018</p>
	 */
	@Test
	@DisplayName("TC-006-60 | The four system actor IDs are fixed, all different, and all UUIDv7")
	void systemActorIdsAreFixed() {
		// Given the four system actors.
		List<UserId> actors = List.of(SystemActors.INSTALLER, SystemActors.REFERENCE_DATA,
				SystemActors.AUDIT_SEALER, SystemActors.OUTBOX_SENDER);

		// When their IDs are read.
		// Then each equals the value V3 inserts into user_account.
		assertEquals("00000000-0000-7000-8000-000000000001", SystemActors.INSTALLER.toString());
		assertEquals("00000000-0000-7000-8000-000000000002", SystemActors.REFERENCE_DATA.toString());
		assertEquals("00000000-0000-7000-8000-000000000003", SystemActors.AUDIT_SEALER.toString());
		assertEquals("00000000-0000-7000-8000-000000000004", SystemActors.OUTBOX_SENDER.toString());

		// And all four are different.
		assertEquals(4, Set.copyOf(actors).size());

		// And all four are version 7 with the standard variant.
		for (UserId actor : actors) {
			assertEquals(7, actor.uuid().version(), actor + " should be version 7");
			assertEquals(2, actor.uuid().variant(), actor + " should be variant 2");
		}
	}
}