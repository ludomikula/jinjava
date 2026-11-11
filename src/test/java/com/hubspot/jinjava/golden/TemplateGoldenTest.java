package com.hubspot.jinjava.golden;

import static org.junit.Assert.fail;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.LegacyOverrides;
import com.hubspot.jinjava.el.ext.AllowlistMethodValidator;
import com.hubspot.jinjava.el.ext.AllowlistReturnTypeValidator;
import com.hubspot.jinjava.el.ext.MethodValidatorConfig;
import com.hubspot.jinjava.el.ext.ReturnTypeValidatorConfig;
import com.hubspot.jinjava.interpret.DeferredValue;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.RenderResult;
import com.hubspot.jinjava.interpret.TemplateError;
import com.hubspot.jinjava.mode.EagerExecutionMode;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Template-level golden test. Renders a corpus of templates and compares a canonical record
 * (rendered output + template errors) with fixtures in src/test/resources/golden.
 *
 * To (re)generate fixtures run with {@code -Djinjava.golden.update=true}. In that mode the
 * fixtures are written and the test passes.
 *
 * Limits: this covers only what is observable through Jinjava.renderForResult for the cases
 * listed here. Error messages are compared verbatim, so they include any exception text of the
 * Jackson version in use (that is intentional: it surfaces behaviour changes).
 */
@RunWith(Parameterized.class)
public class TemplateGoldenTest {

  public static final String UPDATE_PROPERTY = "jinjava.golden.update";
  private static final Path GOLDEN_DIR = Paths.get(
    System.getProperty("basedir", "."),
    "src",
    "test",
    "resources",
    "golden"
  );
  private static final String FIXTURE_EXTENSION = ".out";
  private static final String OUTPUT_HEADER = "=== OUTPUT ===";
  private static final String ERRORS_HEADER = "=== ERRORS ===";
  private static final String FIELD_SEPARATOR = "|";
  private static final String DEFERRED_KEY = "deferred";
  private static final long SMALL_MAX_OUTPUT = 40L;
  private static final int MAX_REPORTED_DIFFS = 20;
  // Object.toString() of beans without serializers embeds a per-run identity hash; the only
  // normalisation applied to the record.
  private static final Pattern IDENTITY_HASH = Pattern.compile(
    "(?<=\\$[A-Za-z]{1,40})@[0-9a-f]{1,8}\\b"
  );

  // ---------- configuration variants ----------

  private static final AllowlistMethodValidator METHOD_VALIDATOR =
    AllowlistMethodValidator.create(
      MethodValidatorConfig
        .builder()
        .addDefaultAllowlistGroups()
        .addAllowedDeclaredMethodsFromCanonicalClassPrefixes(
          "com.hubspot.jinjava.testobjects."
        )
        .build()
    );
  private static final AllowlistReturnTypeValidator RETURN_TYPE_VALIDATOR =
    AllowlistReturnTypeValidator.create(
      ReturnTypeValidatorConfig
        .builder()
        .addDefaultAllowlistGroups()
        .addAllowedCanonicalClassPrefixes("com.hubspot.jinjava.testobjects.")
        .build()
    );

  private static JinjavaConfig.Builder base() {
    return JinjavaConfig
      .builder()
      .withMethodValidator(METHOD_VALIDATOR)
      .withReturnTypeValidator(RETURN_TYPE_VALIDATOR);
  }

  private static LegacyOverrides pyish(boolean snake) {
    return LegacyOverrides
      .newBuilder()
      .withUsePyishObjectMapper(true)
      .withUseSnakeCasePropertyNaming(snake)
      .build();
  }

  private static final Map<String, Function<Void, JinjavaConfig>> VARIANTS =
    new LinkedHashMap<>();

  static {
    VARIANTS.put("default", x -> base().build());
    VARIANTS.put(
      "snake",
      x ->
        base()
          .withLegacyOverrides(
            LegacyOverrides.newBuilder().withUseSnakeCasePropertyNaming(true).build()
          )
          .build()
    );
    VARIANTS.put("default-max", x -> base().withMaxOutputSize(SMALL_MAX_OUTPUT).build());
    VARIANTS.put("pyish", x -> base().withLegacyOverrides(pyish(false)).build());
    VARIANTS.put("pyish-snake", x -> base().withLegacyOverrides(pyish(true)).build());
    VARIANTS.put(
      "pyish-max",
      x ->
        base()
          .withLegacyOverrides(pyish(false))
          .withMaxOutputSize(SMALL_MAX_OUTPUT)
          .build()
    );
    VARIANTS.put(
      "eager",
      x ->
        base()
          .withExecutionMode(EagerExecutionMode.instance())
          .withLegacyOverrides(pyish(false))
          .build()
    );
    VARIANTS.put(
      "eager-snake",
      x ->
        base()
          .withExecutionMode(EagerExecutionMode.instance())
          .withLegacyOverrides(pyish(true))
          .build()
    );
    VARIANTS.put(
      "eager-max",
      x ->
        base()
          .withExecutionMode(EagerExecutionMode.instance())
          .withLegacyOverrides(pyish(false))
          .withMaxOutputSize(SMALL_MAX_OUTPUT)
          .build()
    );
  }

  // ---------- fixtures data ----------

  private static final ZonedDateTime ZDT = ZonedDateTime.of(
    2020,
    2,
    29,
    13,
    14,
    15,
    123_000_000,
    ZoneOffset.UTC
  );
  private static final Date DATE = new Date(1_582_982_055_123L);

  private static Map<String, Object> ctx(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private static Map<String, Object> mixedMap() {
    return ctx(
      "b",
      1,
      "a",
      Arrays.asList(1, "two", 3.5, true),
      "c",
      ctx("z", 1, "y", null)
    );
  }

  // Line separators other than \n, written as escapes so the source stays readable.
  private static final String NEL = "\u0085";
  private static final String LS = "\u2028";
  private static final String PS = "\u2029";
  private static final String EMOJI = "\uD83D\uDE00";
  // snakeyaml-engine reads its input in chunks of this many chars (index E5).
  private static final int YAML_CHUNK = 1025;

  private static final String TYPE_PROBE =
    "{{ v }}|{{ v is string }}|{{ v is number }}|{{ v is boolean }}|{{ v is none }}|{{ v is mapping }}|{{ v is sequence }}|{{ v|tojson }}";
  private static final String FROMYAML = "{% set v = s|fromyaml %}" + TYPE_PROBE;
  private static final String FROMJSON = "{% set v = s|fromjson %}" + TYPE_PROBE;

  private static final class Case {

    final String name;
    final String variant;
    final String template;
    final Map<String, Object> context;

    Case(String name, String variant, String template, Map<String, Object> context) {
      this.name = name;
      this.variant = variant;
      this.template = template;
      this.context = context;
    }
  }

  private static final List<Case> CASES = new ArrayList<>();

  private static void add(
    String name,
    String variant,
    String template,
    Object... keyValues
  ) {
    CASES.add(new Case(name, variant, template, ctx(keyValues)));
  }

  static {
    // ---- tojson ----
    add("tojson-map", "default", "{{ m|tojson }}", "m", mixedMap());
    add(
      "tojson-list",
      "default",
      "{{ l|tojson }}",
      "l",
      Arrays.asList(3, 1, 2, "x", null)
    );
    add(
      "tojson-nested",
      "default",
      "{{ m|tojson }}",
      "m",
      ctx("l", Arrays.asList(ctx("k", Arrays.asList(1, 2)), Arrays.asList()), "e", ctx())
    );
    add("tojson-bean-small", "default", "{{ b|tojson }}", "b", new SmallBean());
    add("tojson-bean-big", "default", "{{ b|tojson }}", "b", new BigBean());
    add("tojson-bean-small-snake", "snake", "{{ b|tojson }}", "b", new SmallBean());
    add("tojson-bean-camel-snake", "snake", "{{ b|tojson }}", "b", new CamelBean());
    add("tojson-enum", "default", "{{ e|tojson }}|{{ e }}", "e", Color.RED);
    add("tojson-date", "default", "{{ d|tojson }}", "d", DATE);
    add("tojson-zoned-datetime", "default", "{{ d|tojson }}", "d", ZDT);
    add(
      "tojson-optional",
      "default",
      "{{ a|tojson }}|{{ b|tojson }}",
      "a",
      Optional.of("v"),
      "b",
      Optional.empty()
    );
    add("tojson-null", "default", "{{ n|tojson }}", "n", null);
    add("tojson-empty-bean", "default", "{{ b|tojson }}", "b", new EmptyBean());
    add("tojson-throwing-bean", "default", "{{ b|tojson }}", "b", new ThrowingBean());
    add(
      "tojson-strings",
      "default",
      "{{ a|tojson }}|{{ b|tojson }}|{{ c|tojson }}|{{ d|tojson }}|{{ e|tojson }}",
      "a",
      "a/b/c",
      "b",
      "</script><b>&amp;'</b>",
      "c",
      "café ☃ 😀   \u0007",
      "d",
      "he said \"hi\"\n\ttab\\",
      "e",
      ""
    );
    add("tojson-safestring", "default", "{{ s|safe|tojson }}", "s", "<i>x/y</i>");
    add(
      "tojson-null-value-loop",
      "default",
      "{% for x in l %}[{{ x|tojson }}]{% endfor %}",
      "l",
      Arrays.asList(1, null, "a")
    );
    add("tojson-pyishdate", "default", "{{ d|tojson }}", "d", new PyishDate(ZDT));
    add("tojson-max-output", "default-max", "{{ m|tojson }}", "m", mixedMap());
    add(
      "tojson-max-output-long-list",
      "default-max",
      "{{ l|tojson }}",
      "l",
      Arrays.asList("aaaaaaaaaa", "bbbbbbbbbb", "cccccccccc", "dddddddddd", "eeeeeeeeee")
    );
    add(
      "tojson-in-pyish-mode",
      "pyish",
      "{{ m|tojson }}|{{ b|tojson }}",
      "m",
      mixedMap(),
      "b",
      new BigBean()
    );

    // ---- fromjson ----
    add(
      "fromjson-object",
      "default",
      FROMJSON,
      "s",
      "{\"a\": 1, \"b\": [1, 2, {\"c\": null}]}"
    );
    add("fromjson-array", "default", FROMJSON, "s", "[1, \"two\", 3.5, true, null]");
    add("fromjson-trailing-garbage", "default", FROMJSON, "s", "{\"a\":1} x");
    add("fromjson-two-values", "default", FROMJSON, "s", "[1] [2]");
    add(
      "fromjson-big-integer",
      "default",
      FROMJSON,
      "s",
      "[123456789012345678901234567890, 12345678901, 1.0e400]"
    );
    add("fromjson-invalid", "default", FROMJSON, "s", "{not json");
    add("fromjson-scalar-string", "default", FROMJSON, "s", "\"a/b\\u00e9\"");
    add("fromjson-empty", "default", FROMJSON, "s", "");

    // ---- pprint ----
    add("pprint-bean", "default", "{{ b|pprint }}", "b", new BigBean());
    add("pprint-map", "default", "{{ m|pprint }}", "m", mixedMap());
    add("pprint-list", "default", "{{ l|pprint }}", "l", Arrays.asList(1, "a'b", null));
    add("pprint-pyishdate", "default", "{{ d|pprint }}", "d", new PyishDate(ZDT));

    // ---- toyaml ----
    add("toyaml-map", "default", "{{ m|toyaml }}", "m", mixedMap());
    add(
      "toyaml-list",
      "default",
      "{{ l|toyaml }}",
      "l",
      Arrays.asList(1, "a", Arrays.asList(2, 3), ctx("k", "v"))
    );
    add("toyaml-bean", "default", "{{ b|toyaml }}", "b", new BigBean());
    String[][] yamlStrings = {
      { "yes", "yes" },
      { "colon-time", "1:30" },
      { "octal-like", "0755" },
      { "multiline", "line1\nline2\n" },
      { "unicode", "café ☃ 😀" },
      { "colon-space", "a: b" },
      { "hash", "#x" },
      { "empty", "" },
      { "true-str", "true" },
      { "null-str", "null" },
      { "quote", "it's \"q\"" },
    };
    for (String[] pair : yamlStrings) {
      add("toyaml-string-" + pair[0], "default", "{{ s|toyaml }}", "s", pair[1]);
    }
    add("toyaml-null", "default", "{{ n|toyaml }}", "n", null);
    add(
      "toyaml-map-strings",
      "default",
      "{{ m|toyaml }}",
      "m",
      ctx("a", "yes", "b", "1:30", "c", "0755", "d", "x\ny", "e", null, "f", Color.RED)
    );

    // ---- fromyaml ----
    String[][] yamlScalars = {
      { "yes", "yes" },
      { "no", "no" },
      { "on", "on" },
      { "off", "off" },
      { "y", "y" },
      { "True", "True" },
      { "TRUE", "TRUE" },
      { "tilde", "~" },
      { "Null", "Null" },
      { "octal-0755", "0755" },
      { "octal-0o755", "0o755" },
      { "hex", "0x1F" },
      { "underscore", "1_000" },
      { "sexagesimal", "1:30" },
      { "date", "2001-12-14" },
      { "timestamp", "2001-12-14t21:59:43.10-05:00" },
      { "inf", ".inf" },
      { "nan", ".NaN" },
      { "float", "1.5e3" },
      { "quoted-yes", "'yes'" },
      { "quoted-0755", "\"0755\"" },
      { "empty-doc", "" },
      { "multi-document", "a: 1\n---\nb: 2\n" },
      { "anchor-merge", "base: &b {x: 1, y: 2}\nderived:\n  <<: *b\n  y: 3\n" },
      { "duplicate-keys", "a: 1\na: 2\n" },
      { "list", "- 1\n- two\n- [3, 4]\n- {k: v}\n" },
      { "invalid", "a: [1, 2" },
      { "bigint", "123456789012345678901234567890" },
    };
    for (String[] pair : yamlScalars) {
      add("fromyaml-" + pair[0], "default", FROMYAML, "s", pair[1]);
    }

    // ---- pyish output ----
    add("pyish-map", "pyish", "{{ m }}", "m", mixedMap());
    add(
      "pyish-list",
      "pyish",
      "{{ l }}",
      "l",
      Arrays.asList(1, "a'b\"c\n", null, 2.5, true)
    );
    add("pyish-bean-small", "pyish", "{{ b }}", "b", new SmallBean());
    add("pyish-bean-big", "pyish", "{{ b }}", "b", new BigBean());
    add("pyish-bean-snake", "pyish-snake", "{{ b }}", "b", new CamelBean());
    add("pyish-bean-big-snake", "pyish-snake", "{{ b }}", "b", new BigBean());
    Map<String, Object> nullKey = new LinkedHashMap<>();
    nullKey.put(null, "nk");
    nullKey.put("k", null);
    add("pyish-map-null-key", "pyish", "{{ m }}", "m", nullKey);
    add(
      "pyish-strings",
      "pyish",
      "{{ l }}",
      "l",
      Arrays.asList(
        "it's",
        "say \"x\"",
        "a\nb",
        "back\\slash",
        "a/b",
        "</script>",
        "café 😀"
      )
    );
    add("pyish-namespace", "pyish", "{% set ns = namespace(a=1) %}{{ ns }}");
    add(
      "pyish-dict-items",
      "pyish",
      "{{ m.items() }}|{{ m.keys() }}|{{ m.values() }}",
      "m",
      ctx("b", 1, "a", ctx("c", 2))
    );
    add(
      "pyish-dates",
      "pyish",
      "{{ d }}|{{ p }}|{{ z }}|{{ l }}",
      "d",
      DATE,
      "p",
      new PyishDate(ZDT),
      "z",
      ZDT,
      "l",
      Arrays.asList(new PyishDate(ZDT))
    );
    add(
      "pyish-enum-optional",
      "pyish",
      "{{ e }}|{{ l }}|{{ o }}",
      "e",
      Color.RED,
      "l",
      Arrays.asList(Color.GREEN),
      "o",
      Optional.of(1)
    );
    add(
      "pyish-empty-bean",
      "pyish",
      "{{ b }}|{{ l }}",
      "b",
      new EmptyBean(),
      "l",
      Arrays.asList(new EmptyBean())
    );
    add("pyish-throwing-bean", "pyish", "{{ b }}", "b", new ThrowingBean());
    add("pyish-max-output", "pyish-max", "{{ m }}", "m", mixedMap());
    add(
      "pyish-max-output-long-list",
      "pyish-max",
      "{{ l }}",
      "l",
      Arrays.asList("aaaaaaaaaa", "bbbbbbbbbb", "cccccccccc", "dddddddddd", "eeeeeeeeee")
    );
    add(
      "pyish-tojson-then-fromjson",
      "pyish",
      "{% set v = (m|tojson)|fromjson %}{{ v }}|{{ v.a }}",
      "m",
      mixedMap()
    );

    // ---- eager mode ----
    String reconstruct =
      "{% set x = {'a': [1,'b'], 'c': bean} %}{% if deferred %}{{ x }}{% endif %}";
    add("eager-reconstruct-bean-small", "eager", reconstruct, "bean", new SmallBean());
    add("eager-reconstruct-bean-big", "eager", reconstruct, "bean", new BigBean());
    add(
      "eager-reconstruct-bean-snake",
      "eager-snake",
      reconstruct,
      "bean",
      new CamelBean()
    );
    add(
      "eager-reconstruct-bean-big-snake",
      "eager-snake",
      reconstruct,
      "bean",
      new BigBean()
    );
    add(
      "eager-reconstruct-strings",
      "eager",
      "{% set x = ['it\\'s', 'say \"q\"', 'a/b', '</script>', 'tab\\there', s] %}{% if deferred %}{{ x }}{% endif %}",
      "s",
      "café 😀 \n back\\slash"
    );
    add(
      "eager-reconstruct-dates",
      "eager",
      "{% set x = [d, p, z] %}{% if deferred %}{{ x }}{% endif %}",
      "d",
      DATE,
      "p",
      new PyishDate(ZDT),
      "z",
      ZDT
    );
    add(
      "eager-reconstruct-map-null",
      "eager",
      "{% set x = {'k': null, 'n': [null, 1], 'e': {}, 'l': []} %}{% if deferred %}{{ x }}{% endif %}"
    );
    add(
      "eager-reconstruct-enum-optional",
      "eager",
      "{% set x = [e, o] %}{% if deferred %}{{ x }}{% endif %}",
      "e",
      Color.GREEN,
      "o",
      Optional.of("v")
    );
    add(
      "eager-for-deferred-resolvable-map",
      "eager",
      "{% set m = {'a': 1, 'b': [2, 3], 'c': bean} %}{% for i in deferred %}{{ m }}{{ i }}{% endfor %}",
      "bean",
      new SmallBean()
    );
    add(
      "eager-for-resolvable-over-map-deferred-body",
      "eager",
      "{% set m = {'a': 1, 'b': 2} %}{% for k, v in m.items() %}{{ k }}={{ v }}{{ deferred }};{% endfor %}"
    );
    add(
      "eager-macro-deferred-arg",
      "eager",
      "{% macro show(a, b) %}{{ a }}-{{ b }}{% endmacro %}{% set m = {'k': [1, 'x']} %}{{ show(m, deferred) }}"
    );
    add(
      "eager-macro-resolved-arg-deferred-body",
      "eager",
      "{% macro show(a) %}{{ a }}{{ deferred }}{% endmacro %}{{ show(bean) }}",
      "bean",
      new BigBean()
    );
    add(
      "eager-set-modified-in-deferred-if",
      "eager",
      "{% set l = [1, 2] %}{% if deferred %}{% do l.append(3) %}{% endif %}{{ l }}"
    );
    add(
      "eager-namespace",
      "eager",
      "{% set ns = namespace(a=1, b=[1,2]) %}{% if deferred %}{% set ns.a = 2 %}{% endif %}{{ ns }}"
    );
    add(
      "eager-tojson-deferred-structure",
      "eager",
      "{% set x = {'a': [1, 2], 'c': bean} %}{% if deferred %}{{ x|tojson }}{% endif %}{{ x|tojson }}",
      "bean",
      new SmallBean()
    );
    add(
      "eager-allow-snake-case-map",
      "eager-snake",
      "{% set x = {'fooBar': 1}|allow_snake_case %}{% if deferred %}{{ x.foo_bar }}{{ x }}{% endif %}"
    );

    // Beans captured in a reconstructed {% set %} tag (EagerContextWatcher / macro call with a
    // deferred argument) are serialized with forOutput=false and get the |allow_snake_case suffix.
    add(
      "eager-snake-suffix-macro-arg-small-bean",
      "eager",
      "{% macro show(a, b) %}{{ a }}-{{ b }}{% endmacro %}{{ show(bean, deferred) }}",
      "bean",
      new SmallBean()
    );
    add(
      "eager-snake-suffix-macro-arg-big-bean",
      "eager",
      "{% macro show(a, b) %}{{ a }}-{{ b }}{% endmacro %}{{ show(bean, deferred) }}",
      "bean",
      new BigBean()
    );
    add(
      "eager-snake-suffix-macro-arg-bean-in-map-and-list",
      "eager",
      "{% macro show(a, b) %}{{ a }}-{{ b }}{% endmacro %}{% set m = {'k': bean, 'l': [bean]} %}{{ show(m, deferred) }}",
      "bean",
      new BigBean()
    );
    add(
      "eager-snake-suffix-macro-arg-camel-bean-snake-config",
      "eager-snake",
      "{% macro show(a, b) %}{{ a }}-{{ b }}{% endmacro %}{{ show(bean, deferred) }}",
      "bean",
      new CamelBean()
    );
    // ---- error / fallback behaviour ----
    add(
      "eager-empty-bean-reconstruct",
      "eager",
      "{% set x = {'c': bean} %}{% if deferred %}{{ x }}{% endif %}",
      "bean",
      new EmptyBean()
    );
    add(
      "eager-throwing-bean-reconstruct",
      "eager",
      "{% set x = {'c': bean} %}{% if deferred %}{{ x }}{% endif %}",
      "bean",
      new ThrowingBean()
    );
    add(
      "eager-max-output-reconstruct",
      "eager-max",
      "{% set x = {'a': [1,'bbbbbbbbbbbbbbbbbbbb'], 'c': 'cccccccccccccccccccc'} %}{% if deferred %}{{ x }}{% endif %}"
    );
    add(
      "eager-max-output-small",
      "eager-max",
      "{% set x = {'a': 1} %}{% if deferred %}{{ x }}{% endif %}"
    );

    // ---- exception unwrapping from bean getters (pins the depth of the cause walk) ----
    // direct = exception thrown by the getter; wrapped = RuntimeException(cause=exception).
    // nested = bean inside a list inside a map.
    Map<String, Supplier<Object>> getterBeans = new LinkedHashMap<>();
    getterBeans.put("deferred-direct", DeferredGetterBean::new);
    getterBeans.put("deferred-wrapped", WrappedDeferredGetterBean::new);
    getterBeans.put("toobig-direct", TooBigGetterBean::new);
    getterBeans.put("toobig-wrapped", WrappedTooBigGetterBean::new);
    for (Map.Entry<String, Supplier<Object>> entry : getterBeans.entrySet()) {
      String id = entry.getKey();
      add(
        "unwrap-" + id + "-tojson",
        "default",
        "{{ b|tojson }}",
        "b",
        entry.getValue().get()
      );
      add("unwrap-" + id + "-pyish", "pyish", "{{ b }}", "b", entry.getValue().get());
      add(
        "unwrap-" + id + "-nested-tojson",
        "default",
        "{{ m|tojson }}",
        "m",
        ctx("k", Arrays.asList(1, entry.getValue().get()))
      );
      add(
        "unwrap-" + id + "-nested-pyish",
        "pyish",
        "{{ m }}",
        "m",
        ctx("k", Arrays.asList(1, entry.getValue().get()))
      );
      add(
        "unwrap-" + id + "-eager-tojson",
        "eager",
        "{% if deferred %}{{ b|tojson }}{% endif %}{{ b|tojson }}",
        "b",
        entry.getValue().get()
      );
      add(
        "unwrap-" + id + "-eager-plain",
        "eager",
        "{% if deferred %}{{ b }}{% endif %}{{ b }}",
        "b",
        entry.getValue().get()
      );
      add(
        "unwrap-" + id + "-eager-reconstruct-nested",
        "eager",
        "{% set x = {'k': [1, b]} %}{% if deferred %}{{ x }}{% endif %}",
        "b",
        entry.getValue().get()
      );
      add(
        "unwrap-" + id + "-eager-nested-tojson",
        "eager",
        "{% set x = {'k': [1, b]} %}{% if deferred %}{{ x|tojson }}{% endif %}{{ x|tojson }}",
        "b",
        entry.getValue().get()
      );
    }
    // tojson over maxOutputSize (variant default-max = 40 chars) on a deeply nested list.
    List<Object> deep = new ArrayList<>(Arrays.asList("aaaaaaaaaa", "bbbbbbbbbb"));
    for (int i = 0; i < 8; i++) {
      deep = new ArrayList<>(Arrays.asList("cccccccccc", deep));
    }
    add("unwrap-tojson-max-output-deep-list", "default-max", "{{ l|tojson }}", "l", deep);
    add("unwrap-pyish-max-output-deep-list", "pyish-max", "{{ l }}", "l", deep);
    add(
      "unwrap-eager-tojson-max-output-deep-list",
      "eager-max",
      "{% if deferred %}{{ l|tojson }}{% endif %}{{ l|tojson }}",
      "l",
      deep
    );

    addYamlLineBreakCases();
    addValueTypeCases();
    addDepthAndBoundaryCases();
  }

  // ---- U+0085 (NEL), U+2028 (LS), U+2029 (PS): line breaks to SnakeYAML 2.x, plain text to
  // snakeyaml-engine (index X1 for toyaml, X2 for fromyaml) ----
  private static void addYamlLineBreakCases() {
    String[][] breaks = { { "nel", NEL }, { "ls", LS }, { "ps", PS } };
    for (String[] pair : breaks) {
      String id = pair[0];
      String ch = pair[1];
      add(
        "toyaml-linebreak-value-" + id,
        "default",
        "{{ s|toyaml }}",
        "s",
        "a" + ch + "b"
      );
      add(
        "toyaml-linebreak-key-" + id,
        "default",
        "{{ m|toyaml }}",
        "m",
        ctx("a" + ch + "b", 1, "plain", 2)
      );
      add(
        "toyaml-linebreak-nested-" + id,
        "default",
        "{{ m|toyaml }}",
        "m",
        ctx(
          "list",
          Arrays.asList("x" + ch, ctx("k" + ch, "v" + ch)),
          "long",
          "word " + ch + " word ".repeat(30)
        )
      );
      add("fromyaml-linebreak-plain-" + id, "default", FROMYAML, "s", "k: a" + ch + "b");
      add(
        "fromyaml-linebreak-single-quoted-" + id,
        "default",
        FROMYAML,
        "s",
        "k: 'a" + ch + "b'"
      );
      add(
        "fromyaml-linebreak-indent-" + id,
        "default",
        FROMYAML,
        "s",
        "z: 0\n" + ch + "a: 1"
      );
      add("fromyaml-linebreak-key-" + id, "default", FROMYAML, "s", ch + ": 1");
    }
  }

  // ---- value types whose Jackson 2 output differs from a plain Jackson 3 mapper (index E1, E2) ----
  private static void addValueTypeCases() {
    // Values sit inside a map: the sandbox's return type validator turns a java.util.Locale or
    // java.time value used directly as a template variable into null before any filter sees it.
    add(
      "tojson-locale-in-map",
      "default",
      "{{ m|tojson }}",
      "m",
      ctx("a", Locale.US, "b", Locale.GERMANY, "c", Locale.forLanguageTag("sr-Latn-RS"))
    );
    add(
      "toyaml-locale-in-map",
      "default",
      "{{ m|toyaml }}",
      "m",
      ctx("a", Locale.US, "b", Locale.forLanguageTag("sr-Latn-RS"))
    );
    add(
      "tojson-instant-in-map",
      "default",
      "{{ m|tojson }}",
      "m",
      ctx("v", Instant.ofEpochSecond(1_582_982_055L))
    );
    add(
      "tojson-local-date-in-map",
      "default",
      "{{ m|tojson }}",
      "m",
      ctx("v", LocalDate.of(2020, 2, 29))
    );
    add(
      "tojson-duration-in-map",
      "default",
      "{{ m|tojson }}",
      "m",
      ctx("v", Duration.ofSeconds(90))
    );
    add(
      "toyaml-instant-in-map",
      "default",
      "{{ m|toyaml }}",
      "m",
      ctx("v", Instant.ofEpochSecond(1_582_982_055L))
    );
    add(
      "pyish-locale-instant-in-map",
      "pyish",
      "{{ m }}",
      "m",
      ctx("a", Locale.US, "b", Instant.ofEpochSecond(1_582_982_055L))
    );
    add("tojson-acronym-bean", "default", "{{ b|tojson }}", "b", new AcronymBean());
    add("tojson-acronym-bean-snake", "snake", "{{ b|tojson }}", "b", new AcronymBean());
    add("toyaml-acronym-bean", "default", "{{ b|toyaml }}", "b", new AcronymBean());
    add("pprint-acronym-bean", "default", "{{ b|pprint }}", "b", new AcronymBean());
    add("pyish-acronym-bean", "pyish", "{{ b }}", "b", new AcronymBean());
    add("pyish-acronym-bean-snake", "pyish-snake", "{{ b }}", "b", new AcronymBean());
    add(
      "eager-reconstruct-acronym-bean",
      "eager",
      "{% set x = {'c': bean} %}{% if deferred %}{{ x }}{% endif %}",
      "bean",
      new AcronymBean()
    );
  }

  // ---- stream constraints: nesting depth and the 1025-character read chunk (index E2, E5, E6) ----
  private static void addDepthAndBoundaryCases() {
    add("tojson-nested-600", "default", "{{ l|tojson }}", "l", nestedLists(600));
    add("tojson-nested-1001", "default", "{{ l|tojson }}", "l", nestedLists(1001));
    add("fromjson-nested-600", "default", FROMJSON, "s", brackets(600));
    add("fromjson-nested-1001", "default", FROMJSON, "s", brackets(1001));
    add("fromyaml-nested-flow-1000", "default", FROMYAML, "s", brackets(1000));
    add("fromyaml-nested-flow-1001", "default", FROMYAML, "s", brackets(1001));
    add(
      "fromyaml-emoji-at-chunk-boundary",
      "default",
      FROMYAML,
      "s",
      "a".repeat(YAML_CHUNK - 1) + EMOJI
    );
    add(
      "fromyaml-emoji-after-chunk-boundary",
      "default",
      FROMYAML,
      "s",
      "a".repeat(YAML_CHUNK) + EMOJI
    );
    add(
      "fromyaml-emoji-before-chunk-boundary",
      "default",
      FROMYAML,
      "s",
      "a".repeat(YAML_CHUNK - 2) + EMOJI
    );
    add("fromyaml-alias-scalar", "default", FROMYAML, "s", "a: &x foo\nb: *x\n");
    add("fromyaml-alias-sequence", "default", FROMYAML, "s", "a: &x [1, 2]\nb: *x\n");
    add(
      "fromyaml-binary",
      "default",
      "{% set v = s|fromyaml %}{{ v.b|tojson }}",
      "s",
      "b: !!binary aGVsbG8="
    );
  }

  private static List<Object> nestedLists(int depth) {
    List<Object> list = new ArrayList<>();
    for (int i = 1; i < depth; i++) {
      List<Object> outer = new ArrayList<>();
      outer.add(list);
      list = outer;
    }
    return list;
  }

  private static String brackets(int depth) {
    return "[".repeat(depth) + "]".repeat(depth);
  }

  // ---------- test mechanics ----------

  @Parameters(name = "{0}")
  public static Collection<Object[]> cases() {
    Set<String> seen = new HashSet<>();
    List<Object[]> params = new ArrayList<>();
    for (Case c : CASES) {
      if (!seen.add(c.name)) {
        throw new IllegalStateException("Duplicate golden case name: " + c.name);
      }
      if (!VARIANTS.containsKey(c.variant)) {
        throw new IllegalStateException(
          "Unknown config variant " + c.variant + " in case " + c.name
        );
      }
      params.add(new Object[] { c.name, c });
    }
    return params;
  }

  private final String name;
  private final Case testCase;

  public TemplateGoldenTest(String name, Case testCase) {
    this.name = name;
    this.testCase = testCase;
  }

  @Test
  public void itMatchesGoldenOutput() throws IOException {
    String actual = render(testCase);
    Path fixture = GOLDEN_DIR.resolve(testCase.name + FIXTURE_EXTENSION);

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.createDirectories(GOLDEN_DIR);
      Files.write(fixture, actual.getBytes(StandardCharsets.UTF_8));
      System.out.println(
        "[golden] wrote " + fixture + " (" + actual.length() + " chars)"
      );
      return;
    }

    if (!Files.exists(fixture)) {
      fail(
        "Golden case '" +
        testCase.name +
        "': fixture missing: " +
        fixture.toAbsolutePath() +
        " (run with -D" +
        UPDATE_PROPERTY +
        "=true). Actual:\n" +
        actual
      );
    }
    String expected = new String(Files.readAllBytes(fixture), StandardCharsets.UTF_8);
    if (!expected.equals(actual)) {
      fail(
        "Golden case '" +
        testCase.name +
        "' (variant " +
        testCase.variant +
        ") differs from expected file " +
        fixture.toAbsolutePath() +
        "\n" +
        diff(expected, actual)
      );
    }
  }

  static String render(Case c) {
    JinjavaConfig config = VARIANTS.get(c.variant).apply(null);
    Jinjava jinjava = new Jinjava(config);
    Map<String, Object> context = new LinkedHashMap<>(c.context);
    if (c.variant.startsWith("eager")) {
      context.put(DEFERRED_KEY, DeferredValue.instance());
    }
    RenderResult result;
    try {
      result = jinjava.renderForResult(c.template, context);
    } finally {
      // Jinjava keeps the interpreter in a thread local only during rendering; be defensive.
      while (JinjavaInterpreter.getCurrent() != null) {
        JinjavaInterpreter.popCurrent();
      }
    }
    return canonical(result);
  }

  static String canonical(RenderResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append(OUTPUT_HEADER).append('\n');
    sb.append(result.getOutput()).append('\n');
    sb.append(ERRORS_HEADER).append('\n');
    for (TemplateError error : result.getErrors()) {
      sb
        .append(error.getSeverity())
        .append(FIELD_SEPARATOR)
        .append(error.getReason())
        .append(FIELD_SEPARATOR)
        .append(error.getItem())
        .append(FIELD_SEPARATOR)
        .append(error.getMessage())
        .append('\n');
    }
    return IDENTITY_HASH.matcher(sb.toString()).replaceAll("@<identity-hash>");
  }

  static String diff(String expected, String actual) {
    String[] exp = expected.split("\n", -1);
    String[] act = actual.split("\n", -1);
    StringBuilder sb = new StringBuilder("--- expected\n+++ actual\n");
    int reported = 0;
    int max = Math.max(exp.length, act.length);
    for (int i = 0; i < max && reported < MAX_REPORTED_DIFFS; i++) {
      String e = i < exp.length ? exp[i] : null;
      String a = i < act.length ? act[i] : null;
      if (e == null || !e.equals(a)) {
        sb.append("@@ line ").append(i + 1).append(" @@\n");
        if (e != null) {
          sb.append('-').append(e).append('\n');
        }
        if (a != null) {
          sb.append('+').append(a).append('\n');
        }
        reported++;
      }
    }
    if (reported == 0) {
      sb.append("(no line-level difference; trailing bytes differ)\n");
    }
    return sb.toString();
  }
}
