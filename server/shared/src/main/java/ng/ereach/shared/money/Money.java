package ng.ereach.shared.money;

import java.util.Objects;

/**
 * An exact amount of Nigerian money, counted in whole kobo.
 *
 * <p>Every amount in e-Reach, from the price of a consultation to a cash-up total, is a
 * {@code Money}. The value is a whole number of kobo held in a {@code long}, so
 * {@code Money.ofKobo(400_000)} is four thousand naira. A {@code Money} never changes after it
 * is made; {@link #plus(Money)}, {@link #minus(Money)} and {@link #percentBps(int)} each return
 * a new {@code Money} and leave the original as it was. Two amounts are equal when they hold the
 * same number of kobo.
 *
 * <h2>Why We Built Our Own Money Module</h2>
 *
 * <p>A hospital bill is a promise about money, so the numbers on it must be exactly right, every
 * time and on every screen. A computer, left to its usual habits, does not always keep that
 * promise. Most programming languages store numbers that have a decimal point in a way that
 * cannot hold ten kobo exactly, in the same way that a calculator cannot write one third exactly
 * as a decimal. Each error is tiny, but when enough of them are added together a bill can come out
 * a kobo wrong, and a cash drawer can fail to balance at the end of the day with nobody able to
 * say why.
 *
 * <p>This class avoids the problem by never using a decimal point at all. It counts money in
 * kobo, the smallest unit of the naira, and keeps that count as a whole number, so four thousand
 * naira is held as 400,000 kobo. Whole numbers are added and subtracted exactly, and the answer
 * is always the one a careful cashier would write down. Nigerian payment companies such as
 * Paystack count money in the same way, so amounts can pass between e-Reach and those services
 * without being converted.
 *
 * <p>The whole number we use can count up to about ninety-two quadrillion naira, which is far
 * more than any hospital will ever handle. If a calculation does go past that limit, which could
 * only happen through a mistake somewhere else in the program, this class stops and reports an
 * error, instead of quietly producing a wrong amount that someone might pay.
 *
 * <p>Java already offers two other ways to handle money, and we considered both. The first,
 * {@code BigDecimal}, also keeps amounts exact, but every time it divides it asks the programmer
 * to choose how to round, and it treats 1.0 and 1.00 as two different numbers. The second, the
 * Java Money library {@code Moneta}, handles many currencies, exchange rates and ways of displaying amounts in
 * different countries. e-Reach works in one currency, and it needs one rounding rule that applies
 * everywhere. Either choice would bring in more than we need, and we would still have to write
 * that rule ourselves.
 *
 * <p>That rule comes from the product requirements, in feature F13. When an insurer or an HMO
 * covers a patient, the patient often pays a fixed share of the bill, called the co-payment, and
 * the insurer pays the rest. The share is written in basis points, where 10,000 basis points make
 * one hundred percent, so a share such as 12.5% is the whole number 1,250 and not a fraction. The
 * co-payment is the bill multiplied by the share and divided by 10,000, rounded to the nearest
 * kobo, with an exact half rounded up. Because the rule lives in this one class, every screen,
 * report and claim in e-Reach works out a co-payment the same way, and the patient's share and
 * the insurer's share always add back to the full bill.
 *
 * <h2>Rules this class keeps</h2>
 *
 * <ul>
 *   <li>The amount is always a whole number of kobo. No {@code float}, {@code double} or
 *       {@code BigDecimal} appears anywhere in this class (PRD rule 8).</li>
 *   <li>Arithmetic never wraps around. A result too large for a {@code long} throws
 *       {@link ArithmeticException}; it never turns into a negative or a smaller amount.</li>
 *   <li>An amount may be negative, so that adjustments and corrections can be recorded, but a
 *       percentage of a negative amount is refused.</li>
 *   <li>This class does not format amounts for display. Showing "&#8358;4,000.00" on a screen
 *       or a receipt is the job of the code that draws that screen or receipt.</li>
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Money bill  = Money.ofKobo(400_000);      // NGN 4,000.00
 * Money copay = bill.percentBps(1_000);     // 10%  -> 40,000 kobo, NGN 400.00
 * Money payer = bill.minus(copay);          // 360,000 kobo, NGN 3,600.00
 * }</pre>
 *
 * @param kobo the amount as a whole number of kobo, where 100 kobo make one naira
 * @since 0.0.1
 */
public record Money(long kobo) {
	
	/** The number of basis points in one hundred percent. */
	private static final long FULL_SHARE_BPS = 10_000L;
	
	/** Half of {@link #FULL_SHARE_BPS}, added before dividing so the result rounds half up. */
	private static final long HALF_SHARE_BPS = FULL_SHARE_BPS / 2;
	
	/**
	 * Returns an amount of the given number of kobo.
	 *
	 * <p>The argument is kobo, not naira: four thousand naira is {@code ofKobo(400_000)}.
	 *
	 * @param kobo the amount as a whole number of kobo
	 * @return the amount
	 */
	public static Money ofKobo(long kobo) {
		return new Money(kobo);
	}
	
	/**
	 * Returns the sum of this amount and another.
	 *
	 * @param other the amount to add
	 * @return a new amount equal to this amount plus {@code other}
	 * @throws NullPointerException if {@code other} is {@code null}
	 * @throws ArithmeticException  if the sum is too large or too small to hold in a {@code long}
	 */
	public Money plus(Money other) {
		Objects.requireNonNull(other, "other");
		return new Money(Math.addExact(kobo, other.kobo));
	}
	
	/**
	 * Returns this amount with another taken away.
	 *
	 * @param other the amount to subtract
	 * @return a new amount equal to this amount minus {@code other}
	 * @throws NullPointerException if {@code other} is {@code null}
	 * @throws ArithmeticException  if the difference is too large or too small to hold in a
	 *                              {@code long}
	 */
	public Money minus(Money other) {
		Objects.requireNonNull(other, "other");
		return new Money(Math.subtractExact(kobo, other.kobo));
	}
	
	/**
	 * Returns the given share of this amount, rounded half up to the nearest kobo.
	 *
	 * <p>This is the co-payment rule from PRD feature F13, step 6:
	 * {@code (amount × basisPoints + 5000) / 10000}, in whole-number division. 10,000 basis
	 * points are one hundred percent, so {@code percentBps(1_000)} is ten percent. Because the
	 * share is never more than the whole, the result is never more than this amount, and
	 * {@code amount.minus(amount.percentBps(bps))} is never negative.
	 *
	 * <table>
	 *   <caption>Rounding examples</caption>
	 *   <tr><th>Amount (kobo)</th><th>Basis points</th><th>Result (kobo)</th></tr>
	 *   <tr><td>400,000</td><td>1,000</td><td>40,000</td></tr>
	 *   <tr><td>15</td><td>5,000</td><td>8 (7.5 rounds up)</td></tr>
	 *   <tr><td>14</td><td>5,000</td><td>7</td></tr>
	 *   <tr><td>1</td><td>4,999</td><td>0</td></tr>
	 * </table>
	 *
	 * @param basisPoints the share to take, from 0 (nothing) to 10,000 (the whole amount)
	 * @return a new amount equal to the share of this amount
	 * @throws IllegalArgumentException if {@code basisPoints} is below 0 or above 10,000, or if
	 *                                  this amount is negative
	 * @throws ArithmeticException      if this amount is so large that multiplying it by
	 *                                  {@code basisPoints} does not fit in a {@code long}; the
	 *                                  largest amount that always fits is 922,337,203,685,477 kobo
	 */
	public Money percentBps(int basisPoints) {
		if (basisPoints < 0 || basisPoints > FULL_SHARE_BPS) {
			throw new IllegalArgumentException(
					"basisPoints must be between 0 and 10000, but was " + basisPoints);
		}
		if (kobo < 0) {
			throw new IllegalArgumentException(
					"cannot take a percentage of a negative amount: " + kobo + " kobo");
		}
		long scaled = Math.multiplyExact(kobo, (long) basisPoints);
		long rounded = Math.addExact(scaled, HALF_SHARE_BPS);
		return new Money(rounded / FULL_SHARE_BPS);
	}
}