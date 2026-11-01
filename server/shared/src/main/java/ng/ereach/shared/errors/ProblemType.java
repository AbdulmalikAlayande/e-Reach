package ng.ereach.shared.errors;

import java.net.URI;
import java.util.Locale;

/**
 * One kind of problem an e-Reach API can report.
 * Each module lists its own kinds in an enum that implements this.
 */
public interface ProblemType {

	/** Where every e-Reach problem type URI starts (PRD section 10). */
	String TYPE_BASE = "https://ereach.ng/problems/";

	/** The stable code from the PRD, in UPPER_SNAKE_CASE, for example DUPLICATE_SUSPECTED. */
	String code();

	/** The HTTP status this kind of problem is always sent with. */
	int status();

	/** A short summary that is the same every time this kind of problem happens. */
	String title();

	/** The RFC 9457 type: {@code https://ereach.ng/problems/duplicate-suspected} for DUPLICATE_SUSPECTED. */
	default URI type() {
		return URI.create(TYPE_BASE + code().toLowerCase(Locale.ROOT).replace('_', '-'));
	}
}