package ng.ereach.shared;

import ng.ereach.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;


public class EreachMoneyModuleTests {
	
	/**
	 *
	 */
	@Test
	@DisplayName("TC-004-11 | Money adds and subtracts exactly")
	public void testThatMoneyAddsAndSubtractsPreciselyWithNoMistakes() {
		// Given: 10 kobo and 20 kobo
		Money tenKobo = Money.ofKobo(10);
		Money twentyKobo = Money.ofKobo(20);
		
		// When: They are added, then 20 kobo is subtracted from the sum
		Money koboSum = tenKobo.plus(twentyKobo);
		Money koboResult = koboSum.minus(twentyKobo);
		
		// Then: The sum is 30 kobo; the difference is 10 kobo
		assertEquals(Money.ofKobo(30), koboSum, "The sum of 10 kobo and 20 kobo should be 30 kobo");
		assertEquals(Money.ofKobo(10), koboResult, "The difference of 30 kobo and 20 kobo should be 10 kobo");
	}

	/**
	 *
	 */
	@Test
	@DisplayName("TC-004-12 | Money never wraps around")
	public void testThatMoneyDoesNotApproximateAndDoesNotWrapExtraValuesAround() {
		// Given: `Money.ofKobo(Long.MAX_VALUE)`
		long maxLong = Long.MAX_VALUE;
		Money maxKobo = Money.ofKobo(maxLong);
		
		// When: 1 kobo is added to maxKobo
		assertThrows(ArithmeticException.class, () -> maxKobo.plus(Money.ofKobo(1)),
				"Adding 1 kobo to Long.MAX_VALUE should throw an ArithmeticException");

		// Then: An `ArithmeticException` is thrown rather than a wrapped amount
	}
	
	@Test
	@DisplayName("TC-004-13 | Co-payment matches the PRD's worked example, where a copayment of 10% is calculated on a tariff of 400,000 kobo (₦4,000)")
	public void testThatCoPaymentMatchesPRDWorkedExample() {
		// Given: A tariff of 400,000 kobo (₦4,000) and a co-payment of 10% (1,000 basis points)
		Money tariff = Money.ofKobo(400_000);
		int copayBps = 1_000; // 10% in basis points;
		
		// When: The co-payment is calculated
		Money copay = tariff.percentBps(copayBps);
		Money payer = tariff.minus(copay);

		// Then: The patient pays 40,000 kobo (₦400), and the payer claim is 360,000 kobo (₦3,600)
		assertEquals(Money.ofKobo(40_000), copay,
				"10% of a 400,000-kobo tariff should be a 40,000-kobo co-payment");
		assertEquals(Money.ofKobo(360_000), payer,
				"The payer share should be the tariff less the patient's co-payment");
		assertEquals(tariff, copay.plus(payer),
				"The patient and payer shares should add back to the original tariff");
	}

	@Test
	@DisplayName("TC-004-14 | Co-payment rounds half up")
	public void testThatCoPaymentRoundsHalfUpAccordingToPRD() throws IOException {
		// Given: Rounding cases in the CSV table beside this test and for each row of the table
		Path table = Path.of("src/test/java/ng/ereach/shared/value_table.csv");

		try (var rows = Files.lines(table).skip(1)) {
			rows.filter(row -> !row.isBlank()).forEach(row -> {
				String[] values = row.split(",", 4);
				assertEquals(4, values.length, "Every value-table row must have four columns");

				long amountKobo = Long.parseLong(values[0].trim());
				int basisPoints = Integer.parseInt(values[1].trim());
				long expectedKobo = Long.parseLong(values[2].trim());
				
				// When: The co-payment is calculated for the amount and basis points, `percentBps` is called
				Money percentBps = Money.ofKobo(amountKobo).percentBps(basisPoints);
				
				// Then: The result matches the expected value in the table
				assertEquals(Money.ofKobo(expectedKobo), percentBps,
					"Co-payment should round half up for amount " + amountKobo + " and basis points " + basisPoints);
			});
		}
	}

	@Test
	@DisplayName("TC-004-15 | Basis points stay between 0 and 10,000")
	public void testThatCoPaymentRejectsBasisPointsOutsideAllowedRange() {
		// Given: A non-negative amount and basis-point values outside the inclusive [0, 10,000] range
		Money amount = Money.ofKobo(1_000);

		// When Values below 0 and above 10,000 (`-1` and `10000`) are called on `percentBps()`
		IllegalArgumentException valueBelowException = assertThrows(IllegalArgumentException.class, () -> amount.percentBps(-1),
				"Negative basis points should be rejected");
		IllegalArgumentException valueAboveException = assertThrows(IllegalArgumentException.class, () -> amount.percentBps(10_001),
				"Basis points above 10,000 should be rejected");
		
		// Then: Both throw `IllegalArgumentException` with the expected message, because values below 0 and above 10,000 are invalid
		assertEquals("basisPoints must be between 0 and 10000, but was -1", valueBelowException.getMessage(),
				"Exception message for basis points below 0 should indicate the valid range");
		assertEquals("basisPoints must be between 0 and 10000, but was 10001", valueAboveException.getMessage(),
				"Exception message for basis points above 10,000 should indicate the valid range");
	}

	@Test
	@DisplayName("TC-004-16 | A percentage of a negative amount is refused")
	public void testThatPercentageOfNegativeAmountIsRejected() {
		// Given: A negative amount (−100 kobo)
		Money amount = Money.ofKobo(-100);

		// When: `percentBps(1000)` is called
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> amount.percentBps(1_000),
				"Percentages of negative amounts should be rejected");
		
		// Then: The exception message is correct
		assertEquals("cannot take a percentage of a negative amount: " + amount.kobo() + " kobo", exception.getMessage(),
				"Exception message for a negative amount should indicate the valid range");
	}

	@Test
	@DisplayName("TC-004-17 | Patient and payer shares always add back to the amount")
	public void testThatPatientAndPayerSharesAddBackToAmount() {
		// Given: 10,000 deterministic pairs (an amount from 0 to 10¹² kobo) and basis-point pairs in the supported ranges (0 to 10,000)
		SplittableRandom random = new SplittableRandom(0xEAEA_C0DEL);

		for (int i = 0; i < 10_000; i++) {
			long amountKobo = random.nextLong(1_000_000_000_001L);
			int basisPoints = random.nextInt(10_001);
			Money amount = Money.ofKobo(amountKobo);

			// When: The patient and payer shares are calculated (`copay = amount.percentBps(bps)` and `payer = amount.minus(copay)`)
			Money copay = amount.percentBps(basisPoints);
			Money payer = amount.minus(copay);

			// Then: Both shares are non-negative and reconstruct the original amount (`copay.plus(payer)` equals the amount)
			assertTrue(copay.kobo() >= 0, "The co-payment must never be negative");
			assertTrue(payer.kobo() >= 0, "The payer share must never be negative");
			assertEquals(amount, copay.plus(payer),
					"Patient and payer shares must add back to the original amount");
		}
	}
	
	@Test
	@DisplayName("TC-004-18 | Money compares by value ")
	public void testThatMoneyComparesByValue() {
		// Given: Two `Money.ofKobo(100)` instances
		Money money1 = Money.ofKobo(100);
		Money money2 = Money.ofKobo(100);

		// When: They are compared with `equals` and `hashCode`
		boolean equals = money1.equals(money2);
		int hashCode1 = money1.hashCode();
		int hashCode2 = money2.hashCode();

		// Then: They are equal with equal hash codes
		assertTrue(equals, "Two Money instances with the same value should be equal");
		assertEquals(hashCode1, hashCode2, "Two Money instances with the same value should have the same hash code");
	}
	
	@Test
	@DisplayName("TC-004-19 | No floating point near money")
	public void testThatNoFloatingPointTypesAreUsedInMoney() {
		// Given: The source code of the Money class
		Path moneySourcePath = Path.of("src/main/java/ng/ereach/shared/money/Money.java");

		try {
			String moneySourceCode = Files.readString(moneySourcePath);

			// When: Java code is checked for floating-point types, excluding comments
			String javaCode = moneySourceCode
					.replaceAll("(?s)/\\*.*?\\*/", "")
					.replaceAll("(?m)//.*$", "");
			boolean containsFloat = javaCode.matches("(?s).*\\bfloat\\b.*");
			boolean containsDouble = javaCode.matches("(?s).*\\bdouble\\b.*");
			boolean containsBigDecimal = javaCode.matches("(?s).*\\bBigDecimal\\b.*");

			// Then: No `float`, `double`, or `BigDecimal` appears in any constructor, factory, or method signature, and the stored value is a `long`
			assertFalse(containsFloat, "The Money class should not use float types");
			assertFalse(containsDouble, "The Money class should not use double types");
			assertFalse(containsBigDecimal, "The Money class should not use BigDecimal types");
		} catch (IOException e) {
			fail("Failed to read the Money source code: " + e.getMessage());
		}
	}
	
	@Test
	@DisplayName("TC-004-20 | A percentage too large for a long is refused, never wrapped")
	public void testThatPercentageTooLargeForLongIsRefused() {
		// Given: Two Money instances, one just below and one just above the threshold for a long
		Money moneyBelowThreshold = Money.ofKobo(922_337_203_685_477L);
		Money moneyAboveThreshold = Money.ofKobo(922_337_203_685_478L);
		
		// When: percentBps(10_000) is called on each
		Money resultBelowThreshold = moneyBelowThreshold.percentBps(10_000);
		ArithmeticException exceptionAboveThreshold = assertThrows(ArithmeticException.class, () -> moneyAboveThreshold.percentBps(10_000));
		
		// Then: The first returns the same amount; the second throws ArithmeticException
		assertEquals(moneyBelowThreshold, resultBelowThreshold,
				"The percentage of the amount just below the threshold should return the same amount");
		assertEquals("long overflow", exceptionAboveThreshold.getMessage(),
				"The percentage of the amount just above the threshold should throw an ArithmeticException with message 'long overflow'");
	}

	@Test
	@DisplayName("TC-004-21 | Money is immutable")
	public void testThatMoneyIsImmutable() {
		// Given: One Money instance whose value is observed before every operation
		Money original = Money.ofKobo(100);

		// When: Operations that produce different values are applied
		Money sum = original.plus(Money.ofKobo(50));
		assertEquals(100, original.kobo(), "plus must not change the original amount");

		Money difference = original.minus(Money.ofKobo(20));
		assertEquals(100, original.kobo(), "minus must not change the original amount");

		Money half = original.percentBps(5_000);

		// Then: Each result is correct and the original remains unchanged
		assertEquals(Money.ofKobo(150), sum);
		assertEquals(Money.ofKobo(80), difference);
		assertEquals(Money.ofKobo(50), half);
		assertEquals(Money.ofKobo(100), original,
				"Percentage calculation must not change the original amount");
		assertNotSame(original, sum, "plus must return a new Money instance");
		assertNotSame(original, difference, "minus must return a new Money instance");
		assertNotSame(original, half, "percentBps must return a new Money instance");
	}
}
