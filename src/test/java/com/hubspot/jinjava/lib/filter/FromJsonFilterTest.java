package com.hubspot.jinjava.lib.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.BaseJinjavaTest;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.InvalidInputException;
import com.hubspot.jinjava.interpret.InvalidReason;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class FromJsonFilterTest extends BaseInterpretingTest {

  private FromJsonFilter filter;

  @Before
  public void setup() {
    filter = new FromJsonFilter();
  }

  @Test(expected = InvalidInputException.class)
  public void itFailsWhenStringIsNotJson() {
    String json = "blah";

    filter.filter(json, interpreter);
  }

  @Test(expected = InvalidInputException.class)
  public void itFailsWhenParameterIsNotString() {
    Integer json = 456;

    filter.filter(json, interpreter);
  }

  @Test(expected = InvalidInputException.class)
  public void itFailsWhenJsonIsInvalid() {
    String json = "{[ }]";

    filter.filter(json, interpreter);
  }

  @Test
  public void itRendersTrivialJsonObject() {
    String trivialJsonObject = "{\"a\":100,\"b\":200}";

    Map<String, Object> vars = ImmutableMap.of("test", trivialJsonObject);
    String template = "{% set obj = test | fromjson %}{{ obj.a }} {{ obj.b }}";
    String renderedJinjava = jinjava.render(template, vars);

    assertThat(renderedJinjava).isEqualTo("100 200");
  }

  @Test
  public void itRendersTrivialJsonArray() {
    String trivialJsonArray = "[\"one\",\"two\",\"three\"]";

    Map<String, Object> vars = ImmutableMap.of("test", trivialJsonArray);
    String template =
      "{% set obj = test | fromjson %}{{ obj[0] }} {{ obj[1] }} {{ obj[2] }}";
    String renderedJinjava = jinjava.render(template, vars);

    assertThat(renderedJinjava).isEqualTo("one two three");
  }

  @Test
  public void itRendersNestedObjectJson() {
    String nestedObject = "{\"first\": 1,\"nested\":{\"second\":\"string\",\"third\":4}}";

    Map<String, Object> vars = ImmutableMap.of("test", nestedObject);
    String template =
      "{% set obj = test | fromjson %}{{ obj.first }} {{ obj.nested.second }} {{ obj.nested.third }}";
    String renderedJinjava = jinjava.render(template, vars);

    assertThat(renderedJinjava).isEqualTo("1 string 4");
  }

  @Test
  public void itRendersNestedJsonWithArray() {
    String nestedObjectWithArray = "{\"a\":{\"b\":{\"c\":[1,2,3]}}}";

    Map<String, Object> vars = ImmutableMap.of("test", nestedObjectWithArray);
    String template = "{% set obj = test | fromjson %}{{ obj.a.b.c }}";
    String renderedJinjava = jinjava.render(template, vars);

    assertThat(renderedJinjava).isEqualTo("[1, 2, 3]");
  }

  @Test
  public void itRendersArrayOfObjects() {
    String arrayOfObjects = "[{\"a\":1},{\"a\":2},{\"a\": 3}]";

    Map<String, Object> vars = ImmutableMap.of("test", arrayOfObjects);
    String template =
      "{% set obj = test | fromjson %}{{ obj[0].a }} {{ obj[1].a }} {{ obj[2].a }}";
    String renderedJinjava = jinjava.render(template, vars);

    assertThat(renderedJinjava).isEqualTo("1 2 3");
  }

  private static final int NESTING_LIMIT = 1000;

  private static String nestedArrays(int depth) {
    return "[".repeat(depth) + "]".repeat(depth);
  }

  @Test
  public void itUsesTheObjectMapperOfTheConfig() {
    JsonMapper mapper = JsonMapper
      .builder()
      .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
      .build();
    JinjavaInterpreter custom = new Jinjava(
      BaseJinjavaTest.newConfigBuilder().withObjectMapper(mapper).build()
    )
      .newInterpreter();
    assertThat(filter.filter("1.5", custom)).isEqualTo(new BigDecimal("1.5"));
    assertThat(filter.filter("1.5", interpreter)).isEqualTo(1.5d);
  }

  @Test
  public void itIgnoresTokensAfterTheFirstValue() {
    assertThat(filter.filter("{\"a\":1} trailing", interpreter))
      .isEqualTo(ImmutableMap.of("a", 1));
    assertThat(filter.filter("[1] [2]", interpreter)).isEqualTo(ImmutableList.of(1));
  }

  @Test
  public void itReadsBigNumbers() {
    assertThat(filter.filter("12345678901234567890", interpreter))
      .isEqualTo(new BigInteger("12345678901234567890"));
    assertThat(filter.filter("1e400", interpreter)).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test
  public void itReadsUpToTheJackson2NestingLimit() {
    assertThat(filter.filter(nestedArrays(NESTING_LIMIT), interpreter)).isNotNull();
    assertThatThrownBy(() -> filter.filter(nestedArrays(NESTING_LIMIT + 1), interpreter))
      .isInstanceOf(InvalidInputException.class)
      .hasMessageContaining(InvalidReason.JSON_READ.getErrorMessage());
  }

  @Test
  public void itFailsForNonStandardJson() {
    for (String json : new String[] {
      "{'a':1}",
      "{a:1}",
      "[1,]",
      "{\"a\":NaN}",
      "// c\n1",
      "",
    }) {
      assertThatThrownBy(() -> filter.filter(json, interpreter))
        .describedAs(json)
        .isInstanceOf(InvalidInputException.class);
    }
  }

  private static final String DESERIALIZER_FAILURE = "deserializer failure";

  private static class FailingDeserializer extends ValueDeserializer<Object> {

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) {
      throw new IllegalStateException(DESERIALIZER_FAILURE);
    }
  }

  @Test
  public void itFailsWithInvalidInputWhenADeserializerThrowsAnUncheckedException() {
    JsonMapper mapper = JsonMapper
      .builder()
      .addModule(
        new SimpleModule().addDeserializer(Object.class, new FailingDeserializer())
      )
      .build();
    JinjavaInterpreter custom = new Jinjava(
      BaseJinjavaTest.newConfigBuilder().withObjectMapper(mapper).build()
    )
      .newInterpreter();
    assertThatThrownBy(() -> filter.filter("{\"a\":1}", custom))
      .isInstanceOf(InvalidInputException.class);
  }
}
