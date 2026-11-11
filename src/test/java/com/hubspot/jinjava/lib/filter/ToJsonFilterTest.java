package com.hubspot.jinjava.lib.filter;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.BaseJinjavaTest;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.InvalidInputException;
import com.hubspot.jinjava.interpret.InvalidReason;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.NullValue;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.AcronymBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.CamelBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.DeferredGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.EmptyBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.ThrowingBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.TooBigGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.WrappedDeferredGetterBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.WrappedTooBigGetterBean;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.Holder;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.ThrowingList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class ToJsonFilterTest extends BaseInterpretingTest {

  private ToJsonFilter filter;

  @Before
  public void setup() {
    filter = new ToJsonFilter();
  }

  @Test
  public void itWritesObjectAsString() {
    int[] testArray = new int[] { 4, 1, 2 };
    assertThat(filter.filter(testArray, interpreter)).isEqualTo("[4,1,2]");

    Map<String, Object> testMap = new LinkedHashMap<>();
    testMap.put("testArray", testArray);
    testMap.put("testString", "testString");
    assertThat(filter.filter(testMap, interpreter))
      .isEqualTo("{\"testArray\":[4,1,2],\"testString\":\"testString\"}");
  }

  @Test
  public void itLimitsLength() {
    List<List<?>> original = new ArrayList<>();
    List<List<?>> temp = original;
    for (int i = 0; i < 100; i++) {
      List<List<?>> nested = new ArrayList<>();
      temp.add(nested);
      temp = nested;
    }
    interpreter =
      new Jinjava(BaseJinjavaTest.newConfigBuilder().withMaxOutputSize(500).build())
        .newInterpreter();
    assertThat(filter.filter(original, interpreter)).asString().contains("[[]]]]");
    for (int i = 0; i < 400; i++) {
      List<List<?>> nested = new ArrayList<>();
      temp.add(nested);
      temp = nested;
    }
    assertThatThrownBy(() -> filter.filter(original, interpreter))
      .isInstanceOf(OutputTooBigException.class);
  }

  private static final int TINY_OUTPUT_LIMIT = 20;
  private static final int LONG_STRING_LENGTH = 10_000;
  private static final long REPORTED_MAX_SIZE = 10L;
  private static final long REPORTED_SIZE = 20L;

  private static JinjavaInterpreter interpreterWith(JinjavaConfig.Builder builder) {
    return new Jinjava(builder.build()).newInterpreter();
  }

  @Test
  public void itUsesTheObjectMapperOfTheConfig() {
    // a plain Jackson 3 mapper, which also sorts properties
    JsonMapper mapper = JsonMapper
      .builder()
      .propertyNamingStrategy(PropertyNamingStrategies.UPPER_SNAKE_CASE)
      .build();
    JinjavaInterpreter custom = interpreterWith(
      BaseJinjavaTest.newConfigBuilder().withObjectMapper(mapper)
    );
    assertThat(filter.filter(new CamelBean(), custom))
      .isEqualTo(
        "{\"FIRST_NAME\":\"Jane\",\"HTTP_STATUS_CODE\":200,\"LAST_NAME\":\"Doe\"}"
      );
  }

  @Test
  public void itWritesAcronymGettersLikeJackson2() {
    assertThat(filter.filter(new AcronymBean(), interpreter))
      .isEqualTo("{\"url\":\"http://x/y\",\"xvalue\":7,\"name\":\"n\"}");
  }

  @Test
  public void itWritesNullValueAsNullAndLocaleWithUnderscore() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("n", NullValue.instance());
    map.put("l", Locale.US);
    assertThat(filter.filter(map, interpreter)).isEqualTo("{\"n\":null,\"l\":\"en_US\"}");
  }

  @Test
  public void itFailsForJavaTimeTypesLikeJackson2() {
    assertThatThrownBy(() -> filter.filter(Instant.EPOCH, interpreter))
      .isInstanceOf(InvalidInputException.class)
      .hasMessageContaining(InvalidReason.JSON_WRITE.getErrorMessage());
  }

  @Test
  public void itRethrowsDeferredValueExceptionFromGetter() {
    assertThatThrownBy(() -> filter.filter(new DeferredGetterBean(), interpreter))
      .isInstanceOf(DeferredValueException.class);
    assertThatThrownBy(() ->
        filter.filter(new Holder(new DeferredGetterBean()), interpreter)
      )
      .isInstanceOf(DeferredValueException.class);
  }

  @Test
  public void itRethrowsDeferredValueExceptionFromRootSerializer() {
    DeferredValueException deferred = new DeferredValueException("root");
    assertThatThrownBy(() -> filter.filter(new ThrowingList(deferred), interpreter))
      .isSameAs(deferred);
  }

  @Test
  public void itDoesNotRethrowDeferredValueExceptionBehindOtherExceptions() {
    assertThatThrownBy(() -> filter.filter(new WrappedDeferredGetterBean(), interpreter))
      .isInstanceOf(InvalidInputException.class)
      .hasMessageContaining(InvalidReason.JSON_WRITE.getErrorMessage());
    assertThatThrownBy(() ->
        filter.filter(
          new ThrowingList(new RuntimeException(new DeferredValueException("root"))),
          interpreter
        )
      )
      .isInstanceOf(InvalidInputException.class);
  }

  @Test
  public void itRethrowsOutputTooBigExceptionFromGetterAndRootSerializer() {
    assertThatThrownBy(() -> filter.filter(new TooBigGetterBean(), interpreter))
      .isInstanceOf(OutputTooBigException.class)
      .hasMessage(
        new OutputTooBigException(REPORTED_MAX_SIZE, REPORTED_SIZE).getMessage()
      );
    assertThatThrownBy(() ->
        filter.filter(
          new ThrowingList(new OutputTooBigException(REPORTED_MAX_SIZE, REPORTED_SIZE)),
          interpreter
        )
      )
      .isInstanceOf(OutputTooBigException.class);
  }

  @Test
  public void itDoesNotMapWrappedOutputTooBigExceptionToTooBig() {
    assertThatThrownBy(() -> filter.filter(new WrappedTooBigGetterBean(), interpreter))
      .isInstanceOf(InvalidInputException.class);
  }

  @Test
  public void itReportsSizesWhenALongStringHitsTheLimit() {
    JinjavaInterpreter limited = interpreterWith(
      BaseJinjavaTest.newConfigBuilder().withMaxOutputSize(TINY_OUTPUT_LIMIT)
    );
    assertThatThrownBy(() -> filter.filter("x".repeat(LONG_STRING_LENGTH), limited))
      .isInstanceOf(OutputTooBigException.class)
      .hasMessageContaining("over limit of " + TINY_OUTPUT_LIMIT + " bytes");
  }

  @Test
  public void itFailsWithInvalidInputWhenJsonCannotBeWritten() {
    assertThatThrownBy(() -> filter.filter(new EmptyBean(), interpreter))
      .isInstanceOf(InvalidInputException.class)
      .hasMessageContaining(InvalidReason.JSON_WRITE.getErrorMessage());
    assertThatThrownBy(() -> filter.filter(new ThrowingBean(), interpreter))
      .isInstanceOf(InvalidInputException.class);
  }

  /** A serializer of a custom mapper that fails on its own, at the root of the value. */
  private static final class FailingSerializer extends ValueSerializer<Marker> {

    private final RuntimeException toThrow;

    private FailingSerializer(RuntimeException toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public void serialize(Marker value, JsonGenerator gen, SerializationContext ctxt) {
      throw toThrow;
    }
  }

  private static final class Marker {}

  private JinjavaInterpreter interpreterFailingWith(RuntimeException toThrow) {
    JsonMapper mapper = JsonMapper
      .builder()
      .addModule(
        new SimpleModule().addSerializer(Marker.class, new FailingSerializer(toThrow))
      )
      .build();
    return interpreterWith(BaseJinjavaTest.newConfigBuilder().withObjectMapper(mapper));
  }

  @Test
  public void itRethrowsDeferredValueExceptionFromRootSerializerOfConfiguredMapper() {
    DeferredValueException deferred = new DeferredValueException("custom");
    assertThatThrownBy(() -> filter.filter(new Marker(), interpreterFailingWith(deferred))
      )
      .isSameAs(deferred);
  }

  @Test
  public void itRethrowsOutputTooBigExceptionFromRootSerializerOfConfiguredMapper() {
    OutputTooBigException tooBig = new OutputTooBigException(
      REPORTED_MAX_SIZE,
      REPORTED_SIZE
    );
    assertThatThrownBy(() -> filter.filter(new Marker(), interpreterFailingWith(tooBig)))
      .isSameAs(tooBig);
  }

  @Test
  public void itFailsWithInvalidInputForOtherExceptionsOfRootSerializer() {
    assertThatThrownBy(() ->
        filter.filter(
          new Marker(),
          interpreterFailingWith(new IllegalStateException("x"))
        )
      )
      .isInstanceOf(InvalidInputException.class)
      .hasMessageContaining(InvalidReason.JSON_WRITE.getErrorMessage());
  }
}
