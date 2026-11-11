package com.hubspot.jinjava.lib.filter.yaml;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Turns the result of {@code fromyaml} into the text recorded under {@code
 * src/test/resources/outcomes/yaml-read-*} and renders it for comparison. The file format, the
 * block digests and the report of differences are those of {@link YamlWriteOutcomes}; this class
 * adds what is specific to reading: the value is shown with the Java type of every scalar (so
 * {@code 1<Integer>} differs from {@code 1<Long>} and from {@code "1"<String>}), a text longer
 * than {@value #MAX_INLINE_CHARS} characters (a long string, a deeply nested or very wide value)
 * is replaced by its length and SHA-256, and a failed read is recorded as the class of the
 * exception, which for {@code fromyaml} is {@code InvalidInputException} for every syntax error.
 *
 * <p>The expected files were recorded from {@code FromYamlFilter} on Jackson 2.20.1's {@code
 * YAMLMapper}, before the filter moved to Jackson 3. To record again run the test with {@code
 * -D}{@value #UPDATE_PROPERTY}{@code =true}; the comparison then passes and the files are
 * rewritten from whatever the filter returns now. Re-recording after the switch records the
 * output of the direct SnakeYAML event reader ({@code FromYamlReader}): do that only for a reviewed, intended change, never to make a failing comparison
 * pass.
 *
 * <p>A {@link Double} is shown by {@link #showDouble(double)}, not by {@link Double#toString}:
 * before JDK 19 {@code Double.toString} sometimes printed more digits than needed
 * (JDK-4511638), so the same value read back as {@code 1.27339423216531456E17} on JDK 17 and
 * {@code 1.2733942321653146E17} on JDK 21, where the expected files were recorded.
 */
final class YamlReadOutcomes {

  static final String UPDATE_PROPERTY = "jinjava.yaml.read.update";
  static final int MAX_INLINE_CHARS = 2000;
  /** The fewest digits JDK 19 and later print for a double: {@code 4.9E-324}, not {@code 5E-324}. */
  private static final int MIN_DOUBLE_DIGITS = 2;
  /** Enough digits to tell any two doubles apart. */
  private static final int MAX_DOUBLE_DIGITS = 17;
  /** JDK 19 and later print plain notation from this magnitude (inclusive) up to {@value #PLAIN_NOTATION_LIMIT}. */
  private static final double PLAIN_NOTATION_FLOOR = 1.0E-3;
  private static final double PLAIN_NOTATION_LIMIT = 1.0E7;

  private static final int MAX_SHOWN_STRING = 100;
  private static final String DIGEST_ALGORITHM = "SHA-256";

  private YamlReadOutcomes() {}

  static boolean isUpdating() {
    return Boolean.getBoolean(UPDATE_PROPERTY);
  }

  /** The reader for a case: the filter applied to the text the case supplies. */
  @SuppressWarnings("unchecked")
  static Function<Object, String> reader(Function<String, Object> filter) {
    return input -> summarize(show(filter.apply(((Supplier<String>) input).get())));
  }

  /** The value as text with the Java type of each scalar; long strings and byte arrays by length and hash. */
  static String show(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder sb = new StringBuilder("Map{");
      String separator = "";
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        sb
          .append(separator)
          .append(show(entry.getKey()))
          .append('=')
          .append(show(entry.getValue()));
        separator = ", ";
      }
      return sb.append('}').toString();
    }
    if (value instanceof List<?> list) {
      StringBuilder sb = new StringBuilder("List[");
      String separator = "";
      for (Object element : list) {
        sb.append(separator).append(show(element));
        separator = ", ";
      }
      return sb.append(']').toString();
    }
    if (value instanceof String string && string.length() > MAX_SHOWN_STRING) {
      return "String[len=" + string.length() + ",hash=" + string.hashCode() + "]<String>";
    }
    if (value instanceof byte[] bytes) {
      return "byte[" + bytes.length + ",hash=" + Arrays.hashCode(bytes) + "]";
    }
    String text = value instanceof Double d ? showDouble(d) : String.valueOf(value);
    return text + "<" + value.getClass().getSimpleName() + ">";
  }

  /**
   * What {@link Double#toString} prints on JDK 19 and later, on every JDK: of the decimals that
   * read back as {@code value}, the shortest (at least {@value #MIN_DOUBLE_DIGITS} digits) and,
   * among those, the closest to the exact value. Plain notation from {@code 1.0E-3} up to but not
   * including {@code 1.0E7}, otherwise {@code d.dddE<exponent>}; always one digit after the point.
   * NaN, the infinities and the zeros are the same on every JDK and are left to {@link
   * Double#toString}.
   */
  static String showDouble(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
      return Double.toString(value);
    }
    double magnitude = Math.abs(value);
    BigDecimal exact = new BigDecimal(magnitude);
    BigDecimal shortest = null;
    for (int digits = MIN_DOUBLE_DIGITS; shortest == null; digits++) {
      shortest = closestReadingBack(exact, digits, magnitude);
      if (shortest == null && digits == MAX_DOUBLE_DIGITS) {
        throw new IllegalStateException("No decimal of 17 digits reads back as " + value);
      }
    }
    String text = formatShortest(shortest.stripTrailingZeros(), magnitude);
    return value < 0 ? "-" + text : text;
  }

  /** The decimal of {@code digits} digits nearest to {@code exact} that reads back as {@code magnitude}, or null. */
  private static BigDecimal closestReadingBack(
    BigDecimal exact,
    int digits,
    double magnitude
  ) {
    BigDecimal nearest = exact.round(new MathContext(digits, RoundingMode.HALF_EVEN));
    // The interval of values reading back as magnitude is narrower below a power of two, so
    // the nearest decimal can be just outside it while its neighbour on the other side is inside.
    BigDecimal unit = BigDecimal.ONE.scaleByPowerOfTen(
      nearest.precision() - nearest.scale() - digits
    );
    BigDecimal best = null;
    for (BigDecimal candidate : List.of(
      nearest,
      nearest.add(unit),
      nearest.subtract(unit)
    )) {
      if (
        Double.parseDouble(candidate.toString()) == magnitude &&
        (best == null ||
          candidate.subtract(exact).abs().compareTo(best.subtract(exact).abs()) < 0)
      ) {
        best = candidate;
      }
    }
    return best;
  }

  private static String formatShortest(BigDecimal shortest, double magnitude) {
    if (magnitude >= PLAIN_NOTATION_FLOOR && magnitude < PLAIN_NOTATION_LIMIT) {
      String plain = shortest.toPlainString();
      return plain.contains(".") ? plain : plain + ".0";
    }
    String digits = shortest.unscaledValue().toString();
    int exponent = digits.length() - 1 - shortest.scale();
    String fraction = digits.length() > 1 ? digits.substring(1) : "0";
    return digits.charAt(0) + "." + fraction + "E" + exponent;
  }

  /** The text, or its length and SHA-256 if it is longer than {@link #MAX_INLINE_CHARS}. */
  static String summarize(String text) {
    if (text.length() <= MAX_INLINE_CHARS) {
      return text;
    }
    return "[" + text.length() + " chars, sha256 " + sha256(text) + "]";
  }

  private static String sha256(String text) {
    try {
      return HexFormat
        .of()
        .formatHex(
          MessageDigest
            .getInstance(DIGEST_ALGORITHM)
            .digest(text.getBytes(StandardCharsets.UTF_16BE))
        );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(DIGEST_ALGORITHM + " is required on every JVM", e);
    }
  }

  /** One line per case, in order: label, tab, outcome. Cases run one after the other so large inputs do not pile up in memory. */
  static List<String> fullLines(List<Case> cases, Function<Object, String> reader) {
    return cases
      .stream()
      .map(c ->
        YamlWriteOutcomes.escape(c.label()) +
        "\t" +
        YamlWriteOutcomes.outcome(reader, c.value())
      )
      .toList();
  }
}
