package ng.ereach.shared;

import ng.ereach.shared.identity.FacilityId;
import ng.ereach.shared.identity.Ids;
import ng.ereach.shared.identity.PersonId;
import ng.ereach.shared.identity.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class UuidModuleTests {
	
	/**
	 * <p>Level: Unit</p>
	 * 	</p>Traces to: Rule 4, ADR-006 </p>
	 */
	@Test
	@DisplayName("TC-004-03 | New IDs are UUIDv7")
	public void testNewIdsAreVersion7Uuid() {
		//Given: Nothing
		
		//When: Ids.newId() is called
		UUID generatedId = Ids.newId();
		
		//Then: The UUID's version() is 7 and its variant() is 2
		assertNotNull(generatedId, "Generated ID should not be null");
		
		// RFC 9562 specs: Version 7 indicates a Unix Epoch time-sorted UUID
		assertEquals(7, generatedId.version(), "The UUID version must be 7");
		
		// RFC spec variant 2 indicates the standard IETF variant layout used by Java
		assertEquals(2, generatedId.variant(), "The UUID variant must be 2");
	}
	
	/**
	 *
	 */
	@Test
	@DisplayName("TC-004-04 | IDs from different milliseconds sort in creation order, the way PostgreSQL sorts them")
	public void testChrono() {
		// Given: ID `A` is created, then at least 2 ms pass
		UUID idA = Ids.newId();
		try {
			Thread.sleep(2);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		// When: ID b is created
		UUID idB = Ids.newId();
		
		// Then: a.toString() sorts before b.toString()
		assertTrue(idA.toString().compareTo(idB.toString()) < 0, "ID A should sort before ID B");
	}
	
	/**
	 * <p>Level: Unit</p>
	 * </p>Traces to: Rule 4 </p>
	 */
	@Test
	@DisplayName("TC-004-05 | IDs are unique under load")
	public void testIdUniquenessUnderHeavyLoad() {
		// Given: Nothing
		
		// When: 100,000 IDs are created in a tight loop
		int numIds = 100_000;
		UUID[] ids = new UUID[numIds];
		for (int i = 0; i < numIds; i++) {
			ids[i] = Ids.newId();
		}
		Set<UUID> setOfDistinctIds = new HashSet<>();
		
		// Then: All 100,000 values are distinct
		for (int i = 0; i < numIds; i++) {
			assertTrue(setOfDistinctIds.add(ids[i]), "All IDs should be unique, but ID at index " + i + " is a duplicate");
		}
	}
	
	/**
	 * <p>Level: Unit</p>
	 * </p>Traces to: Rule 4 </p>
	 */
	@Test
	@DisplayName("TC-004-06 | Typed IDs refuse null")
	public void testBrandedNominalIdsDoesNotAcceptNull() {
		// Given: Nothing
		
		// When: each typed ID is constructed with null
		IllegalArgumentException nullUserIdException = assertThrows(IllegalArgumentException.class, () -> new UserId(null));
		IllegalArgumentException nullFacilityIdException = assertThrows(IllegalArgumentException.class, () -> new FacilityId(null));
		IllegalArgumentException nullPersonIdException = assertThrows(IllegalArgumentException.class, () -> new PersonId(null));
		
		// Then: An IllegalArgumentException is thrown with the correct validation message
		assertEquals("UUID cannot be null", nullUserIdException.getMessage());
		assertEquals("UUID cannot be null", nullFacilityIdException.getMessage());
		assertEquals("UUID cannot be null", nullPersonIdException.getMessage());
	}
	
	/**
	 * <p>Level: Unit</p>
	 * </p>Traces to: Rule 4 </p>
	 */
	@Test
	@DisplayName("TC-004-07 | Typed IDs compare by value and by type")
	public void testBrandedNominalIdsCompareByValueAndType() {
		// Given: Two IDs of each type wrapping the same UUID
		UUID sameUuid = Ids.newId();
		UserId userId1 = new UserId(sameUuid);
		UserId userId2 = new UserId(sameUuid);
		FacilityId facilityId = new FacilityId(sameUuid);
		PersonId personId1 = new PersonId(sameUuid);
		PersonId personId2 = new PersonId(sameUuid);
		
		// When: They are compared with equals and hashCode
		boolean userIdsEqual = Objects.equals(userId1, userId2);
		boolean userIdAndFacilityIdEqual = Objects.equals(userId1, facilityId);
		boolean personIdsEqual = Objects.equals(personId1, personId2);
		
		// Then: The two PersonIds are equal with equal hash codes; the PersonId and the FacilityId are not equal
		assertTrue(userIdsEqual, "Two UserIds wrapping the same UUID should be equal");
		assertEquals(userId1.hashCode(), userId2.hashCode(), "Two UserIds wrapping the same UUID should have the same hash code");
		assertFalse(userIdAndFacilityIdEqual, "A UserId and a FacilityId wrapping the same UUID should not be equal");
		assertTrue(personIdsEqual, "Two PersonIds wrapping the same UUID should be equal");
		assertEquals(personId1.hashCode(), personId2.hashCode(), "Two PersonIds wrapping the same UUID should have the same hash code");
		assertFalse(Objects.equals(personId1, userId1), "A PersonId and a UserId wrapping the same UUID should not be equal");
	}
	
	/**
	 * <p>Level: Unit</p>
	 * </p>Traces to: Rule 4 </p>
	 */
	@Test
	@DisplayName("TC-004-08 | A typed ID reads and prints as a plain UUID")
	public void testBrandedNominalIdsReadsAndPrintsAsPlainUUID() {
		// Given: A valid UUID string
		UUID uuid = Ids.newId();
		
		// When: each typed ID factory parses the text and converts it back to a string
		String userIdString = UserId.of(uuid.toString()).toString();
		String facilityIdString = FacilityId.of(uuid.toString()).toString();
		String personIdString = PersonId.of(uuid.toString()).toString();
		
		// Then: It returns exactly the same text, not UserId[value=...]
		assertEquals(uuid.toString(), userIdString, "toString() must output the plain UUID text value directly");
		assertEquals(uuid.toString(), facilityIdString, "toString() must output the plain UUID text value directly");
		assertEquals(uuid.toString(), personIdString, "toString() must output the plain UUID text value directly");
	}
	
	/**
	 * <p>Level: Unit</p>
	 * </p>Traces to: Rule 4 </p>
	 */
	@Test
	@DisplayName("TC-004-09 | Bad ID text is refused")
	public void testThatNonUUIDStringsCannotBePassedToFactoryMethod_Of() {
		// Given: A text `not-a-uuid`
		String nonUuidText = "not-a-uuid";
		
		// When: each typed ID factory parses the invalid text
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> UserId.of(nonUuidText));
		IllegalArgumentException facilityException = assertThrows(IllegalArgumentException.class, () -> FacilityId.of(nonUuidText));
		IllegalArgumentException personException = assertThrows(IllegalArgumentException.class, () -> PersonId.of(nonUuidText));
		
		// Then: An `IllegalArgumentException` is thrown
		assertNotNull(exception.getMessage(), "Exception should contain a descriptive error message");
		assertNotNull(facilityException.getMessage(), "Exception should contain a descriptive error message");
		assertNotNull(personException.getMessage(), "Exception should contain a descriptive error message");
	}
	
	/**
	 * <p>Level: Unit</p>
	 * </p>Traces to: Rule 4 </p>
	 */
	@Test
	@DisplayName("TC-004-10 | Each typed ID can make a fresh one ")
	public void testBrandedNominalIdsCanCreateNewUniqueIds() {
		// Given: Nothing
		
		// When: each typed ID factory creates a fresh ID
		UserId freshlyCreatedUserId = UserId.newId();
		FacilityId freshlyCreatedFacilityId = FacilityId.newId();
		PersonId freshlyCreatedPersonId = PersonId.newId();
		
		// Then: It returns a `UserId` whose value is version 7
		assertNotNull(freshlyCreatedUserId, "The factory method must return a valid UserId instance");
		assertNotNull(freshlyCreatedUserId.uuid(), "The wrapped UUID value must not be null");
		assertEquals(7, freshlyCreatedUserId.uuid().version(), "The underlying UUID version must be 7 (UUIDv7)");
	
		assertNotNull(freshlyCreatedFacilityId, "The factory method must return a valid UserId instance");
		assertNotNull(freshlyCreatedFacilityId.uuid(), "The wrapped UUID value must not be null");
		assertEquals(7, freshlyCreatedFacilityId.uuid().version(), "The underlying UUID version must be 7 (UUIDv7)");

		assertNotNull(freshlyCreatedPersonId, "The factory method must return a valid PersonId instance");
		assertNotNull(freshlyCreatedPersonId.uuid(), "The wrapped UUID value must not be null");
		assertEquals(7, freshlyCreatedPersonId.uuid().version(), "The underlying UUID version must be 7 (UUIDv7)");
	}
}
