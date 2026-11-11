package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.util.Random;
import org.junit.Test;

/**
 * {@link YamlReadOutcomes#showDouble(double)} must print what {@link Double#toString} prints on JDK
 * 19 and later, on every JDK, because the recorded {@code yaml-read-*} files hold that text. The
 * pinned cases are the output of a real JDK 21 and run everywhere, among them the five values
 * whose text differs on JDK 17 ({@code JDK-4511638}); the comparisons with {@link
 * Double#toString} run only where it is the reference.
 */
public class YamlReadOutcomesDoubleTest {

  /** First JDK whose {@link Double#toString} gives the shortest decimal. */
  private static final int FIRST_SHORTEST_TOSTRING_JDK = 19;

  private static final long SEED = 20261112L;
  private static final int RANDOM_BIT_PATTERNS = 1_000_000;
  private static final int RANDOM_DECIMALS = 1_000_000;
  private static final int MAX_DECIMAL_EXPONENT = 30;

  private static final Object[][] PINNED = {
    // the doubles that printed with an extra digit on JDK 17
    { 1.2733942321653146E17, "1.2733942321653146E17" },
    { 9.117841718787979E16, "9.117841718787979E16" },
    { 6.256003005648814E17, "6.256003005648814E17" },
    { 6.310172304920564E17, "6.310172304920564E17" },
    { 4.248094831002291E17, "4.248094831002291E17" },
    // limits, and the two-digit minimum
    { Double.MIN_VALUE, "4.9E-324" },
    { 1.5E-323, "1.5E-323" },
    { Double.MAX_VALUE, "1.7976931348623157E308" },
    { 2.2250738585072014E-308, "2.2250738585072014E-308" },
    // plain and scientific notation around their limits
    { 1.0, "1.0" },
    { 100.0, "100.0" },
    { 123456.789, "123456.789" },
    { 9999999.999999998, "9999999.999999998" },
    { 1.0E7, "1.0E7" },
    { 0.1, "0.1" },
    { 1.0E-3, "0.001" },
    { 9.999999999999999E-4, "9.999999999999998E-4" },
    { 1.0E-5, "1.0E-5" },
    { 2.0E23, "2.0E23" },
    { 1.0E23, "1.0E23" },
    { -2.5, "-2.5" },
    { 1.2676506002282293E30, "1.2676506002282293E30" },
    // not rendered by this class
    { 0.0, "0.0" },
    { -0.0, "-0.0" },
    { Double.NaN, "NaN" },
    { Double.POSITIVE_INFINITY, "Infinity" },
    { Double.NEGATIVE_INFINITY, "-Infinity" },
  };

  @Test
  public void itPrintsWhatJava21PrintsForThePinnedValues() {
    for (Object[] pinned : PINNED) {
      double value = (Double) pinned[0];
      assertThat(YamlReadOutcomes.showDouble(value))
        .as("showDouble(%s), expected as printed by JDK 21", pinned[1])
        .isEqualTo(pinned[1]);
    }
  }

  @Test
  public void itShowsADoubleWithItsTypeAndNoOtherTypeChanged() {
    assertThat(YamlReadOutcomes.show(1.2733942321653146E17))
      .isEqualTo("1.2733942321653146E17<Double>");
    assertThat(YamlReadOutcomes.show(12L)).isEqualTo("12<Long>");
    assertThat(YamlReadOutcomes.show("1.5")).isEqualTo("1.5<String>");
  }

  @Test
  public void itMatchesDoubleToStringOnRandomBitPatterns() {
    assumeShortestToString();
    Random random = new Random(SEED);
    for (int i = 0; i < RANDOM_BIT_PATTERNS; i++) {
      assertSameAsDoubleToString(Double.longBitsToDouble(random.nextLong()));
    }
  }

  @Test
  public void itMatchesDoubleToStringOnRandomDecimalsOfEveryMagnitude() {
    assumeShortestToString();
    Random random = new Random(SEED + 1);
    for (int i = 0; i < RANDOM_DECIMALS; i++) {
      int exponent = random.nextInt(2 * MAX_DECIMAL_EXPONENT + 1) - MAX_DECIMAL_EXPONENT;
      assertSameAsDoubleToString(random.nextDouble() * Math.pow(10, exponent));
    }
  }

  @Test
  public void itMatchesDoubleToStringAtEveryPowerOfTwoAndItsNeighbours() {
    assumeShortestToString();
    for (
      int exponent = Double.MIN_EXPONENT - 52;
      exponent <= Double.MAX_EXPONENT;
      exponent++
    ) {
      double power = Math.scalb(1.0, exponent);
      assertSameAsDoubleToString(power);
      assertSameAsDoubleToString(Math.nextUp(power));
      assertSameAsDoubleToString(Math.nextDown(power));
      assertSameAsDoubleToString(-power);
    }
  }

  @Test
  public void itMatchesDoubleToStringAtEveryPowerOfTen() {
    assumeShortestToString();
    for (int exponent = -323; exponent <= 308; exponent++) {
      double power = Double.parseDouble("1.0E" + exponent);
      assertSameAsDoubleToString(power);
      assertSameAsDoubleToString(Math.nextUp(power));
      assertSameAsDoubleToString(Math.nextDown(power));
    }
  }

  private static void assumeShortestToString() {
    assumeTrue(
      "Double.toString prints the shortest decimal from JDK 19, this is JDK " +
      Runtime.version().feature(),
      Runtime.version().feature() >= FIRST_SHORTEST_TOSTRING_JDK
    );
  }

  private static void assertSameAsDoubleToString(double value) {
    assertThat(YamlReadOutcomes.showDouble(value))
      .as(
        "showDouble of the double with bits %s",
        Long.toHexString(Double.doubleToLongBits(value))
      )
      .isEqualTo(Double.toString(value));
  }
}
