package ng.ereach.shared.converters;

import ng.ereach.shared.id.FacilityId;

/** Maps {@link FacilityId} as a primary key (see {@link TypedIdJavaType}). */
public class FacilityIdJavaType extends TypedIdJavaType<FacilityId> {

	public FacilityIdJavaType() {
		super(FacilityId.class, FacilityId::new, FacilityId::uuid);
	}
}
