package ng.ereach.shared.converters;

import ng.ereach.shared.id.ClinicId;

/** Maps {@link ClinicId} as a primary key (see {@link TypedIdJavaType}). */
public class ClinicIdJavaType extends TypedIdJavaType<ClinicId> {

	public ClinicIdJavaType() {
		super(ClinicId.class, ClinicId::new, ClinicId::uuid);
	}
}
