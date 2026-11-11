package com.hubspot.jinjava.objects.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.BaseJinjavaTest;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.LegacyOverrides;
import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.objects.collections.SizeLimitingPyMap;
import com.hubspot.jinjava.testobjects.PyishObjectMapperTestObjects;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.DatabindException;

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
        .isInstanceOf(LengthLimitingJsonProcessingException.class)
        .hasMessageContaining("Max length of 10000 chars reached");
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

  private static JsonMappingException wrap(String message, Throwable cause) {
    return new JsonMappingException(null, message, cause);
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
    IOException wrapped = wrap("x", limitException());
    assertThatThrownBy(() -> PyishObjectMapper.handleLengthLimitingException(wrapped))
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
    assertThat(stripped).isInstanceOf(JsonMappingException.class);
    assertThat(stripped).isNotSameAs(innermost);
  }

  /** A value whose pyish serialization fails with the given exception. */
  private static final class FailingPyishSerializable implements PyishSerializable {

    private static final String FALLBACK_TEXT = "failing-pyish-value";
    private final IOException failure;

    private FailingPyishSerializable(IOException failure) {
      this.failure = failure;
    }

    @Override
    public void writePyishSelf(
      JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider
    ) throws IOException {
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
  public void itFallsBackToToStringWhenDeferredValueExceptionIsBehindNonWrapper() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThat(
      PyishObjectMapper.getAsPyishString(
        new FailingPyishSerializable(wrap("x", new IOException(deferred)))
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

  private static DatabindException wrapJackson3(String message, Throwable cause) {
    return DatabindException.from(
      (tools.jackson.core.JsonGenerator) null,
      message,
      cause
    );
  }

  @Test
  public void itMapsLengthLimitBehindJackson3IoWrapper() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(
          JacksonIOException.construct(limitException())
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itMapsLengthLimitBehindStackedJackson3Wrappers() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(
          wrapJackson3("outer", JacksonIOException.construct(limitException()))
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itMapsLengthLimitBehindMixedJacksonGenerations() {
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(
          wrap("outer", wrapJackson3("inner", limitException()))
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itRethrowsOutputTooBigExceptionBehindJackson3Wrapper() {
    OutputTooBigException tooBig = new OutputTooBigException(
      LIMIT_MAX_SIZE,
      LIMIT_ATTEMPTED_SIZE
    );
    assertThatThrownBy(() ->
        PyishObjectMapper.handleLengthLimitingException(wrapJackson3("x", tooBig))
      )
      .isSameAs(tooBig);
  }

  @Test
  public void itStopsJackson3LengthLimitUnwrappingAtFirstNonWrapper() {
    PyishObjectMapper.handleLengthLimitingException(
      wrapJackson3("x", new RuntimeException(limitException()))
    );
    PyishObjectMapper.handleLengthLimitingException(
      JacksonIOException.construct(new IOException("plain"))
    );
  }

  @Test
  public void itStripsJackson3WrappersToDeferredValueException() {
    DeferredValueException deferred = new DeferredValueException("deferred");
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrapJackson3("x", deferred)))
      .isSameAs(deferred);
    assertThat(
      PyishObjectMapper.stripJacksonWrappers(
        wrapJackson3("outer", wrapJackson3("inner", deferred))
      )
    )
      .isSameAs(deferred);
    assertThat(
      PyishObjectMapper.stripJacksonWrappers(
        wrapJackson3("outer", wrap("inner", deferred))
      )
    )
      .isSameAs(deferred);
  }

  @Test
  public void itStopsStrippingJackson3WrappersAtFirstNonWrapper() {
    RuntimeException runtime = new RuntimeException(
      new DeferredValueException("deferred")
    );
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrapJackson3("x", runtime)))
      .isSameAs(runtime);
  }

  @Test
  public void itStripsJackson3WrapperWithoutCauseToNull() {
    assertThat(PyishObjectMapper.stripJacksonWrappers(wrapJackson3("x", null))).isNull();
  }

  @Test
  public void itBoundsStrippingOfLongJackson3WrapperChains() {
    Throwable innermost = new DeferredValueException("deferred");
    Throwable chain = innermost;
    for (int i = 0; i < MAX_WRAPPER_DEPTH_PROBE; i++) {
      chain = wrapJackson3("layer " + i, chain);
    }
    Throwable stripped = PyishObjectMapper.stripJacksonWrappers(chain);
    assertThat(stripped).isInstanceOf(DatabindException.class);
    assertThat(stripped).isNotSameAs(innermost);
  }
}
