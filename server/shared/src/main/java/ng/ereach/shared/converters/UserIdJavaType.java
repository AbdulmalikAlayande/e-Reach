package ng.ereach.shared.converters;

import ng.ereach.shared.id.UserId;

/** Maps {@link UserId} as a primary key (see {@link TypedIdJavaType}). */
public class UserIdJavaType extends TypedIdJavaType<UserId> {

	public UserIdJavaType() {
		super(UserId.class, UserId::new, UserId::uuid);
	}
}
