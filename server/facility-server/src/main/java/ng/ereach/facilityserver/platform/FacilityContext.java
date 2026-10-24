package ng.ereach.facilityserver.platform;

import ng.ereach.shared.id.FacilityId;
import ng.ereach.shared.id.UserId;

import java.util.Objects;

/**
 * Which facility and which user the current piece of work is for.
 *
 * <p>The value lives in a {@link ScopedValue}, so it exists only inside the block passed to
 * {@link #runWithContext} or {@link #callWithContext}, disappears when that block ends, and is
 * not visible to other threads.
 *
 * <p>{@code facilityId} is null only in the sign-in case, before the user has chosen a facility.
 * Use {@link #forFacility} or {@link #forUserOnly} so that case is explicit.
 */
public record FacilityContext(FacilityId facilityId, UserId userId) {
	
	private static final ScopedValue<FacilityContext> CURRENT_FACILITY_CONTEXT = ScopedValue.newInstance();
	
	/** Work inside one facility, done by one user. */
	public static FacilityContext forFacility(FacilityId facilityId, UserId userId) {
		Objects.requireNonNull(facilityId, "facilityId");
		Objects.requireNonNull(userId, "userId");
		return new FacilityContext(facilityId, userId);
	}
	
	/** Sign-in: the user is known, but no facility has been chosen yet. */
	public static FacilityContext forUserOnly(UserId userId) {
		Objects.requireNonNull(userId, "userId");
		return new FacilityContext(null, userId);
	}
	
	public static FacilityContext getCurrent() {
		if (!CURRENT_FACILITY_CONTEXT.isBound()) {
			throw new IllegalStateException("No facility context is currently bound to this execution scope.");
		}
		return CURRENT_FACILITY_CONTEXT.get();
	}
	
	public static boolean isBound() {
		return CURRENT_FACILITY_CONTEXT.isBound();
	}
	
	public static void runWithContext(FacilityContext context, Runnable action) {
		ScopedValue.where(CURRENT_FACILITY_CONTEXT, context).run(action);
	}
	
	/**
	 * Runs {@code action} inside {@code context} and returns its result.
	 * The action's own checked exception (if any) passes straight through; an action that
	 * throws nothing needs no try/catch at the call site.
	 */
	public static <T, X extends Throwable> T callWithContext(
			FacilityContext context, ScopedValue.CallableOp<? extends T, X> action) throws X {
		return ScopedValue.where(CURRENT_FACILITY_CONTEXT, context).call(action);
	}
}