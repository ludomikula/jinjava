package com.hubspot.jinjava.objects.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.BaseJinjavaTest;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.LegacyOverrides;
import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.objects.Namespace;
import com.hubspot.jinjava.objects.collections.SizeLimitingPyMap;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.Color;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.DeferredGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.ThrowingBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.TooBigGetterBean;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.Holder;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.PlainObject;
import com.hubspot.jinjava.testobjects.PyishObjectMapperTestObjects;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.DeferredPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.IoFailingPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.IoWrappedDeferredPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.PlainPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.RuntimeWrappedDeferredPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.SevenPropertyBean;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.TooBigPyishSerializable;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class PyishObjectMapperTest {

  @Test
  public void itSerializesMapWithNullKeysAsEmptyString() {
    Map<String, Object> map = new SizeLimitingPyMap(new HashMap<>(), 10);
    map.put("foo", "bar");
    map.put(null, "null");
    assertThat(PyishObjectMapper.getAsPyishString(map))
      .isEqualTo("{'': 'null', 'foo': 'bar'} ");
  }

  @Test
  public void itSerializesMapEntrySet() {
    SizeLimitingPyMap map = new SizeLimitingPyMap(new HashMap<>(), 10);
    map.put("foo", "bar");
    map.put("bar", ImmutableMap.of("foobar", new ArrayList<>()));
    String result = PyishObjectMapper.getAsPyishString(map.items());
    assertThat(result)
      .isEqualTo("[fn:map_entry('bar', {'foobar': []} ), fn:map_entry('foo', 'bar')]");
  }

  @Test
  public void itSerializesMapEntrySetWithLimit() {
    SizeLimitingPyMap map = new SizeLimitingPyMap(new HashMap<>(), 10);
    map.put("foo", "bar");
    map.put("bar", ImmutableMap.of("foobar", new ArrayList<>()));

    Jinjava jinjava = new Jinjava(
      BaseJinjavaTest.newConfigBuilder().withMaxOutputSize(10000).build()
    );
    try {
      JinjavaInterpreter.pushCurrent(jinjava.newInterpreter());
      String result = PyishObjectMapper.getAsPyishString(map.items());
      assertThat(result)
        .isEqualTo("[fn:map_entry('bar', {'foobar': []} ), fn:map_entry('foo', 'bar')]");
    } finally {
      JinjavaInterpreter.popCurrent();
    }
  }

  @Test
  public void itSerializesMapWithNullValues() {
    Map<String, Object> map = new SizeLimitingPyMap(new HashMap<>(), 10);
    map.put("foo", "bar");
    map.put("foobar", null);
    assertThat(PyishObjectMapper.getAsPyishString(map))
      .isEqualTo("{'foobar': null, 'foo': 'bar'} ");
  }

  @Test
  public void itLimitsDepth() {
    final List<Object> original = new ArrayList<>();
    List<Object> list = original;
    for (int i = 0; i < 20; i++) {
      List<Object> temp = new ArrayList<>();
      list.add("abcdefghijklmnopqrstuvwxyz");
      list.add(temp);
      list = temp;
    }
    list.add("a");
    list.add(original);
    try {
      Jinjava jinjava = new Jinjava(
        BaseJinjavaTest.newConfigBuilder().withMaxOutputSize(10000).build()
      );
      JinjavaInterpreter.pushCurrent(jinjava.newInterpreter());
      assertThatThrownBy(() -> PyishObjectMapper.getAsPyishStringOrThrow(original))
        .as("The string to be serialized is larger than the max output size")
        .isExactlyInstanceOf(IOException.class)
        .hasMessageContaining("Max length of 10000 chars reached")
        .hasRootCauseInstanceOf(LengthLimitingJsonProcessingException.class);
      assertThatThrownBy(() -> PyishObjectMapper.getAsPyishString(original))
        .isInstanceOf(OutputTooBigException.class);
    } finally {
      JinjavaInterpreter.popCurrent();
    }
  }

  @Test
  public void itLimitsOutputSize() {
    String input = RandomStringUtils.random(10002);
    try {
      Jinjava jinjava = new Jinjava(
        BaseJinjavaTest.newConfigBuilder().withMaxOutputSize(10000).build()
      );
      JinjavaInterpreter.pushCurrent(jinjava.newInterpreter());
      assertThatThrownBy(() -> PyishObjectMapper.getAsPyishString(input))
        .isInstanceOf(OutputTooBigException.class)
        .hasMessageContaining("over limit of 10000 bytes");
    } finally {
      JinjavaInterpreter.popCurrent();
    }
  }

  @Test
  public void itSerializesToSnakeCaseAccessibleMap() {
    assertThat(
      PyishObjectMapper.getAsPyishString(new PyishObjectMapperTestObjects.Foo("bar"))
    )
      .isEqualTo("{'fooBar': 'bar'} |allow_snake_case");
  }

  @Test
  public void itSerializesToSnakeCaseAccessibleMapWhenInMapEntry() {
    assertThat(
      PyishObjectMapper.getAsPyishString(
        new AbstractMap.SimpleImmutableEntry<>(
          "foo",
          new PyishObjectMapperTestObjects.Foo("bar")
        )
      )
    )
      .isEqualTo("fn:map_entry('foo', {'fooBar': 'bar'} |allow_snake_case)");
  }

  @Test
  public void itDoesNotConvertToSnakeCaseMapWhenResultIsForOutput() {
    Jinjava jinjava = new Jinjava(
      BaseJinjavaTest
        .newConfigBuilder()
        .withLegacyOverrides(
          LegacyOverrides.newBuilder().withUsePyishObjectMapper(true).build()
        )
        .build()
    );
    JinjavaInterpreter interpreter = jinjava.newInterpreter();
    interpreter.getContext().put("foo", new PyishObjectMapperTestObjects.Foo("bar"));
    assertThat(interpreter.render("{{ foo }}")).isEqualTo("{'fooBar': 'bar'}");
  }

  @Test
  public void itSerializesToSnakeCaseWhenLegacyOverrideIsSet() {
    Jinjava jinjava = new Jinjava(
      BaseJinjavaTest
        .newConfigBuilder()
        .withLegacyOverrides(
          LegacyOverrides
            .newBuilder()
            .withUsePyishObjectMapper(true)
            .withUseSnakeCasePropertyNaming(true)
            .build()
        )
        .build()
    );
    JinjavaInterpreter interpreter = jinjava.newInterpreter();
    try {
      JinjavaInterpreter.pushCurrent(interpreter);
      interpreter.getContext().put("foo", new PyishObjectMapperTestObjects.Foo("bar"));
      assertThat(interpreter.render("{{ foo }}")).isEqualTo("{'foo_bar': 'bar'}");
    } finally {
      JinjavaInterpreter.popCurrent();
    }
  }

  @Test
  public void itSerializesOptional() {
    assertThat(PyishObjectMapper.getAsPyishString(Optional.of("foo"))).isEqualTo("'foo'");
  }

  private static final int LIMIT_MAX_SIZE = 10;
  private static final int LIMIT_ATTEMPTED_SIZE = 11;
  private static final int MAX_WRAPPER_DEPTH_PROBE = 40;

  private static LengthLimitingJsonProcessingException limitException() {
    return new LengthLimitingJsonProcessingException(
      LIMIT_MAX_SIZE,
      LIMIT_ATTEMPTED_SIZE
    );
  }

  private static DatabindException wrap(String message, Throwable cause) {
    return DatabindException.from((JsonGenerator) null, message, cause);
  }

  @Test
  public void itMapsLengthLimitBehindOneJacksonWrapper() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(wrap("x", limitException()))
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itMapsLengthLimitBehindStackedJacksonWrappers() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(
          wrap("outer", wrap("inner", limitException()))
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itMapsLengthLimitBehindJacksonIoWrapper() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(
          JacksonIOException.construct(limitException())
        )
      )
      .isInstanceOf(OutputTooBigException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(
          wrap("outer", JacksonIOException.construct(limitException()))
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itMapsBareLengthLimitAndOutputTooBig() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException((Throwable) limitException())
      )
      .isInstanceOf(OutputTooBigException.class);
    OutputTooBigException tooBig = new OutputTooBigException(
      LIMIT_MAX_SIZE,
      LIMIT_ATTEMPTED_SIZE
    );
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(wrap("x", tooBig))
      )
      .isSameAs(tooBig);
  }

  @Test
  public void itKeepsIoExceptionOverloadOfLengthLimitHandling() {
    IOException bare = limitException();
    assertThatThrownBy(() -> PyishObjectMapper.handleLengthLimitingException(bare))
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itStopsLengthLimitUnwrappingAtFirstNonWrapper() {
    PyishObjectMapper.handleLengthLimitingException(
      new RuntimeException(limitException())
    );
    PyishObjectMapper.handleLengthLimitingException(
      wrap("x", new RuntimeException(limitException()))
    );
    PyishObjectMapper.handleLengthLimitingException(
      JacksonIOException.construct(new IOException("plain"))
    );
  }

  @Test
  public void itIgnoresUnrelatedChainsInLengthLimitHandling() {
    PyishObjectMapper.handleLengthLimitingException(
      wrap("outer", wrap("inner", new IllegalStateException("unrelated")))
    );
    PyishObjectMapper.handleLengthLimitingException(new IOException("plain"));
  }

  @Test
  public void itStripsJacksonWrappersToDeferredValueException() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThat(PyishObjectMapper.stripJacksonWrappers(deferred)).isSameAs(deferred);
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrap("x", deferred)))
      .isSameAs(deferred);
    assertThat(
      PyishObjectMapper.stripJacksonWrappers(wrap("outer", wrap("inner", deferred)))
    )
      .isSameAs(deferred);
    IOException io = new IOException("io", deferred);
    assertThat(PyishObjectMapper.stripJacksonWrappers(JacksonIOException.construct(io)))
      .isSameAs(io);
  }

  @Test
  public void itStopsStrippingAtFirstNonWrapper() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    RuntimeException runtime = new RuntimeException(deferred);
    assertThat(PyishObjectMapper.stripJacksonWrappers(runtime)).isSameAs(runtime);
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrap("x", runtime)))
      .isSameAs(runtime);
    IllegalStateException unrelated = new IllegalStateException("unrelated");
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrap("x", wrap("y", unrelated))))
      .isSameAs(unrelated);
  }

  @Test
  public void itStripsNullToNull() {
    assertThat(PyishObjectMapper.stripJacksonWrappers(null)).isNull();
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrap("x", null))).isNull();
  }

  @Test
  public void itBoundsStrippingOfLongWrapperChains() {
    Throwable innermost = new DeferredValueException("deferred");
    Throwable chain = innermost;
    for (int i = 0; i < MAX_WRAPPER_DEPTH_PROBE; i++) {
      chain = wrap("layer " + i, chain);
    }
    Throwable stripped = PyishObjectMapper.stripJacksonWrappers(chain);
    assertThat(stripped).isInstanceOf(DatabindException.class);
    assertThat(stripped).isNotSameAs(innermost);
  }

  /** A value whose pyish serialization fails with the given exception. */
  private static final class FailingPyishSerializable implements PyishSerializable {

    private static final String FALLBACK_TEXT = "failing-pyish-value";
    private final RuntimeException failure;

    private FailingPyishSerializable(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void writePyishSelf(
      JsonGenerator jsonGenerator,
      SerializationContext serializationContext
    ) {
      throw failure;
    }

    @Override
    public String toString() {
      return FALLBACK_TEXT;
    }
  }

  @Test
  public void itRethrowsDeferredValueExceptionBehindOneJacksonWrapperFromSerializer() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(
          new FailingPyishSerializable(wrap("x", deferred))
        )
      )
      .isSameAs(deferred);
  }

  @Test
  public void itRethrowsDeferredValueExceptionBehindStackedJacksonWrappersFromSerializer() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(
          new FailingPyishSerializable(wrap("outer", wrap("inner", deferred)))
        )
      )
      .isSameAs(deferred);
  }

  @Test
  public void itRethrowsDeferredValueExceptionThrownRawByASerializer() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(new FailingPyishSerializable(deferred))
      )
      .isSameAs(deferred);
  }

  @Test
  public void itFallsBackToToStringWhenDeferredValueExceptionIsBehindNonWrapper() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThat(
      PyishObjectMapper.getAsPyishString(
        new FailingPyishSerializable(wrap("x", new IOException(deferred)))
      )
    )
      .isEqualTo(FailingPyishSerializable.FALLBACK_TEXT);
    assertThat(
      PyishObjectMapper.getAsPyishString(
        new FailingPyishSerializable(new RuntimeException(deferred))
      )
    )
      .isEqualTo(FailingPyishSerializable.FALLBACK_TEXT);
  }

  @Test
  public void itMapsLengthLimitBehindStackedJacksonWrappersFromSerializer() {
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(
          new FailingPyishSerializable(wrap("outer", wrap("inner", limitException())))
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itRethrowsOutputTooBigExceptionThrownRawByASerializer() {
    OutputTooBigException tooBig = new OutputTooBigException(
      LIMIT_MAX_SIZE,
      LIMIT_ATTEMPTED_SIZE
    );
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(new FailingPyishSerializable(tooBig))
      )
      .isSameAs(tooBig);
  }

  @Test
  public void itWrapsEveryFailureOfGetAsPyishStringOrThrowInOneIoException() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new FailingPyishSerializable(deferred))
      )
      .isExactlyInstanceOf(IOException.class)
      .hasMessage("deferred")
      .hasCause(deferred);
    DatabindException wrapper = wrap("wrapper", deferred);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new FailingPyishSerializable(wrapper))
      )
      .isExactlyInstanceOf(IOException.class)
      .hasCause(wrapper)
      .hasRootCause(deferred);
  }

  private static final String SEVEN_PROPERTY_BEAN_TEXT =
    "{'fooBar': '1', 'barBaz': '2', 'bazQux': '3', 'quxQuux': '4', 'quuxCorge': '5'," +
    " 'corgeGrault': '6', 'graultGarply': '7'} ";

  @Test
  public void itSerializesToSnakeCaseAccessibleMapWithSevenProperties() {
    assertThat(PyishObjectMapper.getAsPyishString(new SevenPropertyBean()))
      .isEqualTo(SEVEN_PROPERTY_BEAN_TEXT + "|allow_snake_case");
  }

  @Test
  public void itDoesNotAddSnakeCaseFilterToBeansWithSevenPropertiesForOutput() {
    assertThat(PyishObjectMapper.getAsUnquotedPyishString(new SevenPropertyBean()))
      .isEqualTo(SEVEN_PROPERTY_BEAN_TEXT.trim());
  }

  @Test
  public void itSerializesSnakeCaseBeansWithOneAndSevenPropertiesInSnakeCase() {
    Jinjava jinjava = new Jinjava(
      BaseJinjavaTest
        .newConfigBuilder()
        .withLegacyOverrides(
          LegacyOverrides
            .newBuilder()
            .withUsePyishObjectMapper(true)
            .withUseSnakeCasePropertyNaming(true)
            .build()
        )
        .build()
    );
    try {
      JinjavaInterpreter.pushCurrent(jinjava.newInterpreter());
      assertThat(
        PyishObjectMapper.getAsPyishString(new PyishObjectMapperTestObjects.Foo("bar"))
      )
        .isEqualTo("{'foo_bar': 'bar'} ");
      assertThat(PyishObjectMapper.getAsPyishString(new SevenPropertyBean()))
        .isEqualTo(
          "{'foo_bar': '1', 'bar_baz': '2', 'baz_qux': '3', 'qux_quux': '4'," +
          " 'quux_corge': '5', 'corge_grault': '6', 'grault_garply': '7'} "
        );
    } finally {
      JinjavaInterpreter.popCurrent();
    }
  }

  @Test
  public void itEscapesApostropheOnlyAndKeepsSlashAndWhitespace() {
    assertThat(PyishObjectMapper.getAsPyishString("a/b'c\nd\te\\f\"g\""))
      .isEqualTo("'a/b\\'c\nd\te\\\\f\\\"g\\\"'");
  }

  @Test
  public void itSerializesEmptyBeanAsItsToString() {
    assertThat(PyishObjectMapper.getAsPyishString(new PlainObject()))
      .isEqualTo("PlainObject!");
  }

  @Test
  public void itSerializesEnumByName() {
    assertThat(PyishObjectMapper.getAsPyishString(Color.RED)).isEqualTo("'RED'");
  }

  @Test
  public void itSerializesDateAsEpochMillis() {
    assertThat(PyishObjectMapper.getAsPyishString(new Date(0))).isEqualTo("0");
  }

  @Test
  public void itSerializesEmptyOptionalAsNull() {
    assertThat(PyishObjectMapper.getAsPyishString(Optional.empty())).isEqualTo("null");
  }

  @Test
  public void itSerializesNestedListsAndMaps() {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("k", "v");
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("l", new ArrayList<>(Arrays.asList(1, Arrays.asList("a", "b"), inner)));
    root.put("m", ImmutableMap.of("deep", Arrays.asList(1, 2)));
    root.put("e", new ArrayList<>());
    root.put("em", new LinkedHashMap<>());
    assertThat(PyishObjectMapper.getAsPyishString(root))
      .isEqualTo(
        "{'l': [1, ['a', 'b'], {'k': 'v'} ], 'm': {'deep': [1, 2]} , 'e': [], 'em': {} } "
      );
  }

  @Test
  public void itSerializesNullKeyInNestedMapAsEmptyString() {
    Map<String, Object> nested = new HashMap<>();
    nested.put(null, 1);
    assertThat(PyishObjectMapper.getAsPyishString(Arrays.asList(nested)))
      .isEqualTo("[{'': 1} ]");
  }

  @Test
  public void itSerializesNamespaceWithItsOwnPyishString() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("a", 1);
    assertThat(PyishObjectMapper.getAsPyishString(new Namespace(map)))
      .isEqualTo("namespace({'a': 1} )");
  }

  @Test
  public void itSurfacesDeferredValueExceptionFromBeanGetter() {
    assertThatThrownBy(() -> PyishObjectMapper.getAsPyishString(new DeferredGetterBean()))
      .isInstanceOf(DeferredValueException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(new Holder(new DeferredGetterBean()))
      )
      .isInstanceOf(DeferredValueException.class);
  }

  @Test
  public void itSurfacesDeferredValueExceptionFromPyishSerializable() {
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(new DeferredPyishSerializable())
      )
      .isInstanceOf(DeferredValueException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(
          Arrays.asList(1, new DeferredPyishSerializable())
        )
      )
      .isInstanceOf(DeferredValueException.class);
  }

  @Test
  public void itDoesNotSurfaceDeferredValueExceptionBehindNonWrapper() {
    assertThat(
      PyishObjectMapper.getAsPyishString(new RuntimeWrappedDeferredPyishSerializable())
    )
      .isEqualTo("RuntimeWrappedDeferredPyishSerializable!");
    assertThat(
      PyishObjectMapper.getAsPyishString(new IoWrappedDeferredPyishSerializable())
    )
      .isEqualTo("IoWrappedDeferredPyishSerializable!");
  }

  @Test
  public void itSurfacesOutputTooBigExceptionFromPyishSerializableAndOutputBeanGetter() {
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishString(new TooBigPyishSerializable())
      )
      .isInstanceOf(OutputTooBigException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsUnquotedPyishString(new TooBigGetterBean())
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itFallsBackToToStringWhenBeanGetterThrows() {
    assertThat(PyishObjectMapper.getAsUnquotedPyishString(new ThrowingBean()))
      .matches(".*ThrowingBean@[0-9a-f]+");
    assertThat(PyishObjectMapper.getAsPyishString(new ThrowingBean()))
      .matches("'.*ThrowingBean@[0-9a-f]+'\\|allow_snake_case");
  }

  @Test
  public void itThrowsIoExceptionFromGetAsPyishStringOrThrowForEveryFailure() {
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new ThrowingBean(), true)
      )
      .isInstanceOf(IOException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new DeferredGetterBean(), true)
      )
      .isInstanceOf(IOException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new TooBigGetterBean(), true)
      )
      .isInstanceOf(IOException.class);
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new IoFailingPyishSerializable())
      )
      .isInstanceOf(IOException.class);
  }

  @Test
  public void itWritesSelfWriterValueWithoutInterpreter() {
    assertThat(PyishSerializable.writeValueAsString(Arrays.asList("it's", 1)))
      .isEqualTo("['it\\'s', 1]");
    assertThat(PyishSerializable.writeValueAsString(new PlainObject()))
      .isEqualTo("'PlainObject!'");
    assertThatThrownBy(() ->
        PyishSerializable.writeValueAsString(new DeferredGetterBean())
      )
      .isInstanceOf(DeferredValueException.class);
  }

  @Test
  public void itReportsIoExceptionOfAppendPyishStringAsJacksonIoException() {
    assertThatThrownBy(() ->
        PyishObjectMapper.getAsPyishStringOrThrow(new IoFailingPyishSerializable())
      )
      .isExactlyInstanceOf(IOException.class)
      .hasCauseInstanceOf(JacksonIOException.class)
      .hasRootCauseMessage(PyishOutcomeTestObjects.IO_MESSAGE);
  }

  @Test
  public void itSerializesPyishSerializablesInContainersWithAnotherPyishSerializable() {
    assertThat(
      PyishObjectMapper.getAsPyishString(
        ImmutableMap.of("a", new PlainPyishSerializable(), "b", "x")
      )
    )
      .isEqualTo("{'a': ps(), 'b': 'x'} ");
  }

  /**
   * Known break (index plan A15): a mapper that registers {@link PyishSerializer} itself no
   * longer gets {@link PyishPrettyPrinter} switched on in the middle of the document, because a
   * Jackson 3 generator cannot change its pretty printer. Jackson 2 wrote
   * <code>{"a": ps(), "b": "x", "c": [1, 2]} </code> (the pyish printer from the first
   * {@code PyishSerializable} on, and so with a trailing space); Jackson 3 writes the document
   * with the printer of the writer, here the default compact one.
   */
  @Test
  public void itNoLongerSwitchesToThePyishPrettyPrinterForADownstreamMapper() {
    JsonMapper mapper = JsonMapper
      .builder()
      .addModule(
        new SimpleModule()
          .addSerializer(PyishSerializable.class, PyishSerializer.INSTANCE)
      )
      .build();
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("a", new PlainPyishSerializable());
    document.put("b", "x");
    document.put("c", Arrays.asList(1, 2));
    assertThat(mapper.writeValueAsString(document))
      .isEqualTo("{\"a\":ps(),\"b\":\"x\",\"c\":[1,2]}");
  }

  @Test
  public void itStillEscapesApostrophesForADownstreamMapper() {
    JsonMapper mapper = JsonMapper
      .builder()
      .addModule(new SimpleModule().addSerializer(Object.class, PyishSerializer.INSTANCE))
      .build();
    assertThat(mapper.writeValueAsString("it's")).isEqualTo("\"it\\'s\"");
  }
}
