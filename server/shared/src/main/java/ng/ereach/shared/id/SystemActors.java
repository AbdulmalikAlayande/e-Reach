package ng.ereach.shared.id;


public final class SystemActors {

	/** Installs a facility server and creates the first facility and the first platform operator. */
	public static final UserId INSTALLER = UserId.of("00000000-0000-7000-8000-000000000001");

	/** Loads reference data through the R__ migrations. */
	public static final UserId REFERENCE_DATA = UserId.of("00000000-0000-7000-8000-000000000002");

	/** Seals audit events (ER-009). */
	public static final UserId AUDIT_SEALER = UserId.of("00000000-0000-7000-8000-000000000003");

	/** Sends outbox entries to the central platform (Phase 2). */
	public static final UserId OUTBOX_SENDER = UserId.of("00000000-0000-7000-8000-000000000004");

	private SystemActors() {
	}
}