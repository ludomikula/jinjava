package com.hubspot.jinjava.golden;

import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.LegacyOverrides;
import com.hubspot.jinjava.el.ext.NamedParameter;
import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.InvalidInputException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.LazyExpression;
import com.hubspot.jinjava.interpret.NullValue;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.lib.filter.Filter;
import com.hubspot.jinjava.lib.filter.FromJsonFilter;
import com.hubspot.jinjava.lib.filter.PrettyPrintFilter;
import com.hubspot.jinjava.lib.filter.ToJsonFilter;
import com.hubspot.jinjava.objects.Namespace;
import com.hubspot.jinjava.objects.SafeString;
import com.hubspot.jinjava.objects.collections.PyList;
import com.hubspot.jinjava.objects.collections.SizeLimitingPyMap;
import com.hubspot.jinjava.objects.collections.SnakeCaseAccessibleMap;
import com.hubspot.jinjava.objects.date.PyishDate;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.AcronymBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.BigBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.CamelBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.Color;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.DeferredGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.EmptyBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.SmallBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.ThrowingBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.TooBigGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.WrappedDeferredGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.WrappedTooBigGetterBean;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.Holder;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.MixedBean;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.NullValueBean;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.PlainObject;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.ThrowingJsonValue;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.ThrowingList;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Corpus for {@link JsonFilterOutcomeTest}: values written through {@code tojson} and
 * {@code pprint} and strings read through {@code fromjson}, each in the default and the
 * snake case property naming configuration, and for {@code tojson} also with a
 * {@code maxOutputSize} that makes the filter write into a
 * {@link com.hubspot.jinjava.objects.serialization.LengthLimitingWriter}.
 *
 * <p>Every cell is reduced to an outcome class: the value text, {@link DeferredValueException},
 * {@link OutputTooBigException} (message, so with both sizes), {@link InvalidInputException}
 * (message, so with the reason), or the class name of any other throwable. The outcomes
 * recorded in {@code src/test/resources/outcomes} were taken on Jackson 2.20.1, before the
 * filters moved to Jackson 3, and the test compares against them.
 */
public final class JsonFilterOutcomeCorpus {

  static final String TOJSON = "tojson";
  static final String PPRINT = "pprint";
  static final String FROMJSON = "fromjson";
  static final String DEFAULT_NAMING = "default";
  static final String SNAKE_NAMING = "snake";
  static final String FIELD_SEPARATOR = "\t";
  static final long NO_OUTPUT_LIMIT = 0L;
  static final long TINY_OUTPUT_LIMIT = 20L;
  static final long LARGE_OUTPUT_LIMIT = 10_000L;
  static final int MAX_INLINE_LENGTH = 200;
  static final int REPORTED_HASH_LENGTH = 16;
  static final int LONG_STRING_LENGTH = 10_000;
  static final int DEEP_NESTING_UNDER_LIMIT = 600;
  static final int NESTING_AT_LIMIT = 1000;
  static final int NESTING_OVER_LIMIT = 1001;
  static final int NESTING_JUST_OVER_JACKSON_3_DEFAULT = 501;
  static final int DEPTH_STRUCTURE_LEVELS = 20;
  static final int WIDE_LIST_SIZE = 1000;
  static final int SHORT_LIST_SIZE = 30;
  static final int MAP_SIZE_LIMIT = 10;
  static final long OTB_MAX_SIZE = 10L;
  static final long OTB_SIZE = 20L;
  static final long EPOCH_MILLIS = 1_582_982_055_123L;
  static final String HASH_ALGORITHM = "SHA-256";

  private static final Pattern IDENTITY_HASH = Pattern.compile(
    "(?<=\\$[A-Za-z]{1,40})@[0-9a-f]{1,8}\\b"
  );
  private static final String IDENTITY_HASH_PLACEHOLDER = "@<identity-hash>";

  private JsonFilterOutcomeCorpus() {}

  /** All cells, one per line: context, item, outcome, separated by a tab. */
  static List<String> record() {
    List<String> lines = new ArrayList<>();
    for (String naming : Arrays.asList(DEFAULT_NAMING, SNAKE_NAMING)) {
      boolean snake = SNAKE_NAMING.equals(naming);
      addWrites(
        lines,
        TOJSON + "/" + naming + "/nolimit",
        new ToJsonFilter(),
        snake,
        NO_OUTPUT_LIMIT
      );
      addWrites(
        lines,
        TOJSON + "/" + naming + "/limit20",
        new ToJsonFilter(),
        snake,
        TINY_OUTPUT_LIMIT
      );
      addWrites(
        lines,
        TOJSON + "/" + naming + "/limit10000",
        new ToJsonFilter(),
        snake,
        LARGE_OUTPUT_LIMIT
      );
      addWrites(
        lines,
        PPRINT + "/" + naming,
        new PrettyPrintFilter(),
        snake,
        NO_OUTPUT_LIMIT
      );
      JinjavaInterpreter interpreter = interpreter(snake, NO_OUTPUT_LIMIT);
      for (Map.Entry<String, String> read : readItems().entrySet()) {
        lines.add(
          line(
            FROMJSON + "/" + naming,
            read.getKey(),
            outcome(() -> new FromJsonFilter().filter(read.getValue(), interpreter))
          )
        );
      }
    }
    return lines;
  }

  private static void addWrites(
    List<String> lines,
    String context,
    Filter filter,
    boolean snake,
    long maxOutputSize
  ) {
    JinjavaInterpreter interpreter = interpreter(snake, maxOutputSize);
    for (Map.Entry<String, Supplier<Object>> item : writeItems().entrySet()) {
      lines.add(
        line(
          context,
          item.getKey(),
          outcome(() -> filter.filter(item.getValue().get(), interpreter))
        )
      );
    }
  }

  private static String line(String context, String item, String outcome) {
    return context + FIELD_SEPARATOR + item + FIELD_SEPARATOR + outcome;
  }

  static JinjavaInterpreter interpreter(boolean snake, long maxOutputSize) {
    return new Jinjava(
      JinjavaConfig
        .newBuilder()
        .withMaxOutputSize(maxOutputSize)
        .withLegacyOverrides(
          LegacyOverrides.newBuilder().withUseSnakeCasePropertyNaming(snake).build()
        )
        .build()
    )
      .newInterpreter();
  }

  /** Runs the action and reduces what it returned or threw to an outcome class. */
  static String outcome(Supplier<Object> action) {
    try {
      return "VALUE " + describe(action.get());
    } catch (DeferredValueException e) {
      return "DEFERRED " + e.getMessage();
    } catch (OutputTooBigException e) {
      return "TOO_BIG " + e.getMessage();
    } catch (InvalidInputException e) {
      return "INVALID_INPUT " + e.getMessage();
    } catch (RuntimeException | StackOverflowError e) {
      return "OTHER " + e.getClass().getName();
    }
  }

  static String describe(Object value) {
    if (value == null) {
      return "null";
    }
    String text = IDENTITY_HASH
      .matcher(value.getClass().getName() + ":" + value)
      .replaceAll(IDENTITY_HASH_PLACEHOLDER);
    if (text.length() > MAX_INLINE_LENGTH) {
      return "<length " + text.length() + " sha256 " + digest(text) + ">";
    }
    return escape(text);
  }

  private static String digest(String text) {
    try {
      byte[] hash = MessageDigest
        .getInstance(HASH_ALGORITHM)
        .digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.substring(0, REPORTED_HASH_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(HASH_ALGORITHM + " is required by the JDK", e);
    }
  }

  /** Keeps one outcome on one line and makes control characters visible. */
  static String escape(String text) {
    StringBuilder sb = new StringBuilder();
    for (char c : text.toCharArray()) {
      if (c == '\\') {
        sb.append("\\\\");
      } else if (c == '\n') {
        sb.append("\\n");
      } else if (c == '\r') {
        sb.append("\\r");
      } else if (c == '\t') {
        sb.append("\\t");
      } else if (c < ' ' || c > '~') {
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static Map<String, Supplier<Object>> writeItems() {
    Map<String, Supplier<Object>> items = new LinkedHashMap<>();
    // maps, lists, arrays
    items.put("LinkedHashMap", () -> orderedMap("b", 1, "a", 2));
    items.put("HashMap mixed keys", JsonFilterOutcomeCorpus::mixedKeyMap);
    items.put("map with null value", () -> orderedMap("a", null, "b", 1));
    items.put("map with null key", () -> Collections.singletonMap(null, 1));
    items.put("Guava ImmutableMap", () -> ImmutableMap.of("z", 1, "y", 2));
    items.put("list", () -> new ArrayList<>(Arrays.asList(1, "two", 3.0, null)));
    items.put("int array", () -> new int[] { 1, 2, 3 });
    items.put("byte array", () -> new byte[] { 1, 2, 3, (byte) 255 });
    items.put("char", () -> 'c');
    items.put("1000 element list", JsonFilterOutcomeCorpus::wideList);
    items.put("30 short strings", JsonFilterOutcomeCorpus::shortStrings);
    // beans
    items.put("bean 3 properties", CamelBean::new);
    items.put("bean 6 properties", SmallBean::new);
    items.put("bean 8 properties", BigBean::new);
    items.put("bean getURL getXValue", AcronymBean::new);
    items.put("bean public field and is-getter", MixedBean::new);
    items.put("bean without properties", PlainObject::new);
    items.put("empty bean", EmptyBean::new);
    items.put("bean with NullValue property", NullValueBean::new);
    items.put(
      "list of beans",
      () -> new ArrayList<>(Arrays.asList(new BigBean(), new SmallBean()))
    );
    items.put("map holding a bean", () -> orderedMap("s", new SmallBean()));
    items.put("enum with toString override", () -> Color.RED);
    // dates and java.time, which Jackson 2 without jsr310 cannot write
    items.put("Date", () -> new Date(0));
    items.put("Instant", () -> Instant.EPOCH);
    items.put("ZonedDateTime", JsonFilterOutcomeCorpus::zonedDateTime);
    items.put("LocalDate", () -> LocalDate.of(2020, 1, 2));
    items.put("Duration", () -> Duration.ofSeconds(90));
    items.put("Instant in map", () -> orderedMap("i", Instant.EPOCH));
    items.put("LocalDate in map", () -> orderedMap("d", LocalDate.of(2020, 1, 2)));
    items.put("Duration in map", () -> orderedMap("d", Duration.ofSeconds(90)));
    items.put("Locale", () -> Locale.US);
    items.put("Locale in map", () -> orderedMap("l", Locale.US));
    items.put("PyishDate", () -> new PyishDate(EPOCH_MILLIS));
    // optionals and null
    items.put("Optional present", () -> Optional.of("x"));
    items.put("Optional empty", Optional::empty);
    items.put("OptionalInt", () -> OptionalInt.of(3));
    items.put("null", () -> null);
    items.put("NullValue", NullValue::instance);
    items.put("NullValue in map", () -> orderedMap("n", NullValue.instance()));
    items.put(
      "NullValue in list",
      () -> new ArrayList<>(Arrays.asList(1, NullValue.instance()))
    );
    // strings
    items.put("string with slash", () -> "a/b");
    items.put("string with script end tag", () -> "</script>");
    items.put("string e acute", () -> "\u00e9");
    items.put("string emoji", () -> "\uD83C\uDF89");
    items.put("string control character", () -> "\u0001");
    items.put("string line separator", () -> "\u2028");
    items.put("string space", () -> " ");
    items.put("string with quote", () -> "\"quote\"");
    items.put("string with apostrophe newline tab backslash", () -> "it's\n\t\\ end");
    items.put("long string", () -> "x".repeat(LONG_STRING_LENGTH));
    // numbers
    items.put("BigDecimal 1.10", () -> new BigDecimal("1.10"));
    items.put("BigDecimal 1E+3", () -> new BigDecimal("1E+3"));
    items.put("BigInteger", () -> new BigInteger("12345678901234567890"));
    items.put("Double 1.0", () -> 1.0d);
    items.put("Double NaN", () -> Double.NaN);
    items.put("Double infinity", () -> Double.POSITIVE_INFINITY);
    items.put("Long.MAX_VALUE", () -> Long.MAX_VALUE);
    items.put("Float", () -> 1.5f);
    // jinjava types
    items.put("SafeString", () -> new SafeString("<b>"));
    items.put(
      "LazyExpression evaluated",
      JsonFilterOutcomeCorpus::evaluatedLazyExpression
    );
    items.put(
      "LazyExpression not evaluated",
      () -> LazyExpression.of(() -> "val", "img")
    );
    items.put(
      "LazyExpression throwing DeferredValueException",
      () -> lazyThrowing(new DeferredValueException("lazy"))
    );
    items.put(
      "LazyExpression throwing OutputTooBigException",
      () -> lazyThrowing(new OutputTooBigException(OTB_MAX_SIZE, OTB_SIZE))
    );
    items.put(
      "LazyExpression throwing RuntimeException",
      () -> lazyThrowing(new IllegalStateException("lazy"))
    );
    items.put(
      "LazyExpression throwing wrapped DeferredValueException",
      () -> lazyThrowing(new RuntimeException(new DeferredValueException("lazy")))
    );
    items.put("Namespace", () -> new Namespace(orderedMap("a", 1)));
    items.put(
      "SnakeCaseAccessibleMap",
      () -> new SnakeCaseAccessibleMap(orderedMap("fooBar", 1))
    );
    items.put(
      "SizeLimitingPyMap",
      () -> new SizeLimitingPyMap(orderedMap("a", 1), MAP_SIZE_LIMIT)
    );
    items.put("PyList", () -> new PyList(new ArrayList<>(Arrays.asList(1, "a"))));
    items.put("NamedParameter", () -> new NamedParameter("n", "v"));
    items.put("Map.Entry", () -> new AbstractMap.SimpleEntry<>("k", "v"));
    // failures
    items.put("self-referencing list", JsonFilterOutcomeCorpus::selfReferencingList);
    items.put("self-referencing map", JsonFilterOutcomeCorpus::selfReferencingMap);
    items.put("bean getter throwing RuntimeException", ThrowingBean::new);
    items.put("bean getter throwing DeferredValueException", DeferredGetterBean::new);
    items.put(
      "bean getter throwing wrapped DeferredValueException",
      WrappedDeferredGetterBean::new
    );
    items.put("bean getter throwing OutputTooBigException", TooBigGetterBean::new);
    items.put(
      "bean getter throwing wrapped OutputTooBigException",
      WrappedTooBigGetterBean::new
    );
    items.put(
      "nested bean getter throwing DeferredValueException",
      () -> new Holder(new DeferredGetterBean())
    );
    items.put(
      "nested bean getter throwing wrapped DeferredValueException",
      () -> new Holder(new WrappedDeferredGetterBean())
    );
    items.put(
      "nested bean getter throwing OutputTooBigException",
      () -> new Holder(new TooBigGetterBean())
    );
    items.put(
      "nested bean getter throwing wrapped OutputTooBigException",
      () -> new Holder(new WrappedTooBigGetterBean())
    );
    items.put(
      "root value method throwing DeferredValueException",
      () -> new ThrowingJsonValue(new DeferredValueException("value"))
    );
    items.put(
      "root value method throwing OutputTooBigException",
      () -> new ThrowingJsonValue(new OutputTooBigException(OTB_MAX_SIZE, OTB_SIZE))
    );
    items.put(
      "root value method throwing RuntimeException",
      () -> new ThrowingJsonValue(new IllegalStateException("value"))
    );
    items.put(
      "root value method throwing wrapped DeferredValueException",
      () ->
        new ThrowingJsonValue(new RuntimeException(new DeferredValueException("value")))
    );
    items.put(
      "root value method throwing wrapped OutputTooBigException",
      () ->
        new ThrowingJsonValue(
          new RuntimeException(new OutputTooBigException(OTB_MAX_SIZE, OTB_SIZE))
        )
    );
    items.put(
      "nested value method throwing DeferredValueException",
      () -> orderedMap("v", new ThrowingJsonValue(new DeferredValueException("value")))
    );
    items.put(
      "root list throwing DeferredValueException",
      () -> new ThrowingList(new DeferredValueException("list"))
    );
    items.put(
      "root list throwing OutputTooBigException",
      () -> new ThrowingList(new OutputTooBigException(OTB_MAX_SIZE, OTB_SIZE))
    );
    items.put(
      "root list throwing RuntimeException",
      () -> new ThrowingList(new IllegalStateException("list"))
    );
    items.put(
      "root list throwing wrapped DeferredValueException",
      () -> new ThrowingList(new RuntimeException(new DeferredValueException("list")))
    );
    items.put(
      "root list throwing wrapped OutputTooBigException",
      () ->
        new ThrowingList(
          new RuntimeException(new OutputTooBigException(OTB_MAX_SIZE, OTB_SIZE))
        )
    );
    items.put(
      "nested list throwing DeferredValueException",
      () -> orderedMap("l", new ThrowingList(new DeferredValueException("list")))
    );
    // nesting
    items.put("600 nested lists", () -> nestedLists(DEEP_NESTING_UNDER_LIMIT));
    items.put("501 nested lists", () -> nestedLists(NESTING_JUST_OVER_JACKSON_3_DEFAULT));
    items.put("1000 nested lists", () -> nestedLists(NESTING_AT_LIMIT));
    items.put("1001 nested lists", () -> nestedLists(NESTING_OVER_LIMIT));
    items.put("20 levels with strings", () -> depthStructure(false));
    items.put("20 levels with strings and self reference", () -> depthStructure(true));
    return items;
  }

  static Map<String, String> readItems() {
    Map<String, String> items = new LinkedHashMap<>();
    items.put("object", "{\"a\":1}");
    items.put("array", "[1,2]");
    items.put("object with trailing token", "{\"a\":1} trailing");
    items.put("two arrays", "[1] [2]");
    items.put("1.0", "1.0");
    items.put("1e400", "1e400");
    items.put("1e2", "1e2");
    items.put("big integer", "12345678901234567890");
    items.put(
      "big integer in object",
      "{\"a\":1234567890123456789012345678901234567890}"
    );
    items.put("0.1", "0.1");
    items.put("e acute", "\"\u00e9\"");
    items.put("escaped slash", "\"x\\/y\"");
    items.put("truncated literal", "nul");
    items.put("NaN in object", "{\"a\":NaN}");
    items.put("duplicate keys", "{\"a\":1,\"a\":2}");
    items.put("null", "null");
    items.put("empty string", "");
    items.put("comment", "// c\n1");
    items.put("single quotes", "{'a':1}");
    items.put("unquoted name", "{a:1}");
    items.put("trailing comma in array", "[1,]");
    items.put("trailing comma in object", "{\"a\":1,}");
    items.put(
      "501 nested arrays",
      "[".repeat(NESTING_JUST_OVER_JACKSON_3_DEFAULT) +
      "]".repeat(NESTING_JUST_OVER_JACKSON_3_DEFAULT)
    );
    items.put(
      "1000 nested arrays",
      "[".repeat(NESTING_AT_LIMIT) + "]".repeat(NESTING_AT_LIMIT)
    );
    items.put(
      "1001 nested arrays",
      "[".repeat(NESTING_OVER_LIMIT) + "]".repeat(NESTING_OVER_LIMIT)
    );
    return items;
  }

  private static Map<String, Object> orderedMap(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private static Map<Object, Object> mixedKeyMap() {
    Map<Object, Object> map = new HashMap<>();
    map.put("s", 1);
    map.put(2, "two");
    map.put(3L, "three");
    map.put(true, "t");
    map.put('c', "ch");
    map.put(1.5, "d");
    return map;
  }

  private static List<Object> wideList() {
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < WIDE_LIST_SIZE; i++) {
      list.add(i);
    }
    return list;
  }

  private static List<Object> shortStrings() {
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < SHORT_LIST_SIZE; i++) {
      list.add("s" + i);
    }
    return list;
  }

  private static ZonedDateTime zonedDateTime() {
    return ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 6_000_000, ZoneOffset.UTC);
  }

  private static LazyExpression evaluatedLazyExpression() {
    LazyExpression expression = LazyExpression.of(() -> "val", "img");
    expression.get();
    return expression;
  }

  private static LazyExpression lazyThrowing(RuntimeException toThrow) {
    return LazyExpression.of(
      () -> {
        throw toThrow;
      },
      "img"
    );
  }

  private static List<Object> selfReferencingList() {
    List<Object> list = new ArrayList<>();
    list.add(list);
    return list;
  }

  private static Map<String, Object> selfReferencingMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("self", map);
    return map;
  }

  private static List<Object> nestedLists(int depth) {
    List<Object> root = new ArrayList<>();
    List<Object> current = root;
    for (int i = 0; i < depth; i++) {
      List<Object> next = new ArrayList<>();
      current.add(next);
      current = next;
    }
    return root;
  }

  /** The structure of PyishObjectMapperTest.itLimitsDepth. */
  private static List<Object> depthStructure(boolean selfReference) {
    List<Object> original = new ArrayList<>();
    List<Object> list = original;
    for (int i = 0; i < DEPTH_STRUCTURE_LEVELS; i++) {
      List<Object> next = new ArrayList<>();
      list.add("abcdefghijklmnopqrstuvwxyz");
      list.add(next);
      list = next;
    }
    list.add("a");
    if (selfReference) {
      list.add(original);
    }
    return original;
  }
}
