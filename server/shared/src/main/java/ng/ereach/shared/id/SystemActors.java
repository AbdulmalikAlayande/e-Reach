package ng.ereach.shared.id;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SystemActors {

	public static final UserId INSTALLER      = UserId.of("00000000-0000-7000-8000-000000000001");
	public static final UserId REFERENCE_DATA = UserId.of("00000000-0000-7000-8000-000000000002");
	public static final UserId AUDIT_SEALER   = UserId.of("00000000-0000-7000-8000-000000000003");
	public static final UserId OUTBOX_SENDER  = UserId.of("00000000-0000-7000-8000-000000000004");
	
	public static UserId getInstallerId() {
		return INSTALLER;
	}
	
	public static UserId getReferenceDataId() {
		return REFERENCE_DATA;
	}
	
	public static UserId getAuditSealerId() {
		return AUDIT_SEALER;
	}
	
	public static UserId getOutboxSenderId() {
		return OUTBOX_SENDER;
	}
	
	static void main() {
		Set<UserId> userIds = new HashSet<>();
		userIds.add(getInstallerId());
		userIds.add(getReferenceDataId());
		userIds.add(getAuditSealerId());
		userIds.add(getOutboxSenderId());
		
		System.out.println(Arrays.toString(userIds.stream().map(UserId::toString).sorted().toArray()));
		
		try {
			boolean wasAbleToAdd = userIds.add(getInstallerId());
			if (!wasAbleToAdd)
				throw new RuntimeException("User already exists");
		} catch (RuntimeException e) {
			System.out.println(e.getMessage());
		}
	}
}
