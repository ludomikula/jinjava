package com.hubspot.jinjava.objects.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hubspot.jinjava.interpret.NullValue;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.AcronymBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.Color;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.EmptyBean;
import com.hubspot.jinjava.testobjects.JsonOutcomeTestObjects.NullValueBean;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

public class Jackson2ParitySettingsTest {

  private static final String ACRONYM_BEAN_JSON =
    "{\"url\":\"http://x/y\",\"xvalue\":7,\"name\":\"n\"}";
  private static final int JACKSON_3_DEFAULT_NESTING_DEPTH = 500;
  private static final int JACKSON_3_DEFAULT_STRING_LENGTH = 100_000_000;

  private static String write(JsonMapper mapper, Object value) {
    return mapper.writeValueAsString(value);
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

  private static String nestedArrays(int depth) {
    return "[".repeat(depth) + "]".repeat(depth);
  }

  @Test
  public void itNamesAccessorsLikeJackson2() {
    JsonMapper mapper = Jackson2ParitySettings.jsonMapperBuilder().build();
    assertThat(write(mapper, new AcronymBean())).isEqualTo(ACRONYM_BEAN_JSON);
  }

  @Test
  public void itNamesAccessorsLikeJackson2OnAnyMapperBuilder() {
    JsonMapper mapper = Jackson2ParitySettings.applyTo(JsonMapper.builder()).build();
    assertThat(write(mapper, new AcronymBean()).contains("\"url\"")).isTrue();
    assertThat(write(mapper, new AcronymBean()).contains("\"xvalue\"")).isTrue();
  }

  @Test
  public void itDoesNotApplyJackson2NamingWithoutTheSettings() {
    JsonMapper mapper = JsonMapper.builder().build();
    assertThat(write(mapper, new AcronymBean()))
      .contains("\"URL\"")
      .contains("\"XValue\"");
  }

  @Test
  public void itRejectsJavaTimeTypesLikeJackson2WithoutJsr310() {
    JsonMapper mapper = Jackson2ParitySettings.jsonMapperBuilder().build();
    List<Object> javaTimeValues = new ArrayList<>();
    javaTimeValues.add(Instant.EPOCH);
    javaTimeValues.add(ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 6, ZoneOffset.UTC));
    javaTimeValues.add(LocalDate.of(2020, 1, 2));
    javaTimeValues.add(Duration.ofSeconds(90));
    for (Object value : javaTimeValues) {
      assertThatThrownBy(() -> write(mapper, value))
        .describedAs(value.getClass().getName())
        .isInstanceOf(DatabindException.class);
      assertThatThrownBy(() -> write(mapper, Collections.singletonMap("k", value)))
        .describedAs("map with " + value.getClass().getName())
        .isInstanceOf(DatabindException.class);
    }
  }

  @Test
  public void itWritesJavaTimeTypesWithoutTheSettings() {
    assertThat(write(JsonMapper.builder().build(), LocalDate.of(2020, 1, 2)))
      .isNotEmpty();
  }

  @Test
  public void itWritesLocaleWithUnderscore() {
    JsonMapper mapper = Jackson2ParitySettings.jsonMapperBuilder().build();
    assertThat(write(mapper, Locale.US)).isEqualTo("\"en_US\"");
    assertThat(write(mapper, Collections.singletonMap("l", Locale.US)))
      .isEqualTo("{\"l\":\"en_US\"}");
  }

  @Test
  public void itWritesNullValueAsNullThroughItsJsonValueMethod() {
    JsonMapper mapper = Jackson2ParitySettings.jsonMapperBuilder().build();
    assertThat(write(mapper, NullValue.instance())).isEqualTo("null");
    assertThat(write(mapper, new NullValueBean())).isEqualTo("{\"n\":null}");
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("n", NullValue.instance());
    assertThat(write(mapper, map)).isEqualTo("{\"n\":null}");
  }

  @Test
  public void itKeepsJackson2Defaults() {
    JsonMapper mapper = Jackson2ParitySettings.jsonMapperBuilder().build();
    // properties in declaration order, not sorted
    assertThat(write(mapper, new AcronymBean())).startsWith("{\"url\"");
    // enums by name, not by toString
    assertThat(write(mapper, Color.RED)).isEqualTo("\"RED\"");
    // dates as epoch milliseconds
    assertThat(write(mapper, new Date(0))).isEqualTo("0");
    // optionals unwrapped
    assertThat(write(mapper, Optional.of("x"))).isEqualTo("\"x\"");
    // beans without properties fail
    assertThatThrownBy(() -> write(mapper, new EmptyBean()))
      .isInstanceOf(DatabindException.class);
    // trailing tokens after the first value are ignored
    assertThat(mapper.readValue("[1] [2]", Object.class)).isEqualTo(List.of(1));
  }

  @Test
  public void itRaisesStreamConstraintsToJackson2Limits() {
    JsonFactory factory = Jackson2ParitySettings.jsonFactoryBuilder().build();
    StreamReadConstraints read = factory.streamReadConstraints();
    StreamWriteConstraints write = factory.streamWriteConstraints();
    assertThat(read.getMaxNestingDepth())
      .isEqualTo(Jackson2ParitySettings.MAX_NESTING_DEPTH);
    assertThat(read.getMaxStringLength())
      .isEqualTo(Jackson2ParitySettings.MAX_STRING_LENGTH);
    assertThat(write.getMaxNestingDepth())
      .isEqualTo(Jackson2ParitySettings.MAX_NESTING_DEPTH);
    assertThat(Jackson2ParitySettings.MAX_NESTING_DEPTH)
      .isGreaterThan(JACKSON_3_DEFAULT_NESTING_DEPTH);
    assertThat(Jackson2ParitySettings.MAX_STRING_LENGTH)
      .isLessThan(JACKSON_3_DEFAULT_STRING_LENGTH);
  }

  @Test
  public void itReadsAndWritesUpToTheJackson2NestingLimit() {
    JsonMapper mapper = Jackson2ParitySettings.jsonMapperBuilder().build();
    int arrayDepth = Jackson2ParitySettings.MAX_NESTING_DEPTH;
    assertThat(mapper.readValue(nestedArrays(arrayDepth), Object.class)).isNotNull();
    assertThatThrownBy(() -> mapper.readValue(nestedArrays(arrayDepth + 1), Object.class))
      .isInstanceOf(JacksonException.class);
    // the root list counts as one level
    assertThat(write(mapper, nestedLists(arrayDepth - 1)).length())
      .isEqualTo(2 * arrayDepth);
    assertThatThrownBy(() -> write(mapper, nestedLists(arrayDepth)))
      .isInstanceOf(JacksonException.class);
  }

  @Test
  public void itAppliesStreamConstraintsToAnyFactoryBuilder() {
    JsonFactory factory = Jackson2ParitySettings
      .applyStreamConstraints(JsonFactory.builder())
      .build();
    assertThat(factory.streamReadConstraints().getMaxNestingDepth())
      .isEqualTo(Jackson2ParitySettings.MAX_NESTING_DEPTH);
    assertThat(factory.streamReadConstraints().getMaxStringLength())
      .isEqualTo(Jackson2ParitySettings.MAX_STRING_LENGTH);
    assertThat(factory.streamWriteConstraints().getMaxNestingDepth())
      .isEqualTo(Jackson2ParitySettings.MAX_NESTING_DEPTH);
  }
}
