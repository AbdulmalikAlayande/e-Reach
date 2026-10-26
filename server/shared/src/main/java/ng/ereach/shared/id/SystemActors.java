package ng.ereach.shared.id;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SystemActors {
	
	private static final UserId INSTALLER = UserId.newId();
	private static final UserId REFERENCE_DATA = UserId.newId();
	private static final UserId AUDIT_SEALER = UserId.newId();
	private static final UserId OUTBOX_SENDER = UserId.newId();
	
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
