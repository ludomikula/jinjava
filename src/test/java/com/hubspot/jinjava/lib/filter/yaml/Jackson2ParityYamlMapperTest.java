package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.Test;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class Jackson2ParityYamlMapperTest {

  private static final YAMLMapper MAPPER = Jackson2ParityYamlMapper.create();
  private static final int ACCEPTED_NESTING = 700;
  private static final int REJECTED_NESTING = 1100;

  private static Map<String, Object> map(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  @Test
  public void itWritesNoDocumentStartMarker() {
    assertThat(MAPPER.writeValueAsString(map("k", 1))).isEqualTo("k: 1\n");
  }

  @Test
  public void itRejectsEveryOptionalTypeLikeJackson2WithoutItsJdk8Module() {
    for (Object optional : new Object[] {
      Optional.of("x"),
      Optional.empty(),
      OptionalInt.of(1),
      OptionalLong.of(1L),
      OptionalDouble.of(1.5),
    }) {
      assertThatThrownBy(() -> MAPPER.writeValueAsString(map("k", optional)))
        .as(optional.getClass().getName())
        .isInstanceOf(InvalidDefinitionException.class)
        .hasMessageContaining("Optional types are not supported");
    }
  }

  @Test
  public void itRejectsJavaTimeTypes() {
    for (Object time : new Object[] {
      ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
      Duration.ofSeconds(5),
      Instant.EPOCH,
      LocalDate.of(2020, 1, 2),
    }) {
      assertThatThrownBy(() -> MAPPER.writeValueAsString(map("k", time)))
        .as(time.getClass().getName())
        .isInstanceOf(InvalidDefinitionException.class);
    }
  }

  @Test
  public void itWritesALocaleWithToString() {
    assertThat(MAPPER.writeValueAsString(map("k", Locale.US)))
      .isEqualTo("k: \"en_US\"\n");
  }

  @Test
  public void itNamesGettersLikeJackson2() {
    assertThat(MAPPER.writeValueAsString(new YamlWriteCorpus.UrlBean()))
      .isEqualTo("url: \"u\"\n");
    assertThat(MAPPER.writeValueAsString(new YamlWriteCorpus.XValueBean()))
      .isEqualTo("xvalue: 1\n");
    assertThat(MAPPER.writeValueAsString(new YamlWriteCorpus.AbBean()))
      .isEqualTo("aB: 1\n");
  }

  @Test
  public void itAppliesJackson2NestingLimits() {
    Object accepted = "leaf";
    for (int i = 0; i < ACCEPTED_NESTING; i++) {
      accepted = java.util.List.of(accepted);
    }
    assertThat(MAPPER.writeValueAsString(accepted)).isNotEmpty();

    Object rejected = "leaf";
    for (int i = 0; i < REJECTED_NESTING; i++) {
      rejected = java.util.List.of(rejected);
    }
    Object tooDeep = rejected;
    assertThatThrownBy(() -> MAPPER.writeValueAsString(tooDeep))
      .isInstanceOf(StreamConstraintsException.class);
  }

  @Test
  public void itWritesOrdinaryValuesUnchanged() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("text", "v");
    value.put("number", 5);
    value.put("list", java.util.List.of(1, 2));
    assertThat(MAPPER.writeValueAsString(value))
      .isEqualTo("text: \"v\"\nnumber: 5\nlist:\n- 1\n- 2\n");
  }
}
