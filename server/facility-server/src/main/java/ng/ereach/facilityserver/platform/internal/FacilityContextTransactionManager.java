package ng.ereach.facilityserver.platform.internal;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import ng.ereach.facilityserver.platform.FacilityContext;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Starts every transaction the way JpaTransactionManager does, then tells PostgreSQL which
 * facility and user the transaction is for, before any application query runs (ADR-003).
 *
 * <p>Both settings are local to the transaction (third argument {@code true}), so they vanish
 * at commit or rollback and a pooled connection will not carry them into the next transaction.
 * When there is no context, both are set to an empty string, which row-level security treats
 * as "no facility": reads return nothing and writes are refused.
 */
public class FacilityContextTransactionManager extends JpaTransactionManager {
	
	private static final String CONTEXT_SQL_QUERY = """
		SELECT set_config('app.facility_id', ?, true),
		       set_config('app.user_id', ?, true)
	""";
	
	public FacilityContextTransactionManager(EntityManagerFactory entityManagerFactory) {
		super(entityManagerFactory);
	}
	
	@Override
	protected void doBegin(@NonNull Object transaction, @NonNull TransactionDefinition definition) {
		super.doBegin(transaction, definition);
		try {
			applyContext();
		} catch (RuntimeException e) {
			rollBackAndRelease(transaction, e);
			throw new CannotCreateTransactionException("Could not apply the facility context to the new transaction", e);
		}
	}
	
	private void rollBackAndRelease(@NonNull Object transaction, RuntimeException cause) {
		try {
			EntityManagerHolder holder =
					(EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
			if (holder != null) {
				EntityTransaction entityTransaction = holder.getEntityManager().getTransaction();
				if (entityTransaction.isActive()) {
					entityTransaction.rollback();
				}
			}
		} catch (RuntimeException rollbackFailure) {
			cause.addSuppressed(rollbackFailure);
		} finally {
			doCleanupAfterCompletion(transaction);
		}
	}
	
	protected void applyContext() {
		FacilityContext context = FacilityContext.isBound() ? FacilityContext.getCurrent() : null;
		String facilityId = (context != null && context.facilityId() != null) ? context.facilityId().toString() : "";
		String userId = (context != null && context.userId() != null) ? context.userId().toString() : "";
		
		DataSource dataSource = getDataSource();
		if (dataSource == null) {
			throw new IllegalStateException("No DataSource is set on the transaction manager");
		}
		
		Connection connection = DataSourceUtils.getConnection(dataSource);
		try (PreparedStatement statement = connection.prepareStatement(CONTEXT_SQL_QUERY)) {
			statement.setString(1, facilityId);
			statement.setString(2, userId);
			statement.execute();
		} catch (SQLException e) {
			throw new IllegalStateException("Could not set app.facility_id and app.user_id", e);
		}
	}
}
