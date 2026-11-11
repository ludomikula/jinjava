package com.hubspot.jinjava.lib.filter.yaml;

import static com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.BOM;
import static com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.LS;
import static com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.NEL;
import static com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.PS;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The YAML texts that {@code fromyaml} is compared on. Every {@link Case} holds a {@code
 * Supplier<String>} as its value, so the large inputs (up to 20,000,001 characters) exist only
 * while they are read. The sets are:
 * <ul>
 *   <li>{@link #corpus()}: 75 inputs, the YAML 1.1 scalar forms, tags, documents and limits;</li>
 *   <li>{@link #surrogateChunkInputs()} and {@link #surrogateSweep()}: a supplementary character
 *       at every position where SnakeYAML's 1025 character read chunks end (274 and 48);</li>
 *   <li>{@link #lineBreakInputs()} and {@link #lineBreakSweep()}: U+0085, U+2028, U+2029 and
 *       U+FEFF in many positions (19 and 68);</li>
 *   <li>{@link #targeted()}: 797 inputs on anchors, documents, numbers, tags, nesting, sizes and
 *       Jackson 2's stream limits;</li>
 *   <li>{@link #fuzz(int)}: {@value #FUZZ_SIZE} seeded random documents from {@link
 *       YamlReadFuzz}.</li>
 * </ul>
 */
final class YamlReadCorpus {

  static final int FUZZ_SIZE = 100_000;
  static final long FUZZ_SEED = 20260511L;

  /** SnakeYAML 2.4's default {@code LoaderOptions.codePointLimit}: 3 MiB. */
  static final int CODE_POINT_LIMIT = 3 * 1024 * 1024;
  /** Jackson 2's maximum string length. */
  static final int MAX_STRING = 20_000_000;

  private static final String SUPPLEMENTARY = "\uD83D\uDE00";
  private static final int SUPPLEMENTARY_LENGTH = SUPPLEMENTARY.length();
  private static final String NBSP = "\u00A0";
  private static final int WORD_LENGTH = 100;
  private static final int[] LINE_BREAK_SWEEP_CHARACTERS = {
    0x85,
    0x2028,
    0x2029,
    0xFEFF,
  };
  private static final int[] SURROGATE_LENGTHS = {
    1023,
    1024,
    1025,
    1026,
    2047,
    2048,
    2049,
    2051,
    4096,
    4097,
    4101,
    8193,
    8201,
  };
  /**
   * StreamReader reads 1025 characters per chunk, so the high surrogate indices that straddle a
   * chunk are k * 1025 - 1: 1024, 2049, 3074, 4099, ..., 8199; the others are the neighbours.
   */
  private static final int[] SURROGATE_POSITIONS = {
    1023,
    1024,
    2047,
    2049,
    4095,
    4099,
    8191,
    8199,
  };
  private static final int[] SURROGATE_SWEEP_BASES = { 1015, 2040, 4090 };
  private static final int SURROGATE_SWEEP_WIDTH = 16;
  private static final int SURROGATE_SWEEP_LENGTH = 4110;

  private static final String[] SCALAR_VARIANTS = {
    "yes",
    "no",
    "on",
    "off",
    "y",
    "n",
    "Y",
    "Yes",
    "YES",
    "NO",
    "On",
    "OFF",
    "true",
    "True",
    "TRUE",
    "false",
    "False",
    "~",
    "null",
    "Null",
    "NULL",
  };
  private static final String[] SCALAR_FORMS = {
    "0755",
    "0o755",
    "0x1F",
    "-0x1F",
    "0b1010",
    "1_000",
    "1_000.5",
    "+12",
    "-0",
    "007",
    "08",
    "09.5",
    "1e3",
    "1.5",
    ".5",
    "6.8523015e+5",
    "1:30",
    "190:20:30",
    "190:20:30.15",
    ".inf",
    "-.inf",
    ".NaN",
    "2001-12-14",
    "2001-12-14t21:59:43.10-05:00",
    "<<",
    "=",
    "'yes'",
    "\"yes\"",
    "'0755'",
    "\"123\"",
    "!!str 123",
    "!!int \"12\"",
    "!!bool yes",
    "!!float 1",
    "!!null x",
    "!!binary aGVsbG8=",
  };
  private static final int ALIAS_COUNT = 2000;
  private static final int DEEP_FLOW_LIST = 1100;
  private static final int LARGE_VALUE = 5_000_000;

  private YamlReadCorpus() {}

  static Case text(String label, String text) {
    return new Case(label, (Supplier<String>) () -> text);
  }

  static Case lazy(String label, Supplier<String> text) {
    return new Case(label, text);
  }

  /** The text of a case built by {@link #text(String, String)} or {@link #lazy(String, Supplier)}. */
  @SuppressWarnings("unchecked")
  static String textOf(Case c) {
    return ((Supplier<String>) c.value()).get();
  }

  // ---------------------------------------------------------------- corpus (75)

  static List<Case> corpus() {
    List<Case> cases = new ArrayList<>();
    for (String variant : SCALAR_VARIANTS) {
      value(cases, variant);
    }
    cases.add(text("a: (empty value)", "a:"));
    for (String form : SCALAR_FORMS) {
      value(cases, form);
    }
    cases.add(text("a: |\\n  yes (literal block)", "a: |\n  yes"));
    cases.add(text("a: >\\n  yes\\n  no (folded)", "a: >\n  yes\n  no\n"));
    cases.add(text("key yes: 1", "yes: 1"));
    cases.add(text("key 1: a", "1: a"));
    cases.add(text("key ~: b", "~: b"));
    cases.add(text("key 0x1F: c", "0x1F: c"));
    cases.add(text("flow seq [yes, 0x1F, ~]", "[yes, 0x1F, ~]"));
    cases.add(text("nested map", "a:\n  b:\n    c: yes\n    d: [1, on, 0755]\n"));
    cases.add(text("two documents", "a: 1\n---\nb: 2"));
    cases.add(text("anchor/alias", "x: &a 1\ny: *a"));
    cases.add(text("merge key", "base: &b {x: 1}\nd:\n  <<: *b\n  y: 2"));
    cases.add(text("duplicate keys", "a: 1\na: 2"));
    cases.add(text("doc is just yes", "yes"));
    cases.add(text("empty document \"\"", ""));
    cases.add(
      text("1100-deep flow list", "[".repeat(DEEP_FLOW_LIST) + "]".repeat(DEEP_FLOW_LIST))
    );
    cases.add(
      lazy(
        "2000 aliases to one anchor",
        () -> {
          StringBuilder sb = new StringBuilder("x: &a 1\ny: [");
          for (int i = 0; i < ALIAS_COUNT; i++) {
            sb.append(i == 0 ? "*a" : ", *a");
          }
          return sb.append("]").toString();
        }
      )
    );
    cases.add(lazy("5MB string value", () -> "a: " + "x".repeat(LARGE_VALUE)));
    return cases;
  }

  private static void value(List<Case> cases, String scalar) {
    cases.add(text("a: " + scalar, "a: " + scalar));
  }

  // ---------------------------------------------------------------- surrogate pairs at read chunk ends (274, 48)

  private static final String[][] SURROGATE_TEMPLATES = {
    { "plain", "k: ", "" },
    { "dq", "k: \"", "\"" },
    { "sq", "k: '", "'" },
    { "key", "", ": 1" },
    { "comment", "k: 1\n# ", "" },
    { "literal", "k: |\n  ", "\n" },
    { "listitem", "- ", "" },
  };

  /** {@code prefix}, filler, a supplementary character whose high surrogate is at {@code position}, filler, {@code suffix}; null if it does not fit. */
  private static String surrogateInput(
    String prefix,
    String suffix,
    int length,
    int position
  ) {
    int fillBefore = position - prefix.length();
    int fillAfter = length - position - SUPPLEMENTARY_LENGTH - suffix.length();
    if (fillBefore < 0 || fillAfter < 0) {
      return null;
    }
    return (
      prefix + "a".repeat(fillBefore) + SUPPLEMENTARY + "a".repeat(fillAfter) + suffix
    );
  }

  static List<Case> surrogateChunkInputs() {
    List<Case> cases = new ArrayList<>();
    for (String[] template : SURROGATE_TEMPLATES) {
      for (int length : SURROGATE_LENGTHS) {
        for (int position : SURROGATE_POSITIONS) {
          String text = surrogateInput(template[1], template[2], length, position);
          if (text != null) {
            cases.add(text(template[0] + " L=" + length + " hi@" + position, text));
          }
        }
      }
    }
    return cases;
  }

  static List<Case> surrogateSweep() {
    List<Case> cases = new ArrayList<>();
    for (int base : SURROGATE_SWEEP_BASES) {
      for (int position = base; position < base + SURROGATE_SWEEP_WIDTH; position++) {
        cases.add(
          text(
            "plain L=" + SURROGATE_SWEEP_LENGTH + " hi@" + position,
            surrogateInput("k: ", "", SURROGATE_SWEEP_LENGTH, position)
          )
        );
      }
    }
    return cases;
  }

  // ---------------------------------------------------------------- line breaks (19, 68)

  static List<Case> lineBreakInputs() {
    String[][] inputs = {
      { "NEL key", NEL + ": 1" },
      { "BOM key", BOM + ": 1" },
      { "BOM+a key (doc start)", BOM + "a: 1" },
      { "BOM mid-doc key", "a: 1\n" + BOM + "b: 2" },
      { "sq NEL only", "k: '" + NEL + "'" },
      { "sq a NEL b", "k: 'a" + NEL + "b'" },
      { "dq a NEL b", "k: \"a" + NEL + "b\"" },
      { "plain a NEL b", "k: a" + NEL + "b" },
      { "plain a LS b", "k: a" + LS + "b" },
      { "plain a PS b", "k: a" + PS + "b" },
      { "plain a NBSP b", "k: a" + NBSP + "b" },
      { "sq a LS b", "k: 'a" + LS + "b'" },
      { "sq a PS b", "k: 'a" + PS + "b'" },
      { "sq a NBSP b", "k: 'a" + NBSP + "b'" },
      { "LS key", LS + ": 1" },
      { "PS key", PS + ": 1" },
      { "NBSP key", NBSP + ": 1" },
      { "space key", " : 1" },
      { "literal a NEL b", "k: |\n  a" + NEL + "b\n" },
    };
    List<Case> cases = new ArrayList<>();
    for (String[] input : inputs) {
      cases.add(text(input[0], input[1]));
    }
    return cases;
  }

  static List<Case> lineBreakSweep() {
    String[][] contexts = {
      { "doc-start", "@a: 1" },
      { "key-start(2nd)", "z: 0\n@a: 1" },
      { "key-middle", "a@b: 1" },
      { "key-end", "a@: 1" },
      { "plain-val-start", "k: @a" },
      { "plain-val-middle", "k: a@b" },
      { "plain-val-end", "k: a@" },
      { "sq-start", "k: '@a'" },
      { "sq-middle", "k: 'a@b'" },
      { "sq-end", "k: 'a@'" },
      { "dq-middle", "k: \"a@b\"" },
      { "literal-middle", "k: |\n  a@b\n" },
      { "literal-start", "k: |\n  @a\n" },
      { "flowseq-item-middle", "[a@b, c]" },
      { "flowseq-item-start", "[@a, c]" },
      { "comment-middle", "k: 1 # a@b" },
      { "comment-line", "# @\nk: 1" },
    };
    List<Case> cases = new ArrayList<>();
    for (int character : LINE_BREAK_SWEEP_CHARACTERS) {
      for (String[] context : contexts) {
        cases.add(
          text(
            String.format("U+%04X %s", character, context[0]),
            context[1].replace("@", new String(Character.toChars(character)))
          )
        );
      }
    }
    return cases;
  }

  // ---------------------------------------------------------------- fuzz

  static Case fuzz(int index) {
    YamlReadFuzz fuzz = new YamlReadFuzz(FUZZ_SEED);
    return text("#" + index, fuzz.input(index));
  }

  // ---------------------------------------------------------------- targeted (797)

  private static String words(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(i % WORD_LENGTH == WORD_LENGTH - 1 && i != length - 1 ? ' ' : 'x');
    }
    return sb.toString();
  }

  /** Lines of 100 characters including the newline, each starting with {@code prefix}; exactly {@code length} characters. */
  private static String lines(String prefix, int length) {
    StringBuilder sb = new StringBuilder(length);
    while (sb.length() < length) {
      sb.append(prefix);
      int room = Math.min(WORD_LENGTH - prefix.length() - 1, length - sb.length());
      for (int i = 0; i < room; i++) {
        sb.append('x');
      }
      if (sb.length() < length) {
        sb.append('\n');
      }
    }
    sb.setLength(Math.min(sb.length(), length));
    return sb.toString();
  }

  static List<Case> targeted() {
    List<Case> t = new ArrayList<>();
    addAnchors(t);
    addDocuments(t);
    addDuplicateKeysAndEmpties(t);
    addNumbers(t);
    addTags(t);
    addNesting(t);
    addSizes(t);
    addMisc(t);
    return t;
  }

  private static void add(List<Case> t, String label, String text) {
    t.add(text(label, text));
  }

  private static void addLazy(List<Case> t, String label, Supplier<String> text) {
    t.add(lazy(label, text));
  }

  private static void addAnchors(List<Case> t) {
    add(t, "anchor+alias to map", "a: &x {k: v}\nb: *x");
    add(t, "anchor+alias to list", "a: &x [1, 2]\nb: *x");
    add(t, "anchored key", "&x a: 1\nb: *x");
    add(t, "alias undefined", "a: *x");
    add(t, "alias top-level", "*x");
    add(t, "alias as key", "*a : 1");
    add(t, "alias as explicit key", "&a k: v\n? *a\n: 1");
    add(t, "recursive anchor", "&a [*a]");
    add(t, "anchor on seq item", "- &a [1]\n- *a");
    add(t, "merge key single", "base: &b {x: 1}\nd:\n  <<: *b\n  y: 2");
    add(t, "merge key list", "a: &a {x: 1}\nb: &b {y: 2}\nc:\n  <<: [*a, *b]");
    add(t, "merge key twice", "<<: {a: 1}\n<<: {b: 2}");
    add(t, "merge key quoted", "'<<': 1\n\"<<\": 2");
    for (int n : new int[] { 49, 50, 51, 60, 500 }) {
      StringBuilder sb = new StringBuilder("x: &a 1\ny: [");
      for (int i = 0; i < n; i++) {
        sb.append(i == 0 ? "*a" : ", *a");
      }
      add(t, n + " aliases to one anchor", sb.append("]").toString());
    }
    add(t, "alias bomb (billion laughs, 9 levels)", aliasBomb(9, 3));
    add(t, "alias bomb (40 levels, would be 2^40 if expanded)", aliasBomb(40, 2));
  }

  private static String aliasBomb(int levels, int fanOut) {
    StringBuilder sb = new StringBuilder("a: &a0 [x, x]\n");
    for (int i = 1; i <= levels; i++) {
      sb.append("a").append(i).append(": &a").append(i).append(" [");
      for (int k = 0; k < fanOut; k++) {
        sb.append(k == 0 ? "" : ", ").append("*a").append(i - 1);
      }
      sb.append("]\n");
    }
    return sb.toString();
  }

  private static void addDocuments(List<Case> t) {
    add(t, "2 docs", "a: 1\n---\nb: 2");
    add(t, "3 docs", "---\na: 1\n---\nb: 2\n---\nc: 3");
    add(t, "doc end marker then doc", "a: 1\n...\n---\nb: 2");
    add(t, "scalar docs", "--- a\n--- b");
    add(t, "plain scalar docs", "a\n---\nb");
    add(t, "empty first doc", "---\n---\na: 1");
    add(t, "empty docs with ...", "---\n...\n---\na: 1");
    add(t, "flow docs", "--- []\n--- {}");
    add(t, "valid first, invalid second", "a: 1\n---\nb: [\n");
    add(t, "valid first, junk second", "a: 1\n---\n]\n");
    add(t, "valid first, tab-indented second", "a: 1\n---\n\tb: 2\n");
    add(t, "directive %YAML 1.1", "%YAML 1.1\n---\na: 1");
    add(t, "directive %YAML 1.2", "%YAML 1.2\n---\na: 1");
    add(t, "directive %YAML 2.0", "%YAML 2.0\n---\na: 1");
    add(t, "directive %TAG", "%TAG ! tag:example.com,2000:\n--- !foo bar");
    add(
      t,
      "directive %TAG handle",
      "%TAG !e! tag:example.com,2000:app/\n---\n!e!foo bar"
    );
  }

  private static final String[] EMPTIES = {
    "",
    " ",
    "\n",
    "\n\n",
    "# c",
    "# c\n",
    "---",
    "--- ",
    "---\n",
    "...",
    "...\n",
    "a:",
    "a:\n",
    "a: ''",
    "a: \"\"",
    "? \n",
    "? a",
    "- ",
    "-",
    "[]",
    "{}",
    "[ ]",
    "{ }",
    "a: {}",
    "a: []",
    "~",
    "null",
    "!!null",
    "''",
    "\"\"",
    "key: ~",
    "a: \nb:",
    "a:\n  b:\n",
    "- \n- ",
    "[,]",
    "[a,,b]",
    "{a}",
    "{a: }",
    "{: a}",
    "[a: b]",
    "{? a}",
    "a: |\n",
    "a: >\n",
    "a: |+\n\n",
    "a: |-\n  x",
    "\t",
    "\tfoo",
    "a:\tb",
  };

  private static void addDuplicateKeysAndEmpties(List<Case> t) {
    add(t, "dup keys diff values", "a: 1\na: 2");
    add(t, "dup keys same values", "a: 1\na: 1");
    add(t, "dup keys flow", "{a: 1, a: 2}");
    add(t, "dup keys maps", "a: {x: 1}\na: {y: 2}");
    add(t, "dup keys across non-adjacent", "a: 1\nb: 2\na: 3");
    add(t, "dup keys '1' vs 1", "1: a\n'1': b");
    add(t, "dup keys explicit", "? a\n? a");
    for (String s : EMPTIES) {
      add(t, "empty/comment/edge " + s, s);
    }
  }

  private static final String[] NUMBERS = {
    "9223372036854775807",
    "9223372036854775808",
    "-9223372036854775808",
    "-9223372036854775809",
    "2147483647",
    "2147483648",
    "-2147483648",
    "-2147483649",
    "10000000000000000000000000000000000000000",
    "-10000000000000000000000000000000000000000",
    "0x7FFFFFFF",
    "0x80000000",
    "0xFFFFFFFF",
    "0xFFFFFFFFFFFFFFF",
    "0x1FFFFFFFFFFFFFFF",
    "-0xFFFFFFFFFFFFFFFFFF",
    "0b1111111111111111111111111111111",
    "0b11111111111111111111111111111111",
    "0b111111111111111111111111111111111111111111111111111111111111111",
    "0b1111111111111111111111111111111111111111111111111111111111111111",
    "0777777777777",
    "07777777777777777777777",
    "077777777777777777777777",
    "1_000_000",
    "1_0",
    "0_7",
    "0b_1",
    "0x_ff",
    "1__0",
    "_1",
    "1_",
    "+0",
    "-0",
    "00",
    "0",
    "+",
    "-",
    "--1",
    "0o7",
    "0b",
    "0x",
    "0xg",
    "1234567890123456789012345678901234567890.5",
    "1234567890123456789012345678901234567890.5e10",
    "1e400",
    "-1e400",
    "1e-400",
    "-0.0",
    "0.0",
    ".0",
    "1.",
    "1.e3",
    ".inf",
    "-.inf",
    "+.inf",
    ".Inf",
    ".INF",
    ".NaN",
    ".nan",
    ".NAN",
    "-.NaN",
    "1_0.5",
    "+1.5",
    "1e3",
    "1E3",
    "1e+3",
    "1.5e-3",
    "190:20:30.15",
    "1:30",
    "0:30",
    "1:60",
    ".",
    "..5",
    "1e",
    "6.8523015e+5",
    "0.1",
    "123456789.123456789",
    "9007199254740993",
    "12345678901234567890.0",
  };

  private static void addNumbers(List<Case> t) {
    for (String n : NUMBERS) {
      add(t, "num top " + n, n);
      add(t, "num value " + n, "a: " + n);
      add(t, "num list " + n, "[" + n + ", " + n + "]");
    }
  }

  private static final String[] TAGGED = {
    "!!binary aGVsbG8=",
    "!!binary \"aGVsbG8=\"",
    "!!binary '!!!'",
    "!!binary \"\"",
    "!!binary YWJj\n",
    "!!binary |\n  aGVsbG8g\n  d29ybGQ=",
    "!!str 123",
    "!!str ''",
    "!!str",
    "!!int \"12\"",
    "!!int 12",
    "!!int abc",
    "!!int -",
    "!!int +",
    "!!int \"\"",
    "!!int 0x_",
    "!!int 0o17",
    "!!int 12abc",
    "!!int 1:30",
    "!!int 1.5",
    "!!int 99999999999999999999999",
    "!!float 1",
    "!!float abc",
    "!!float 1_0.5",
    "!!float \"\"",
    "!!float .inf",
    "!!float 1e999",
    "!!bool yes",
    "!!bool no",
    "!!bool maybe",
    "!!bool true",
    "!!bool TRUE",
    "!!bool \"\"",
    "!!null x",
    "!!null \"\"",
    "!!null",
    "!!timestamp 2001-12-14",
    "!!timestamp x",
    "!foo bar",
    "!foo 123",
    "!foo",
    "! 123",
    "! yes",
    "!!set {a, b}",
    "!!omap [a: 1, b: 2]",
    "!!seq [1]",
    "!!map {a: 1}",
    "!!seq a",
    "!!map a",
    "!<tag:yaml.org,2002:int> 12",
    "!<tag:yaml.org,2002:int,float> 12",
    "!<tag:yaml.org,2002:binary> aGk=",
    "!!merge <<",
    "!!value =",
    "!!yaml !",
    "!!python/object:foo x",
    "&a !!map {x: 1}",
    "!!str &a x",
    "&a !!int 5",
    "!!int &a 5",
    "!<!int> 12",
    "!!pairs [a: 1]",
    "!!float \"1\"",
    "!!int '5'",
    "!!bool 'yes'",
    "!!str yes",
    "!!str null",
    "!!str 0x1F",
  };

  private static void addTags(List<Case> t) {
    for (String s : TAGGED) {
      add(t, "tag top " + s, s);
      add(t, "tag value " + s, "a: " + s);
      add(t, "tag list " + s, "- " + s);
      add(t, "tag key " + s, s.startsWith("!!bin") || s.contains("\n") ? s : s + ": 1");
    }
  }

  private static void addNesting(List<Case> t) {
    for (int d : new int[] { 49, 50, 51, 52, 100, 999, 1000, 1001, 1100, 5000, 50000 }) {
      add(t, "nesting flow seq depth " + d, "[".repeat(d) + "]".repeat(d));
      add(t, "nesting flow map depth " + d, "{a: ".repeat(d) + "1" + "}".repeat(d));
      add(t, "nesting unclosed flow seq depth " + d, "[".repeat(d));
      if (d <= DEEP_FLOW_LIST) {
        StringBuilder blockMaps = new StringBuilder();
        for (int i = 0; i < d; i++) {
          blockMaps.append(" ".repeat(i)).append("k:\n");
        }
        blockMaps.append(" ".repeat(d)).append("v");
        add(t, "nesting block maps depth " + d, blockMaps.toString());
        add(t, "nesting block compact seq depth " + d, "- ".repeat(d) + "x");
        StringBuilder seqOfSeq = new StringBuilder();
        for (int i = 0; i < d; i++) {
          seqOfSeq.append(" ".repeat(i)).append("-\n");
        }
        seqOfSeq.append(" ".repeat(d)).append("v");
        add(t, "nesting block seq-of-seq depth " + d, seqOfSeq.toString());
        add(t, "nesting block mixed depth " + d, "- a: ".repeat(d / 2) + "x");
      }
    }
    for (int n : new int[] { 999, 1000, 1001, 2000, 10000 }) {
      add(t, "siblings flow lists " + n, "[" + "[],".repeat(n) + "[]]");
      add(t, "siblings block lists " + n, "- []\n".repeat(n));
      add(t, "siblings block maps " + n, "- {a: [1]}\n".repeat(n));
      add(
        t,
        "siblings nested-under-depth-998 " + n,
        "[".repeat(998) + "[],".repeat(n) + "[]" + "]".repeat(998)
      );
    }
  }

  private static void addSizes(List<Case> t) {
    for (int delta : new int[] { -1, 0, 1, 2 }) {
      int total = CODE_POINT_LIMIT + delta;
      addLazy(
        t,
        "doc plain value (words) total=" + total,
        () -> "a: " + words(total - 3)
      );
      addLazy(
        t,
        "doc dq value (words) total=" + total,
        () -> "a: \"" + words(total - 5) + "\""
      );
    }
    for (int delta : new int[] { 0, 1 }) {
      int total = CODE_POINT_LIMIT + delta;
      addLazy(
        t,
        "doc sq value (words) total=" + total,
        () -> "a: '" + words(total - 5) + "'"
      );
      addLazy(
        t,
        "doc comment lines total=" + total,
        () -> "a: 1\n" + lines("# ", total - 5)
      );
      addLazy(
        t,
        "doc many entries total~" + total,
        () -> "k: v\n".repeat(total / "k: v\n".length())
      );
      addLazy(
        t,
        "doc emoji value (words) total=" + total + " chars",
        () -> "a: " + words(total - 3).replace("xxxxxxxxx ", SUPPLEMENTARY + "xxxxxxx ")
      );
    }
    addLazy(
      t,
      "doc literal lines 3MiB-1",
      () -> "a: |\n" + lines("  ", CODE_POINT_LIMIT - 6)
    );
    addLazy(
      t,
      "doc literal lines 3MiB+1",
      () -> "a: |\n" + lines("  ", CODE_POINT_LIMIT - 4)
    );
    addLazy(
      t,
      "single 3MiB-1 run of x (plain, quadratic)",
      () -> "a: " + "x".repeat(CODE_POINT_LIMIT - 4)
    );
    addLazy(
      t,
      "single 3MiB+1 run of x (dq, quadratic)",
      () -> "a: \"" + "x".repeat(CODE_POINT_LIMIT - 4) + "\""
    );
    for (int n : new int[] { MAX_STRING - 1, MAX_STRING, MAX_STRING + 1 }) {
      addLazy(t, "dq string value (words) " + n, () -> "a: \"" + words(n) + "\"");
      addLazy(t, "plain string value (words) " + n, () -> "a: " + words(n));
    }
    addLazy(
      t,
      "sq string value (words) 20000000",
      () -> "a: '" + words(MAX_STRING) + "'"
    );
    addLazy(
      t,
      "literal string value (lines) 20000000",
      () -> "a: |\n" + lines("  ", MAX_STRING)
    );
    addLazy(
      t,
      "dq string top-level (words) 20000001",
      () -> "\"" + words(MAX_STRING + 1) + "\""
    );
    addLazy(t, "dq key (words) 20000000", () -> "\"" + words(MAX_STRING) + "\": 1");
    addLazy(t, "5MB doc plain value", () -> "a: " + "x".repeat(LARGE_VALUE));
    addLazy(t, "5MB doc many entries", () -> "k: v\n".repeat(1_000_000));
    addLazy(t, "5MB doc list", () -> "- 1\n".repeat(1_250_000));
    addLazy(
      t,
      "2.5MB doc many entries (under codePointLimit)",
      () -> "k: v\n".repeat(500_000)
    );
    for (int n : new int[] { 999, 1000, 1001, 2000 }) {
      add(t, "int digits " + n, "a: " + "1".repeat(n));
      add(t, "int digits neg " + n, "a: -" + "1".repeat(n));
      add(t, "int digits underscore " + n, "a: 1_" + "1".repeat(n - 1));
      add(t, "float digits " + n, "a: " + "1".repeat(n - 2) + ".5");
      add(t, "float exp digits " + n, "a: 1." + "0".repeat(n - 4) + "e3");
      add(t, "hex digits " + n, "a: 0x" + "f".repeat(n - 2));
      add(t, "bin digits " + n, "a: 0b" + "1".repeat(n - 2));
      add(t, "oct digits " + n, "a: 0" + "7".repeat(n - 1));
      add(t, "!!int digits " + n, "a: !!int " + "9".repeat(n));
      add(t, "!!float digits " + n, "a: !!float " + "9".repeat(n));
    }
    for (int n : new int[] { 49_999, 50_000, 50_001, 100_000 }) {
      add(t, "key length " + n, "k".repeat(n) + ": 1");
      add(t, "flow key length " + n, "{" + "k".repeat(n) + ": 1}");
      add(t, "dq key length " + n, "\"" + "k".repeat(n) + "\": 1");
      add(t, "explicit key length " + n, "? " + "k".repeat(n) + "\n: 1");
    }
  }

  private static void addMisc(List<Case> t) {
    add(t, "plain scalar docs with directives only", "%YAML 1.1\n");
    add(t, "non-string key map", "? [a, b]\n: 1");
    add(t, "null key", "~: 1");
    add(t, "int key", "1: a\n2: b");
    add(t, "bool key", "yes: 1\nno: 2");
    add(t, "float key", "1.5: a");
    add(t, "complex key map", "? {a: 1}\n: 2");
    add(t, "tab inside plain", "a: x\ty");
    add(t, "windows newlines", "a: 1\r\nb: 2\r\n");
    add(t, "CR only newlines", "a: 1\rb: 2\r");
    add(t, "lone high surrogate", "a: \uD83D");
    add(t, "lone low surrogate", "a: \uDE00");
    add(t, "NUL char", "a: \u0000");
    add(t, "C0 control char", "a: \u0007");
    add(t, "DEL char", "a: \u007F");
    add(t, "C1 control char U+0080", "a: \u0080");
    add(t, "U+FFFE", "a: \uFFFE");
    add(t, "non-BMP in key", SUPPLEMENTARY + ": 1");
  }
}
