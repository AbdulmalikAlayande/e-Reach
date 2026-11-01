package ng.ereach.shared.errors;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A broken rule that should reach the client as an RFC 9457 problem.
 * The message is the problem's "detail": written for the client, excluding internal details or patient data.
 */
public class EReachException extends RuntimeException {

    /** According to RFC 9457, section 3.2:
     * An extension should start with a letter, and must only contain letters, digits or underscores,
     * And it must also be at least 3 characters.
     */
    private static final Pattern EXTENSION_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{2,}$");

    /** The five standard members, plus "code", which every e-Reach problem already carries.*/
    private static final Set<String> RESERVED_NAMES = Set.of("type", "title", "status", "detail", "instance", "code");

    private final ProblemType problemType;
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public EReachException(ProblemType problemType, String detail) {
        this(problemType, detail, null);
    }

    public EReachException(ProblemType problemType, String detail, @Nullable Throwable cause) {
        super(detail, cause);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("A problem needs a detail for the client");
        }
    }

    /** Adds an extension member, for example the duplicate candidates or the current row version. */
    public EReachException withExtension(String name, Object value) {
        if (name == null || !EXTENSION_NAME.matcher(name).matches() || RESERVED_NAMES.contains(name)) {
            throw new IllegalArgumentException(String.format("%s is not a valid RFC 9457 extension name or it is already reserved", name));
        }
        extensions.put(name, Objects.requireNonNull(value, "value"));
        return this;
    }

    public ProblemType problemType() {
        return problemType;
    }

    public String detail() {
        return getMessage();
    }

    public Map<String, Object> extensions() {
        return Collections.unmodifiableMap(extensions);
    }
}