package com.hubspot.jinjava.golden;

import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.objects.collections.SizeLimitingPyMap;
import com.hubspot.jinjava.objects.serialization.PyishObjectMapper;
import com.hubspot.jinjava.objects.serialization.PyishSerializable;
import com.hubspot.jinjava.testobjects.PyishObjectMapperTestObjects;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.DeferredPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.IoFailingPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.IoWrappedDeferredPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.PlainPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.RuntimeWrappedDeferredPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.SevenPropertyBean;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.TooBigPyishSerializable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Corpus for {@link PyishOutcomeTest}: every value of {@link JsonFilterOutcomeCorpus#writeItems()}
 * plus the pyish specific values of {@link #pyishItems()}, written through every pyish entry
 * point ({@link #GET_AS_PYISH_STRING}, {@link #GET_AS_UNQUOTED_PYISH_STRING},
 * {@link #GET_AS_PYISH_STRING_OR_THROW}, {@link #WRITE_VALUE_AS_STRING}) in five contexts: no
 * interpreter, an interpreter, an interpreter with snake case property naming, and an
 * interpreter whose {@code maxOutputSize} of {@value #TINY_OUTPUT_LIMIT} or
 * {@value #LARGE_OUTPUT_LIMIT} makes the mapper write into a
 * {@link com.hubspot.jinjava.objects.serialization.LengthLimitingWriter}.
 *
 * <p>Every cell is reduced to an outcome class by
 * {@link JsonFilterOutcomeCorpus#outcome(Supplier)}. Any {@link IOException} thrown by
 * {@code getAsPyishStringOrThrow} is reported as one class, {@link #IO_EXCEPTION_CLASS}, since
 * callers only catch {@code IOException}. The outcomes recorded in
 * {@code src/test/resources/outcomes/pyish-outcomes.txt} were taken on Jackson 2.20.1, before the
 * pyish serializer moved to Jackson 3.
 */
public final class PyishOutcomeCorpus {

  static final String GET_AS_PYISH_STRING = "getAsPyishString";
  static final String GET_AS_UNQUOTED_PYISH_STRING = "getAsUnquotedPyishString";
  static final String GET_AS_PYISH_STRING_OR_THROW = "getAsPyishStringOrThrow";
  static final String WRITE_VALUE_AS_STRING = "writeValueAsString";
  static final String CONTEXT_NO_INTERPRETER = "no-interpreter";
  static final String CONTEXT_INTERPRETER = "interpreter";
  static final String CONTEXT_SNAKE_CASE = "snake-case";
  static final String CONTEXT_TINY_LIMIT = "limit60";
  static final String CONTEXT_LARGE_LIMIT = "limit10000";
  static final String IO_EXCEPTION_CLASS = "java.io.UncheckedIOException";
  static final String TOO_BIG_PREFIX = "TOO_BIG ";
  static final String TOO_BIG_SIZE_PLACEHOLDER = "<size>";
  static final String FAILING_GETTER_ITEM_MARKER = "getter throwing";
  static final String GETTER_TOO_BIG_OUTCOME =
    TOO_BIG_PREFIX +
    new OutputTooBigException(
      JsonFilterOutcomeCorpus.OTB_MAX_SIZE,
      JsonFilterOutcomeCorpus.OTB_SIZE
    )
      .getMessage();
  static final long TINY_OUTPUT_LIMIT = 60L;
  static final long LARGE_OUTPUT_LIMIT = 10_000L;
  static final int MAP_SIZE_LIMIT = 10;
  static final List<String> OPERATIONS = Arrays.asList(
    GET_AS_PYISH_STRING,
    GET_AS_UNQUOTED_PYISH_STRING,
    GET_AS_PYISH_STRING_OR_THROW,
    WRITE_VALUE_AS_STRING
  );

  private PyishOutcomeCorpus() {}

  /** All cells, one per line: context and operation, item, outcome, separated by a tab. */
  static List<String> record() {
    List<String> lines = new ArrayList<>();
    Map<String, Supplier<Object>> items = items();
    for (Map.Entry<String, JinjavaInterpreter> context : contexts().entrySet()) {
      JinjavaInterpreter interpreter = context.getValue();
      if (interpreter != null) {
        JinjavaInterpreter.pushCurrent(interpreter);
      }
      try {
        for (String operation : OPERATIONS) {
          for (Map.Entry<String, Supplier<Object>> item : items.entrySet()) {
            lines.add(
              context.getKey() +
              "/" +
              operation +
              JsonFilterOutcomeCorpus.FIELD_SEPARATOR +
              item.getKey() +
              JsonFilterOutcomeCorpus.FIELD_SEPARATOR +
              maskIdentityHashSize(
                JsonFilterOutcomeCorpus.outcome(() ->
                  operation(operation).apply(item.getValue().get())
                ),
                item.getKey()
              )
            );
          }
        }
      } finally {
        if (interpreter != null) {
          JinjavaInterpreter.popCurrent();
        }
      }
    }
    return lines;
  }

  /**
   * The attempted size in the message of the {@code OutputTooBigException} of the context limit
   * depends on the length of an identity hash code, 7 or 8 hex digits, for the values whose
   * getter fails: their serialization falls back to the {@code toString()} of a bean that does
   * not override it. Measured on Jackson 2.20.1, over 60 recordings only the seven cells of
   * {@code limit60/getAsPyishString} for these values varied. The size is masked for them, but
   * not for the {@code OutputTooBigException} that the getter itself throws.
   */
  static String maskIdentityHashSize(String outcome, String itemName) {
    if (
      outcome.startsWith(TOO_BIG_PREFIX) &&
      itemName.contains(FAILING_GETTER_ITEM_MARKER) &&
      !outcome.equals(GETTER_TOO_BIG_OUTCOME)
    ) {
      return (
        TOO_BIG_PREFIX +
        TOO_BIG_SIZE_PLACEHOLDER +
        outcome.substring(outcome.indexOf(" byte"))
      );
    }
    return outcome;
  }

  /** The contexts by name; the interpreter is null where none is current. */
  static Map<String, JinjavaInterpreter> contexts() {
    Map<String, JinjavaInterpreter> contexts = new LinkedHashMap<>();
    contexts.put(CONTEXT_NO_INTERPRETER, null);
    contexts.put(
      CONTEXT_INTERPRETER,
      JsonFilterOutcomeCorpus.interpreter(false, JsonFilterOutcomeCorpus.NO_OUTPUT_LIMIT)
    );
    contexts.put(
      CONTEXT_SNAKE_CASE,
      JsonFilterOutcomeCorpus.interpreter(true, JsonFilterOutcomeCorpus.NO_OUTPUT_LIMIT)
    );
    contexts.put(
      CONTEXT_TINY_LIMIT,
      JsonFilterOutcomeCorpus.interpreter(false, TINY_OUTPUT_LIMIT)
    );
    contexts.put(
      CONTEXT_LARGE_LIMIT,
      JsonFilterOutcomeCorpus.interpreter(false, LARGE_OUTPUT_LIMIT)
    );
    return contexts;
  }

  /** The pyish entry point with the given name. */
  static Function<Object, Object> operation(String name) {
    switch (name) {
      case GET_AS_PYISH_STRING:
        return PyishObjectMapper::getAsPyishString;
      case GET_AS_UNQUOTED_PYISH_STRING:
        return PyishObjectMapper::getAsUnquotedPyishString;
      case GET_AS_PYISH_STRING_OR_THROW:
        return PyishOutcomeCorpus::getAsPyishStringOrThrow;
      case WRITE_VALUE_AS_STRING:
        return PyishSerializable::writeValueAsString;
      default:
        throw new IllegalArgumentException("Unknown pyish operation " + name);
    }
  }

  /** Reports any {@link IOException} as an {@link UncheckedIOException}, one outcome class. */
  static String getAsPyishStringOrThrow(Object value) {
    try {
      return PyishObjectMapper.getAsPyishStringOrThrow(value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Map<String, Supplier<Object>> items() {
    Map<String, Supplier<Object>> items = new LinkedHashMap<>(
      JsonFilterOutcomeCorpus.writeItems()
    );
    items.putAll(pyishItems());
    return items;
  }

  /** Values that matter to the pyish serializer and not to the JSON filters. */
  static Map<String, Supplier<Object>> pyishItems() {
    Map<String, Supplier<Object>> items = new LinkedHashMap<>();
    items.put("bean with getFooBar", () -> new PyishObjectMapperTestObjects.Foo("bar"));
    items.put("bean with 7 properties", SevenPropertyBean::new);
    items.put("nested lists and maps", PyishOutcomeCorpus::nestedContainers);
    items.put("list of map entries", PyishOutcomeCorpus::mapEntries);
    items.put("map entry set of a SizeLimitingPyMap", PyishOutcomeCorpus::entrySet);
    items.put(
      "strings that look like values",
      () -> new ArrayList<>(Arrays.asList("true", "1.5", "abc"))
    );
    items.put("Boolean and Integer", () -> new ArrayList<>(Arrays.asList(true, 1)));
    items.put("StringBuilder", () -> new StringBuilder("sb"));
    items.put("object whose toString is a number", () -> new NumberLikeObject());
    items.put("PyishSerializable", PlainPyishSerializable::new);
    items.put(
      "PyishSerializable in list and map",
      () -> new ArrayList<>(Arrays.asList(1, new PlainPyishSerializable(), map("k", "v")))
    );
    items.put(
      "PyishSerializable throwing DeferredValueException",
      DeferredPyishSerializable::new
    );
    items.put(
      "PyishSerializable throwing IOException caused by DeferredValueException",
      IoWrappedDeferredPyishSerializable::new
    );
    items.put(
      "PyishSerializable throwing RuntimeException caused by DeferredValueException",
      RuntimeWrappedDeferredPyishSerializable::new
    );
    items.put(
      "PyishSerializable throwing OutputTooBigException",
      TooBigPyishSerializable::new
    );
    items.put("PyishSerializable throwing IOException", IoFailingPyishSerializable::new);
    items.put(
      "list holding PyishSerializable throwing DeferredValueException",
      () -> new ArrayList<>(Arrays.asList(1, new DeferredPyishSerializable()))
    );
    items.put(
      "list holding PyishSerializable throwing IOException caused by DeferredValueException",
      () -> new ArrayList<>(Arrays.asList(1, new IoWrappedDeferredPyishSerializable()))
    );
    items.put(
      "list holding PyishSerializable throwing OutputTooBigException",
      () -> new ArrayList<>(Arrays.asList(1, new TooBigPyishSerializable()))
    );
    items.put(
      "map holding PyishSerializable throwing DeferredValueException",
      () -> map("p", new DeferredPyishSerializable())
    );
    return items;
  }

  private static Map<String, Object> map(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static Object nestedContainers() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put(
      "l",
      new ArrayList<>(
        Arrays.asList(1, new ArrayList<>(Arrays.asList("a", "b")), map("k", "v"))
      )
    );
    root.put("m", map("deep", new ArrayList<>(Arrays.asList(1, 2))));
    root.put("e", new ArrayList<>());
    root.put("em", new LinkedHashMap<>());
    return root;
  }

  private static Object mapEntries() {
    List<Object> entries = new ArrayList<>();
    entries.add(new AbstractMap.SimpleEntry<>("bar", map("foobar", new ArrayList<>())));
    entries.add(new AbstractMap.SimpleEntry<>("foo", "bar"));
    return entries;
  }

  private static Object entrySet() {
    SizeLimitingPyMap map = new SizeLimitingPyMap(new HashMap<>(), MAP_SIZE_LIMIT);
    map.put("foo", "bar");
    map.put("bar", map("foobar", new ArrayList<>()));
    return map.items();
  }

  /** An object that is neither a number nor a string, whose text is a number. */
  private static final class NumberLikeObject {

    @Override
    public String toString() {
      return "42";
    }
  }
}
