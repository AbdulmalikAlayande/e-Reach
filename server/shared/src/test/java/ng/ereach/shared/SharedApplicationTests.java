package ng.ereach.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class SharedApplicationTests {
	
	/**
	 * Absolute path to the multi-module repository root used by the external-process tests
	 * in this class.
	 *
	 * <p>This constant designates the filesystem location that contains the top-level
	 * {@code pom.xml} for the e-Reach project. Several tests here launch the Maven
	 * wrapper (mvnw / mvnw.cmd) as an external process and rely on the working directory
	 * being the repository root so the reactor build order and dependency reporting are
	 * produced consistently.</p>
	 *
	 * <p>Implementation notes:</p>
	 * <ul>
	 *   <li>The value is expressed as an absolute Windows path for the primary
	 *       development environment. When running tests on non-Windows CI or
	 *       developer machines update this constant or adapt tests accordingly.</li>
	 *   <li>The constant is immutable and intended only for test use; it has no
	 *       effect at application runtime.</li>
	 *   <li>ProcessBuilder instances created by {@link #configureProcessBuilder(String[], String[])}
	 *       will use this directory as their working directory.</li>
	 * </ul>
	 *
	 * @see #configureProcessBuilder(String[], String[])
	 * @since 0.0.1
	 */
	public static final String PROJECT_ROOT_DIRECTORY = "C:\\Users\\HP\\IdeaProjects\\eReach\\server";
	/**
	 * Build and return a configured {@link ProcessBuilder} ready to execute the
	 * Maven wrapper from the repository root.
	 *
	 * <p>This helper centralizes platform-specific command selection (Windows vs
	 * Unix-like) and applies a consistent set of settings used by the tests in
	 * this class:</p>
	 * <ul>
	 *   <li>The working directory is set to {@link #PROJECT_ROOT_DIRECTORY} so the
	 *       launched process executes at the repository root.</li>
	 *   <li>{@code redirectErrorStream(true)} is applied so STDERR is merged with
	 *       STDOUT and can be captured via a single input stream reader.</li>
	 * </ul>
	 *
	 * <p>The method itself does not start the process; it returns a configured but
	 * mutable {@link ProcessBuilder} instance so callers can further adjust the
	 * environment if necessary before invoking {@code start()}.</p>
	 *
	 * @param winCommands platform-specific command array to use on Windows. Typical
	 *                    examples: {@code {"cmd.exe", "/c", "mvnw.cmd", "validate"}}
	 * @param unixCommands platform-specific command array to use on Unix-like systems. Typical
	 *                     examples: {@code {"./mvnw", "validate"}}
	 * @return a configured {@link ProcessBuilder} instance; the process is not started by this method.
	 * @throws IllegalArgumentException if both command arrays are null or empty (caller error)
	 */
	private ProcessBuilder configureProcessBuilder(String[] winCommands, String[] unixCommands) {
		
		File projectDir = new File(PROJECT_ROOT_DIRECTORY);
		ProcessBuilder processBuilder;
		
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("win"))
			processBuilder = new ProcessBuilder(winCommands);
		else
			processBuilder = new ProcessBuilder(unixCommands);
		
		processBuilder.directory(projectDir);
		processBuilder.redirectErrorStream(true);
		
		return processBuilder;
	}
	
	/**
	 * Level: Build
	 * Traces To: PRD section 7
	 *
	 * <p> Integration-level test that verifies the shared module is included in the
	 * top-level Maven reactor and that the reactor build completes successfully.</p>
	 *
	 * <p>This test executes the repository's Maven wrapper as an external process
	 * ({@code mvnw validate}) using the repository root defined by
	 * {@link #PROJECT_ROOT_DIRECTORY}. It captures the process output, asserts the
	 * process returns a zero exit code, and verifies the reactor output contains the
	 * expected modules in the correct order: the top-level project ("e-Reach"),
	 * the shared module ("ereach-shared"), and the facility server ("facility-server").</p>
	 *
	 * <p>Rationale: placing this check in the test suite provides a build-level
	 * safety-net that detects accidental removals or misconfiguration of the
	 * shared module within the multi-module reactor. Because this test spawns an
	 * external Maven invocation it is intentionally slower than unit tests and may
	 * be environment-dependent (Maven wrapper availability, network settings,
	 * filesystem layout).</p>
	 *
	 * <p>Implementation notes:</p>
	 * <ul>
	 *   <li>Uses {@link #configureProcessBuilder(String[], String[])} to create a
	 *       platform-appropriate {@link ProcessBuilder} instance.</li>
	 *   <li>Redirects STDERR into STDOUT so both streams are captured in a single
	 *       reader; the method checks for a zero exit code and then inspects output
	 *       lines to find module names and {@code SUCCESS} markers.</li>
	 * </ul>
	 *
	 * @throws AssertionError if the Maven process fails (non-zero exit code) or the
	 *         expected reactor modules/order are not present in the captured output
	 * @see #configureProcessBuilder(String[], String[])
	 * @see #assertReactorListAllModules(List)
	 * @since 0.0.1
	 */
	@Test
	@DisplayName("TC-004-01 | The shared module is part of the build")
	void runMavenValidate() {
		
		try {
			//Given: The root pom.xml lists backend/shared, and facility-server depends on ereach-shared\
			ProcessBuilder processBuilder = configureProcessBuilder(
				new String[]{"cmd.exe", "/c", "mvnw.cmd", "validate"},
				new String[]{"./mvnw", "validate"}
			);
			
			List<String> consoleOutput = new ArrayList<>();
			
			//When: .\mvnw.cmd validate runs at the root
			Process process = processBuilder.start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println(line);
					consoleOutput.add(line);
				}
			}
			
			int exitCode = process.waitFor();
			assertEquals(0, exitCode, "Maven validate failed!");
			
			//Then: The reactor lists e-Reach, then ereach-shared, then facility-server, all SUCCESS
			assertReactorListAllModules(consoleOutput);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			// Explicitly fail the JUnit test if an unexpected exception occurs
			throw new AssertionError("Test interrupted due to process execution error", e);
			
		}
		
	}
	
	/**
	 * Assert the multi-module Maven reactor includes the expected modules in the expected order.
	 *
	 * <p>This method examines the captured lines of Maven console output produced by
	 * the reactor build and asserts that the top-level project ("e-Reach"), the shared
	 * module ("ereach-shared") and the facility server module ("facility-server") all
	 * appear and report {@code SUCCESS}. The checks also enforce ordering constraints
	 * so that the repository root is built before the shared module and the shared
	 * module is built before the facility server.</p>
	 *
	 * <p>The method looks for lines that contain both the module name and the word
	 * {@code SUCCESS} to reduce false positives from unrelated log entries.</p>
	 *
	 * @param consoleOutput list of lines captured from the Maven process stdout; must not be null.
	 * @throws AssertionError if any expected module is missing, not marked SUCCESS, or the ordering is incorrect.
	 */
	private void assertReactorListAllModules(List<String> consoleOutput) {
		int eReachIndex = -1;
		int eReachSharedIndex = -1;
		int facilityServerIndex = -1;
		
		for (int i = 0; i < consoleOutput.size(); i++) {
			String line = consoleOutput.get(i);
			if (line.contains("e-Reach") && line.contains("SUCCESS")) {
				eReachIndex = i;
			}else if (line.contains("ereach-shared") && line.contains("SUCCESS")) {
				eReachSharedIndex = i;
			} else if (line.contains("facility-server") && line.contains("SUCCESS")) {
				facilityServerIndex = i;
			}
		}
		
		assertTrue(eReachIndex != -1, "e-Reach module was not found or did not report SUCCESS");
		assertTrue(eReachSharedIndex != -1, "ereach-shared module was not found or did not report SUCCESS");
		assertTrue(facilityServerIndex != -1, "facility-server module was not found or did not report SUCCESS");
		
		assertTrue(eReachIndex < eReachSharedIndex, "e-Reach must be built before ereach-shared");
		assertTrue(eReachSharedIndex < facilityServerIndex, "ereach-shared must be built before facility-server");
		
	}
	
	/**
	 * Level: Build
	 * Traces To: PRD section 7
	 *
	 * <p> Build-level policy test that validates the compile-time surface of the
	 * shared module remains intentionally small.</p>
	 *
	 * <p>This test runs {@code mvn -pl backend/shared dependency:tree} via the
	 * Maven wrapper and scans the produced dependency tree to ensure the shared
	 * module exposes only the approved compile-scoped dependency
	 * {@code com.github.f4b6a3:uuid-creator}. Any other {@code :compile} scoped
	 * dependency is considered a policy violation and causes the test to fail.</p>
	 *
	 * <p>Rationale: keeping compile-time dependencies minimal reduces coupling
	 * between modules and prevents accidental leakage of heavy dependencies into
	 * consumers of the shared module. This test acts as a guardrail during
	 * development and code reviews.</p>
	 *
	 * @throws AssertionError when an unexpected {@code :compile} scoped dependency is
	 *         found in the dependency tree or when the external Maven command fails.
	 * @see #verifyOnlyUuidCreatorExistsInDepsTree(List)
	 * @since 0.0.1
	 */
	@Test
	@DisplayName("TC-004-02 | The shared module stays small")
	void testThatSharedModuleSizeStaysSmall(){
		try {
			//Given: The shared module's pom.xml
			ProcessBuilder processBuilder = configureProcessBuilder(
				new String[]{"cmd.exe", "/c", "mvnw.cmd", "-pl", "backend/shared", "dependency:tree"},
				new String[]{"./mvnw", "-pl", "backend/shared", "dependency:tree"}
			);
			
			List<String> depsTreeLines = new ArrayList<>();
			
			//When: run .\mvnw.cmd -pl backend/shared dependency:tree runs at the root
			Process process = processBuilder.start();
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine())!= null)     {
					System.out.println(line);
					depsTreeLines.add(line);
				}
			}
			int exitCode = process.waitFor();
			assertEquals(0, exitCode, "Maven dependency:tree execution failed!");
			
			//Then: 'Compile' scope holds only uuid-creator; everything else is 'Test' scope
			boolean foundUuidCreator = verifyOnlyUuidCreatorExistsInDepsTree(depsTreeLines);
			assertTrue(foundUuidCreator, "Validation missing: uuid-creator was not found in the dependency tree.");
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			throw new AssertionError("Test interrupted due to process execution error", e);
			
		}
	}
	
	/**
	 * Inspect the dependency tree output for compile-scope dependencies and validate policy.
	 *
	 * <p>By design the shared module should expose a minimal compile-time surface. This
	 * helper scans the lines produced by {@code mvn -pl backend/shared dependency:tree}
	 * and ensures there are no unexpected {@code :compile} scoped dependencies. The only
	 * permitted compile-scoped dependency is {@code com.github.f4b6a3:uuid-creator} which
	 * is required by the shared utilities. Any other compile-scoped dependency will cause
	 * an immediate {@link AssertionError} to be thrown to highlight an unintended
	 * compile-time coupling.</p>
	 *
	 * @param depsTreeLines the lines of text produced by the Maven dependency tree execution; must not be null.
	 * @return {@code true} if the expected compile dependency {@code com.github.f4b6a3:uuid-creator}
	 *         was found and no other compile-scope dependency exists.
	 * @throws AssertionError when an unexpected compile-scoped dependency is encountered.
	 * @since 0.0.1
	 */
	private boolean verifyOnlyUuidCreatorExistsInDepsTree(List<String> depsTreeLines) {
        boolean foundUuidCreator = false;
		for (String line : depsTreeLines) {
			String trimmedLine = line.trim();
			if (trimmedLine.endsWith(":compile")) {
				if (!trimmedLine.contains("com.github.f4b6a3:uuid-creator"))
					throw new AssertionError("Unexpected compile dependency found in shared module: " + trimmedLine);
				else
					foundUuidCreator = true;
			}
		}
		return foundUuidCreator;
	}
}
