package ng.ereach.shared.converters;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.ImmutableMutabilityPlan;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.SqlTypes;

import java.util.UUID;
import java.util.function.Function;

/**
 * Tells Hibernate how a typed ID (a record wrapping one UUID) maps to a {@code uuid} column.
 *
 * <p>Hibernate 7 refuses an {@code AttributeConverter} on an {@code @Id}, so primary keys use a
 * {@code JavaType} instead: {@code @Id @JavaType(FacilityIdJavaType.class) @JdbcTypeCode(SqlTypes.UUID)}.
 * Typed IDs used in other attributes keep their {@code AttributeConverter}.
 *
 * @param <T> the typed ID, for example {@code FacilityId}
 */
public abstract class TypedIdJavaType<T> extends AbstractClassJavaType<T> {

	private final Function<UUID, T> fromUuid;
	private final Function<T, UUID> toUuid;

	protected TypedIdJavaType(Class<T> idType, Function<UUID, T> fromUuid, Function<T, UUID> toUuid) {
		super(idType, ImmutableMutabilityPlan.instance());
		this.fromUuid = fromUuid;
		this.toUuid = toUuid;
	}

	/** Every typed ID is stored in a PostgreSQL {@code uuid} column. */
	@Override
	public JdbcType getRecommendedJdbcType(JdbcTypeIndicators indicators) {
		return indicators.getJdbcType(SqlTypes.UUID);
	}

	@Override
	public <X> X unwrap(T value, Class<X> type, WrapperOptions options) {
		if (value == null) {
			return null;
		}
		UUID uuid = toUuid.apply(value);
		if (UUID.class.isAssignableFrom(type)) {
			return type.cast(uuid);
		}
		if (String.class.isAssignableFrom(type)) {
			return type.cast(uuid.toString());
		}
		throw unknownUnwrap(type);
	}

	@Override
	public <X> T wrap(X value, WrapperOptions options) {
		if (value == null) {
			return null;
		}
		if (value instanceof UUID uuid) {
			return fromUuid.apply(uuid);
		}
		if (value instanceof String text) {
			return fromUuid.apply(UUID.fromString(text));
		}
		throw unknownWrap(value.getClass());
	}

	@Override
	public T fromString(CharSequence text) {
		return fromUuid.apply(UUID.fromString(text.toString()));
	}
}
