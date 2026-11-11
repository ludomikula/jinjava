package com.hubspot.jinjava.lib.filter.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TimeZone;
import java.util.UUID;

/**
 * The inputs of the {@code toyaml} outcome tests. Everything here is generated
 * deterministically (fixed seeds, no hash ordering), so the expected outputs recorded on
 * Jackson 2 stay valid for every later run. The sets are:
 * <ul>
 *   <li>{@link #corpus()}: 119 hand-picked values (scalars that need quoting, numbers, beans,
 *       dates, enums, arrays, keys that need quoting, {@code Optional}, {@code java.time}).</li>
 *   <li>{@link #extras()}: more JDK and bean types, nesting depth around Jackson 2's limit.</li>
 *   <li>{@link #namingBeans()}: 14 beans whose getter names differ between Jackson 2 and 3.</li>
 *   <li>{@link #targeted()}: 21,688 values around U+0085, U+2028 and U+2029, the characters
 *       SnakeYAML 2.4 and snakeyaml-engine treat differently.</li>
 *   <li>{@link #exhaustion()}: 320 values holding every private-use code point (and more),
 *       which exhaust a naive choice of mask character.</li>
 *   <li>{@link #chunkEdges()}: 10,640 long values with a surrogate pair at a read chunk edge.</li>
 *   <li>{@link #fuzz(int)}: seeded random nested structures.</li>
 * </ul>
 */
public final class YamlWriteCorpus {

  /** A labelled input value. */
  public record Case(String label, Object value) {}

  public static final String NEL = "\u0085";
  public static final String LS = "\u2028";
  public static final String PS = "\u2029";
  public static final String BOM = "\ufeff";
  private static final String PRIVATE_USE_FIRST = "\ue000";
  private static final String PRIVATE_USE_SECOND = "\ue001";
  private static final String PRIVATE_USE_THIRD = "\ue002";
  private static final String PRIVATE_USE_FOURTH = "\ue003";

  /** Number of fuzz structures; structure {@code i} is built from seed {@code FUZZ_SEED_BASE + i}. */
  public static final int FUZZ_SIZE = 100_000;
  public static final long FUZZ_SEED_BASE = 0x5eed0000L;

  private static final String[] RAW_LINE_BREAKS = { NEL, LS, PS };
  private static final String[] ATOMS = {
    NEL,
    LS,
    PS,
    "\n",
    "\t",
    "'",
    "\"",
    "#",
    ": ",
    " ",
    "  ",
    "\\N",
    "\\L",
    "\\P",
    "\\",
    "a",
    "yes",
    "1",
    "-",
    "?",
    "é",
    "\r",
    "\u00a0",
    "",
    "😀",
    ":",
    "---",
    "&",
    "|",
    ">",
  };
  private static final String[] CORE_ATOMS = {
    NEL,
    LS,
    PS,
    "\n",
    "'",
    "\"",
    "#",
    ": ",
    " ",
    "\\N",
    "\\L",
    "\\P",
    "a",
    "-",
  };
  private static final String[] FUZZ_ALPHABET = {
    NEL,
    LS,
    PS,
    "\n",
    "\t",
    "\r",
    " ",
    " ",
    " ",
    "'",
    "\"",
    "#",
    ":",
    ": ",
    "-",
    "?",
    "\\",
    "a",
    "b",
    "x",
    "1",
    "é",
    "\u00a0",
    "|",
    ">",
    "{",
    "[",
    ",",
    "&",
    "*",
    "!",
    "%",
    "@",
    "`",
    BOM,
    "😀",
    "\u0007",
    "\u007f",
    "yes",
    "null",
    "\\N",
    "\\L",
    "\\P",
    LS + PS,
    NEL + "\n",
  };

  private static final int SHORT_STRING_PERCENT = 8;
  private static final int STRING_PERCENT_BASE = 10;
  private static final int SHORT_STRING_BOUND = 6;
  private static final int LONG_STRING_BOUND = 80;
  private static final int MAX_FUZZ_DEPTH = 4;
  private static final int FUZZ_KINDS_AT_MAX_DEPTH = 5;
  private static final int FUZZ_KINDS = 8;
  private static final int FUZZ_INT_BOUND = 100;
  private static final int FUZZ_COLLECTION_BOUND = 4;
  private static final int FUZZ_INT_KEY_ONE_IN = 12;
  private static final int FUZZ_INT_KEY_BOUND = 50;

  private static final int FIRST_NON_ASCII_FIXTURE = 0x100;
  private static final int MAX_CODE_POINT = 0x10FFFF;
  private static final int SUPPLEMENTARY_FIRST = 0x10000;
  private static final int PLANE_MASK = 0xFFFF;
  private static final int PLANE_LAST_USABLE = 0xFFFD;
  private static final int LS_CODE_POINT = 0x2028;
  private static final int PS_CODE_POINT = 0x2029;
  private static final int BOM_CODE_POINT = 0xFEFF;
  private static final int[] SURROGATES = { 0xD800, 0xDFFF };
  private static final int BMP_PRIVATE_USE_FIRST = 0xE000;
  private static final int BMP_PRIVATE_USE_LAST = 0xF8FF;
  private static final int BMP_LAST_PRINTABLE = 0xFFFD;
  private static final int PLANE_15_FIRST = 0xF0000;
  private static final int PLANE_15_LAST = 0xFFFFD;
  private static final int PLANE_16_FIRST = 0x100000;
  private static final int PLANE_16_LAST = 0x10FFFD;

  private static final int[] LONG_KEY_LENGTHS = {
    100,
    120,
    126,
    127,
    128,
    129,
    130,
    200,
  };
  private static final int MAX_TARGETED_LABEL = 40;
  private static final int LABEL_HEAD = 18;
  private static final int LABEL_TAIL = 12;
  private static final int ALL_SHAPES_EVERY = 3;
  private static final int[] BYTE_ARRAY_LENGTHS = { 1, 3, 57, 58, 100, 200, 1000 };

  private static final int[][] CHUNK_RANGES = {
    { 1000, 1060 },
    { 2020, 2060 },
    { 3070, 3100 },
  };
  private static final int SPACE_EVERY = 7;
  private static final String SMILE = "😀";

  private static final int NESTED_OK_DEPTH = 700;
  private static final int NESTED_TOO_DEEP = 1100;

  private YamlWriteCorpus() {}

  // ---------------------------------------------------------------------------------- corpus

  /** The 119 hand-picked values; the set the first Jackson 3 candidates were compared on. */
  public static List<Case> corpus() {
    List<Case> cases = new ArrayList<>();
    String[] strings = {
      "yes",
      "no",
      "on",
      "off",
      "y",
      "n",
      "true",
      "True",
      "null",
      "~",
      "",
      "1:30",
      "123",
      "0755",
      "0x1F",
      "1_000",
      "1e3",
      "2001-12-14",
      "a: b",
      "#x",
      "- x",
      "multi\nline",
      "trailing\n",
      "  leading space",
      "trailing space ",
      "héllo ✓ 日本 🎉",
      "tab\there",
      "quote ' and \"",
      "a long string of more than one hundred characters with spaces so that line splitting would be triggered if it is enabled at all, yes",
      "@at",
      "`tick",
      "%pct",
      "*star",
      "&amp",
      "!bang",
      "{brace",
      "[bracket",
      ">gt",
      "|pipe",
      "<<",
      "=",
      "-",
      "?",
      "0o755",
      ".inf",
      "\u0007bell",
      " sep",
    };
    for (String s : strings) {
      keyed(cases, "String " + s, s);
    }
    keyed(cases, "Integer 5", 5);
    keyed(cases, "Long 9007199254740993", 9007199254740993L);
    keyed(cases, "Double 1.5", 1.5);
    keyed(cases, "Double 1.0E20", 1.0E20);
    keyed(cases, "BigDecimal 1.10", new BigDecimal("1.10"));
    keyed(cases, "Boolean true", true);
    keyed(cases, "null", null);
    keyed(cases, "List.of(1,yes)", List.of(1, "yes"));
    keyed(cases, "nested map", map("b", map("c", 1)));
    keyed(cases, "map with null value", map("x", null));
    Map<Object, Object> integerKey = new LinkedHashMap<>();
    integerKey.put(1, "one");
    keyed(cases, "map with Integer key", integerKey);
    keyed(cases, "int[]{4,1,2}", new int[] { 4, 1, 2 });
    Map<String, Object> arrayAndString = new LinkedHashMap<>();
    arrayAndString.put("testArray", new int[] { 4, 1, 2 });
    arrayAndString.put("testString", "testString");
    top(cases, "{testArray:[4,1,2], testString}", arrayAndString);
    keyed(cases, "POJO zeta/alpha/mid", new Pojo());
    keyed(
      cases,
      "ZonedDateTime",
      ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)
    );
    keyed(cases, "Date(0)", new Date(0));
    keyed(cases, "enum toString!=name", Color.GREEN);
    keyed(cases, "Optional.of(x)", Optional.of("x"));
    keyed(cases, "Optional.empty()", Optional.empty());
    keyed(cases, "empty POJO", new Empty());
    String[] keys = {
      "yes",
      "1",
      "a: b",
      "",
      "multi\nline",
      "x".repeat(200),
      "~",
      "null",
      "#c",
      "- k",
    };
    for (String key : keys) {
      top(cases, "key [" + (key.length() > 20 ? "x*200" : key) + "]", map(key, 1));
    }
    String[] extraStrings = {
      "cr\rlf",
      NEL + "nel",
      LS + "ls",
      "\u007fdel",
      "😀",
      "back\\slash",
      "'",
      "\"",
      "   ",
      "\u00a0nbsp",
      BOM + "bom",
      "a\n\nb",
      "x".repeat(300),
      "word ".repeat(80),
    };
    for (String s : extraStrings) {
      keyed(
        cases,
        "String extra " + (s.length() > 20 ? s.substring(0, 5) + "..." + s.length() : s),
        s
      );
    }
    keyed(cases, "BigInteger", new BigInteger("123456789012345678901234567890"));
    keyed(cases, "Float 1.5f", 1.5f);
    keyed(cases, "Short", (short) 7);
    keyed(cases, "Character x", 'x');
    keyed(cases, "byte[]{1,2,3}", new byte[] { 1, 2, 3 });
    keyed(cases, "UUID", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    keyed(cases, "List of maps", List.of(Map.of("a", 1), Map.of("b", List.of(1, 2))));
    keyed(cases, "empty List", List.of());
    keyed(cases, "empty Map", Map.of());
    keyed(cases, "URI", URI.create("http://x/y"));
    keyed(cases, "Class", String.class);
    keyed(cases, "Double NaN", Double.NaN);
    keyed(cases, "Double +Inf", Double.POSITIVE_INFINITY);
    keyed(cases, "Duration", Duration.ofSeconds(5));
    keyed(cases, "Instant", Instant.EPOCH);
    keyed(cases, "nested list in list", List.of(List.of(1, 2), List.of()));
    keyed(cases, "map in map in list", List.of(Map.of("a", Map.of("b", 1))));
    keyed(cases, "Calendar", epochCalendar());
    keyed(cases, "StringBuilder", new StringBuilder("sb"));
    keyed(cases, "char[]", new char[] { 'a', 'b' });
    keyed(cases, "Locale", Locale.US);
    top(cases, "String yes", "yes");
    top(cases, "Integer 5", 5);
    top(cases, "null", null);
    top(cases, "List.of(1,yes)", List.of(1, "yes"));
    top(cases, "empty Map", new LinkedHashMap<>());
    top(cases, "POJO", new Pojo());
    top(cases, "multi-key map", map("a", 1));
    return Collections.unmodifiableList(cases);
  }

  /** More JDK types, beans holding rejected types, and nesting around Jackson 2's limit. */
  public static List<Case> extras() {
    List<Case> cases = new ArrayList<>();
    keyed(cases, "OptionalInt", OptionalInt.of(3));
    keyed(cases, "LocalDate", LocalDate.of(2020, 1, 2));
    keyed(cases, "bean holding Optional", new OptionalBean());
    keyed(cases, "bean holding Locale", new LocaleBean());
    keyed(cases, "bean holding LocalDate", new LocalDateBean());
    keyed(cases, "TimeZone", TimeZone.getTimeZone("UTC"));
    keyed(cases, "Currency", java.util.Currency.getInstance("EUR"));
    keyed(cases, "AtomicInteger", new java.util.concurrent.atomic.AtomicInteger(4));
    keyed(cases, "AtomicBoolean", new java.util.concurrent.atomic.AtomicBoolean(true));
    keyed(cases, "BitSet", java.util.BitSet.valueOf(new long[] { 5L }));
    keyed(cases, "Pattern", java.util.regex.Pattern.compile("a+b"));
    keyed(cases, "Charset", java.nio.charset.StandardCharsets.UTF_8);
    keyed(cases, "Object[]", new Object[] { 1, "two", null, 3.5 });
    keyed(cases, "long[]", new long[] { 1L, 2L });
    keyed(cases, "boolean[]", new boolean[] { true, false });
    keyed(cases, "double[]", new double[] { 1.5, 2.0 });
    Set<String> set = new LinkedHashSet<>(List.of("z", "a"));
    keyed(cases, "LinkedHashSet", set);
    keyed(cases, "java.sql.Timestamp", new java.sql.Timestamp(0L));
    keyed(cases, "Object with no properties", new Object[0]);
    keyed(cases, "map with enum key", map(Color.RED, 1));
    keyed(cases, "map with null key value", map("n", null));
    top(cases, "nested " + NESTED_OK_DEPTH, nestedLists(NESTED_OK_DEPTH));
    top(cases, "nested " + NESTED_TOO_DEEP, nestedLists(NESTED_TOO_DEEP));
    top(cases, "nested maps " + NESTED_OK_DEPTH, nestedMaps(NESTED_OK_DEPTH));
    return Collections.unmodifiableList(cases);
  }

  private static Object nestedLists(int depth) {
    Object value = "leaf";
    for (int i = 0; i < depth; i++) {
      value = List.of(value);
    }
    return value;
  }

  private static Object nestedMaps(int depth) {
    Object value = "leaf";
    for (int i = 0; i < depth; i++) {
      value = map("k", value);
    }
    return value;
  }

  private static GregorianCalendar epochCalendar() {
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(0);
    return calendar;
  }

  private static void keyed(List<Case> cases, String label, Object value) {
    cases.add(new Case("k=" + label, map("k", value)));
  }

  private static void top(List<Case> cases, String label, Object value) {
    cases.add(new Case("TOP " + label, value));
  }

  private static Map<Object, Object> map(Object key, Object value) {
    Map<Object, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static Map<String, Object> mapOf(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  // ---------------------------------------------------------------------------- naming beans

  /** The 14 bean cases whose property names Jackson 3 derives differently from Jackson 2. */
  public static List<Case> namingBeans() {
    List<Case> cases = new ArrayList<>();
    cases.add(new Case("getURL", new UrlBean()));
    cases.add(new Case("getXValue", new XValueBean()));
    cases.add(new Case("getURLValue+getIPAddress", new UrlValueBean()));
    cases.add(new Case("isActive", new ActiveBean()));
    cases.add(new Case("getA", new ABean()));
    cases.add(new Case("getaB", new AbBean()));
    cases.add(new Case("getABC", new AbcBean()));
    cases.add(new Case("public fields Name/fooBar/URL", new FieldBean()));
    cases.add(new Case("@JsonProperty-renamed getter", new RenamedBean()));
    cases.add(new Case("8-property bean", new EightBean()));
    cases.add(new Case("bean in map", Map.of("b", new UrlBean())));
    cases.add(new Case("bean in list", List.of(new XValueBean(), new UrlBean())));
    cases.add(new Case("holder (nested bean + list of beans)", new Holder()));
    cases.add(new Case("bean with LS/NEL values", new LineBreakBean()));
    return Collections.unmodifiableList(cases);
  }

  // ------------------------------------------------------------------------------- targeted

  /** 21,688 values around U+0085, U+2028 and U+2029 in every position and shape. */
  public static List<Case> targeted() {
    List<Case> out = new ArrayList<>();
    java.util.Set<String> seen = new LinkedHashSet<>();
    for (String a : ATOMS) {
      seen.add(a);
    }
    for (String a : ATOMS) {
      for (String b : ATOMS) {
        seen.add(a + b);
      }
    }
    for (String raw : RAW_LINE_BREAKS) {
      for (String other : ATOMS) {
        seen.add(raw + "x" + other);
        seen.add("x" + other + raw + "y");
        seen.add("x" + raw + other);
        seen.add(other + "x" + raw + "y" + other);
        for (int n = 2; n <= 4; n++) {
          seen.add(raw.repeat(n));
          seen.add("a" + raw.repeat(n) + "b");
          seen.add(" " + raw.repeat(n));
          seen.add(raw.repeat(n) + other);
        }
      }
    }
    seen.add(NEL + LS + PS);
    seen.add("a" + NEL + "\n" + LS + "\t" + PS + "b");
    seen.add(PS + LS + NEL + " ");
    seen.add(" " + NEL + " " + LS + " " + PS + " ");
    for (String text : new String[] { "\\N", "\\L", "\\P" }) {
      seen.add(text);
      seen.add("a" + text + "b");
      seen.add(" " + text);
      seen.add(text + " ");
      for (String raw : RAW_LINE_BREAKS) {
        seen.add(text + raw);
        seen.add(raw + text);
        seen.add(text + raw + text);
        seen.add("a" + text + raw + "b");
        seen.add(raw + "\\" + text);
        seen.add("\\" + raw + text.substring(1));
      }
    }
    seen.add("\\N\\L\\P");
    seen.add("\\\\L");
    seen.add("\\\\" + LS);
    seen.add("\\" + LS);
    seen.add("\\" + NEL);
    seen.add("\\" + PS);
    for (int n : LONG_KEY_LENGTHS) {
      for (String raw : RAW_LINE_BREAKS) {
        String x = "x".repeat(n);
        seen.add(raw + x);
        seen.add(x + raw);
        seen.add(x + raw + x);
        seen.add(x.substring(0, n / 2) + raw.repeat(2) + x.substring(n / 2));
      }
    }
    for (String raw : RAW_LINE_BREAKS) {
      String w = "word ".repeat(30);
      seen.add(w + raw);
      seen.add(raw + w);
      seen.add(w + raw + w);
      seen.add(w.repeat(2) + raw + " " + raw + " " + w);
      seen.add(("ab" + raw + " ").repeat(60));
      seen.add(("a " + raw).repeat(50));
      seen.add((raw + " ").repeat(45));
      seen.add((" " + raw).repeat(45));
      seen.add(w + "\n" + raw + w);
      seen.add(w + "'" + raw + "\"" + w);
      seen.add("# " + w + raw);
      seen.add(w + ": " + raw + w);
    }
    for (String raw : new String[] { LS, PS }) {
      seen.add(PRIVATE_USE_FIRST);
      seen.add(raw + PRIVATE_USE_FIRST);
      seen.add(PRIVATE_USE_FIRST + raw + PRIVATE_USE_SECOND);
      seen.add(PRIVATE_USE_SECOND + raw);
      seen.add(PRIVATE_USE_FIRST + PRIVATE_USE_SECOND);
      seen.add(PRIVATE_USE_THIRD + PRIVATE_USE_FOURTH + raw);
    }
    int index = 0;
    for (String s : seen) {
      index++;
      shapes(out, s, s.length() <= MAX_TARGETED_LABEL || index % ALL_SHAPES_EVERY == 0);
    }
    for (String a : CORE_ATOMS) {
      for (String b : CORE_ATOMS) {
        for (String c : CORE_ATOMS) {
          shapes(out, a + b + c, false);
        }
      }
    }
    out.add(
      new Case(
        "int/bool neighbours",
        mapOf(
          "i",
          1,
          "b",
          true,
          "s" + LS,
          "v" + LS,
          "n",
          null,
          LS,
          2,
          NEL,
          false,
          PS,
          3.5
        )
      )
    );
    out.add(
      new Case(
        "int/bool neighbours 2",
        mapOf("a", List.of(1, true, "x" + NEL, "y" + LS, "z" + PS, 2L, false))
      )
    );
    for (int n : BYTE_ARRAY_LENGTHS) {
      out.add(new Case("byte[" + n + "] value", mapOf("k", new byte[n])));
    }
    out.add(
      new Case(
        "byte[100] key+text",
        mapOf("k" + LS, new byte[100], "t", "multi\nline\n" + LS)
      )
    );
    out.add(new Case("long multi-line string", mapOf("k", "line1\nline2\n".repeat(60))));
    out.add(
      new Case(
        "long multi-line string+LS",
        mapOf("k", ("line1\n" + LS + "line2\n").repeat(60))
      )
    );
    out.add(new Case("trailing newline string", mapOf("k", "abc\n")));
    out.add(new Case("leading-space multi-line", mapOf("k", " a\n b\n")));
    return Collections.unmodifiableList(out);
  }

  private static void shapes(List<Case> out, String s, boolean all) {
    String l = label(s);
    out.add(new Case("val {k:S} S=" + l, mapOf("k", s)));
    out.add(new Case("key {S:1} S=" + l, mapOf(s, 1)));
    if (!all) {
      return;
    }
    out.add(new Case("key+val {S:S} S=" + l, mapOf(s, s)));
    out.add(new Case("top-level S=" + l, s));
    out.add(new Case("list [S,1,true] S=" + l, List.of(s, 1, true)));
    out.add(
      new Case(
        "list of maps S=" + l,
        List.of(mapOf(s, 1, "n", true), mapOf("a", s, "b", 2))
      )
    );
    out.add(
      new Case("map of lists S=" + l, mapOf("x", List.of(s, 2), s, List.of(true, s)))
    );
    out.add(
      new Case(
        "3-deep S=" + l,
        mapOf("a", mapOf("b", mapOf(s, s, "z", 7)), "c", mapOf("d", mapOf("e", 5)))
      )
    );
    out.add(new Case("nested key-in-key-map S=" + l, mapOf(s, mapOf(s, List.of(s)))));
    out.add(new Case("mixed order S=" + l, mapOf("first", 1, s, false, "last", s)));
  }

  private static String label(String s) {
    String visible = visible(s);
    return visible.length() > MAX_TARGETED_LABEL
      ? visible.substring(0, LABEL_HEAD) +
      "...(" +
      s.length() +
      ")..." +
      visible.substring(visible.length() - LABEL_TAIL)
      : visible;
  }

  /** Makes the characters that matter here readable in a label. */
  static String visible(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '\n':
          sb.append("\\n");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\u0085':
          sb.append("<NEL>");
          break;
        case '\u2028':
          sb.append("<LS>");
          break;
        case '\u2029':
          sb.append("<PS>");
          break;
        case '\u00a0':
          sb.append("<NBSP>");
          break;
        default:
          if (c < ' ' || c == '\u007f') {
            sb.append(String.format("<%02X>", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  // ------------------------------------------------------------------------------ exhaustion

  /**
   * 320 values built to use up every candidate mask character: all BMP private use code points,
   * those plus U+F900..U+FFFD, planes 15 and 16, and (8 cases) every usable code point. Each
   * with and without a masked character at the start, middle and end, as value and as key, with
   * single and double quote forcing.
   */
  public static List<Case> exhaustion() {
    String bmpPrivateUse = range(BMP_PRIVATE_USE_FIRST, BMP_PRIVATE_USE_LAST);
    String bmpTail = range(BMP_PRIVATE_USE_FIRST, BMP_LAST_PRINTABLE);
    String planes =
      range(PLANE_15_FIRST, PLANE_15_LAST) + range(PLANE_16_FIRST, PLANE_16_LAST);
    Map<String, String> bodies = new LinkedHashMap<>();
    bodies.put("B=BMP-PUA E000-F8FF", bmpPrivateUse);
    bodies.put("B=PUA+F900-FFFD", bmpTail);
    bodies.put("B=plane15/16-PUA", planes);
    bodies.put("B=PUA+F900-FFFD+plane15/16", bmpTail + planes);
    String[] specials = { NEL, LS, PS, BOM };
    String[] specialNames = { "NEL", "LS", "PS", "FEFF" };
    String[] positions = { "start", "mid", "end" };
    String[] shapes = { "value", "key", "sq-value", "dq-value", "sq-key", "dq-key" };
    List<Case> out = new ArrayList<>();
    for (Map.Entry<String, String> body : bodies.entrySet()) {
      for (int special = -1; special < specials.length; special++) {
        for (int pos = 0; pos < (special < 0 ? 1 : positions.length); pos++) {
          String text = body.getValue();
          if (special >= 0) {
            text = insertAt(text, specials[special], pos);
          }
          for (String shape : shapes) {
            out.add(
              new Case(
                body.getKey() +
                " |" +
                (special < 0
                    ? "no special"
                    : specialNames[special] + " " + positions[pos]) +
                " " +
                shape,
                shaped(shape, text)
              )
            );
          }
        }
      }
    }
    String all = allUsableCodePoints();
    for (int special = 0; special < specials.length; special++) {
      for (String shape : new String[] { "value", "key" }) {
        out.add(
          new Case(
            "B=ALL-USABLE(" +
            all.codePointCount(0, all.length()) +
            " cps) |" +
            specialNames[special] +
            " mid " +
            shape,
            shaped(shape, insertAt(all, specials[special], 1))
          )
        );
      }
    }
    return Collections.unmodifiableList(out);
  }

  private static Object shaped(String shape, String body) {
    String text = shape.startsWith("sq-")
      ? "x: " + body
      : shape.startsWith("dq-") ? "\n" + body : body;
    return shape.endsWith("key") ? mapOf(text, 1) : mapOf("k", text);
  }

  private static String insertAt(String body, String special, int position) {
    switch (position) {
      case 0:
        return special + body;
      case 1:
        return (
          body.substring(0, body.length() / 2) +
          special +
          body.substring(body.length() / 2)
        );
      default:
        return body + special;
    }
  }

  private static String range(int first, int last) {
    StringBuilder sb = new StringBuilder();
    for (int c = first; c <= last; c++) {
      sb.appendCodePoint(c);
    }
    return sb.toString();
  }

  /** The YAML printable code points at or above U+0100 except U+2028, U+2029 and U+FEFF. */
  static String allUsableCodePoints() {
    StringBuilder sb = new StringBuilder();
    for (int c = FIRST_NON_ASCII_FIXTURE; c <= MAX_CODE_POINT; c++) {
      if (isUsable(c)) {
        sb.appendCodePoint(c);
      }
    }
    return sb.toString();
  }

  /**
   * YAML's printable set at or above U+0100 (as snakeyaml-engine's {@code StreamReader.isPrintable}
   * defines it) without the characters the mask replaces and without the two last code points
   * of planes 15 and 16, which the mask's candidate pool does not hold either.
   */
  static boolean isUsable(int c) {
    if (
      c < FIRST_NON_ASCII_FIXTURE ||
      c == LS_CODE_POINT ||
      c == PS_CODE_POINT ||
      c == BOM_CODE_POINT
    ) {
      return false;
    }
    if (c >= PLANE_15_FIRST && (c & PLANE_MASK) > PLANE_LAST_USABLE) {
      return false;
    }
    return (
      c < SURROGATES[0] ||
      (c >= BMP_PRIVATE_USE_FIRST && c <= BMP_LAST_PRINTABLE) ||
      (c >= SUPPLEMENTARY_FIRST && c <= MAX_CODE_POINT)
    );
  }

  // ----------------------------------------------------------------------------- chunk edges

  /**
   * 10,640 long values (1,000 to 3,100 characters) with a surrogate pair at or near the 1,024
   * character chunk edges of snakeyaml-engine's reader, with a masked character nearby.
   */
  public static List<Case> chunkEdges() {
    List<Case> out = new ArrayList<>();
    String[] specials = { NEL, LS, PS, BOM };
    String[] specialNames = { "NEL", "LS", "PS", "FEFF" };
    for (int[] range : CHUNK_RANGES) {
      for (int k = range[0]; k <= range[1]; k++) {
        for (int family = 0; family < 2; family++) {
          StringBuilder xb = new StringBuilder();
          for (int i = 0; i < k; i++) {
            xb.append(family == 1 && i % SPACE_EVERY == SPACE_EVERY - 1 ? ' ' : 'a');
          }
          String x = xb.toString();
          for (int s = 0; s < specials.length; s++) {
            String[] forms = {
              x + SMILE,
              x + specials[s] + SMILE,
              x + SMILE + specials[s],
              specials[s] + x + SMILE,
              SMILE + x + specials[s] + SMILE,
            };
            for (int f = 0; f < forms.length; f++) {
              for (int key = 0; key < 2; key++) {
                out.add(
                  new Case(
                    "k=" +
                    k +
                    " fam" +
                    family +
                    " " +
                    specialNames[s] +
                    " form" +
                    f +
                    (key == 1 ? " key" : " value"),
                    key == 1 ? mapOf(forms[f], 1) : mapOf("k", forms[f])
                  )
                );
              }
            }
          }
        }
      }
    }
    return Collections.unmodifiableList(out);
  }

  // ----------------------------------------------------------------------------------- fuzz

  /** Random nested structure number {@code index}, built from seed {@code FUZZ_SEED_BASE + index}. */
  public static Case fuzz(int index) {
    return new Case(
      "fuzz #" + index,
      randomValue(new SplittableRandom(FUZZ_SEED_BASE + index), 0)
    );
  }

  private static String randomString(SplittableRandom r) {
    int n = r.nextInt(STRING_PERCENT_BASE) < SHORT_STRING_PERCENT
      ? r.nextInt(SHORT_STRING_BOUND)
      : r.nextInt(LONG_STRING_BOUND);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(FUZZ_ALPHABET[r.nextInt(FUZZ_ALPHABET.length)]);
    }
    return sb.toString();
  }

  private static Object randomValue(SplittableRandom r, int depth) {
    int kind = r.nextInt(depth >= MAX_FUZZ_DEPTH ? FUZZ_KINDS_AT_MAX_DEPTH : FUZZ_KINDS);
    switch (kind) {
      case 0:
        return r.nextInt(FUZZ_INT_BOUND);
      case 1:
        return r.nextBoolean();
      case 2:
        return null;
      case 3:
      case 4:
        return randomString(r);
      case 5:
      case 6:
        Map<Object, Object> map = new LinkedHashMap<>();
        int entries = r.nextInt(FUZZ_COLLECTION_BOUND);
        for (int i = 0; i < entries; i++) {
          Object key = r.nextInt(FUZZ_INT_KEY_ONE_IN) == 0
            ? (Object) r.nextInt(FUZZ_INT_KEY_BOUND)
            : randomString(r);
          map.put(key, randomValue(r, depth + 1));
        }
        return map;
      default:
        List<Object> list = new ArrayList<>();
        int elements = r.nextInt(FUZZ_COLLECTION_BOUND);
        for (int i = 0; i < elements; i++) {
          list.add(randomValue(r, depth + 1));
        }
        return list;
    }
  }

  // ---------------------------------------------------------------------------- test objects
  // Beans with several properties fix their order: the order of reflection results (and so
  // Jackson's natural property order) can differ from one JVM run to the next.

  @JsonPropertyOrder({ "zeta", "alpha", "mid" })
  public static class Pojo {

    private final String zeta = "z";
    private final int alpha = 1;
    private final boolean mid = true;

    public String getZeta() {
      return zeta;
    }

    public int getAlpha() {
      return alpha;
    }

    public boolean isMid() {
      return mid;
    }
  }

  public static class Empty {}

  public enum Color {
    RED,
    GREEN;

    @Override
    public String toString() {
      return "color-" + name().toLowerCase(Locale.ROOT);
    }
  }

  public static class OptionalBean {

    public Optional<String> getName() {
      return Optional.of("n");
    }
  }

  public static class LocaleBean {

    public Locale getLocale() {
      return Locale.GERMANY;
    }
  }

  public static class LocalDateBean {

    public LocalDate getDay() {
      return LocalDate.of(2020, 1, 2);
    }
  }

  public static class UrlBean {

    public String getURL() {
      return "u";
    }
  }

  public static class XValueBean {

    public int getXValue() {
      return 1;
    }
  }

  @JsonPropertyOrder({ "urlvalue", "ipaddress" })
  public static class UrlValueBean {

    public int getURLValue() {
      return 2;
    }

    public String getIPAddress() {
      return "ip";
    }
  }

  public static class ActiveBean {

    public boolean isActive() {
      return true;
    }
  }

  public static class ABean {

    public int getA() {
      return 1;
    }
  }

  public static class AbBean {

    public int getaB() {
      return 1;
    }
  }

  public static class AbcBean {

    public int getABC() {
      return 1;
    }
  }

  @JsonPropertyOrder({ "Name", "fooBar", "URL" })
  public static class FieldBean {

    public String Name = "n";
    public int fooBar = 2;
    public String URL = "x";
  }

  @JsonPropertyOrder({ "plain", "renamed" })
  public static class RenamedBean {

    @JsonProperty("renamed")
    public String getOrig() {
      return "r";
    }

    public int getPlain() {
      return 1;
    }
  }

  @JsonPropertyOrder({ "id", "url", "zulu", "alpha", "mid", "tagList", "xvalue", "aB" })
  public static class EightBean {

    public String getZulu() {
      return "z";
    }

    public int getAlpha() {
      return 1;
    }

    public boolean isMid() {
      return true;
    }

    public String getURL() {
      return "u";
    }

    public double getXValue() {
      return 1.5;
    }

    public long getID() {
      return 7L;
    }

    public List<String> getTagList() {
      return List.of("a", "b");
    }

    public String getaB() {
      return "ab";
    }
  }

  @JsonPropertyOrder({ "url", "xvalue" })
  public static class LineBreakBean {

    public String getURL() {
      return "a" + LS + "b";
    }

    public String getXValue() {
      return NEL;
    }
  }

  @JsonPropertyOrder({ "urlBean", "items" })
  public static class Holder {

    public UrlBean getUrlBean() {
      return new UrlBean();
    }

    public List<XValueBean> getItems() {
      return List.of(new XValueBean(), new XValueBean());
    }
  }
}
