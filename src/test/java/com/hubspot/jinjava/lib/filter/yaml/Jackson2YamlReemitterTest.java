package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class Jackson2YamlReemitterTest {

  private static final String NEL = "\u0085";
  private static final String LS = "\u2028";
  private static final String PS = "\u2029";
  private static final int OVER_ENGINE_CODE_POINT_LIMIT = 3_200_000;

  private static final YAMLMapper MAPPER = Jackson2ParityYamlMapper.create();

  private static String jackson3Text(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  private static Map<String, Object> map(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  @Test
  public void itWritesLineSeparatorsAsEscapesInDoubleQuotedValues() {
    assertThat(Jackson2YamlReemitter.reemit(jackson3Text(map("k", "a" + LS + "b"))))
      .isEqualTo("k: \"a\\Lb\"\n");
    assertThat(Jackson2YamlReemitter.reemit(jackson3Text(map("k", "a" + PS + "b"))))
      .isEqualTo("k: \"a\\Pb\"\n");
    assertThat(Jackson2YamlReemitter.reemit(jackson3Text(map("k", NEL + "x"))))
      .isEqualTo("k: \"\\Nx\"\n");
  }

  @Test
  public void itWritesAKeyWithALineBreakInTheComplexForm() {
    assertThat(Jackson2YamlReemitter.reemit(jackson3Text(map(NEL, 1))))
      .isEqualTo("? '" + NEL + "  '\n: 1\n");
  }

  @Test
  public void itKeepsStructureAndStylesOfTextWithoutLineBreakCharacters() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("text", "plain words");
    value.put("number", 12);
    value.put("flag", true);
    value.put("yes", "yes");
    value.put("list", List.of(1, "two", List.of(3)));
    value.put("nested", map("inner", map("deepest", "x: y")));
    value.put("empty", List.of());
    value.put("bytes", new byte[] { 1, 2, 3 });
    String text = jackson3Text(value);
    assertThat(Jackson2YamlReemitter.reemit(text)).isEqualTo(text);
  }

  @Test
  public void itRestoresMaskedCharactersInNestedValues() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("a", List.of(LS + "x", PS, map("\ufeff", NEL)));
    String reemitted = Jackson2YamlReemitter.reemit(jackson3Text(value));
    Object parsed = new Yaml().load(reemitted);
    assertThat(parsed).isEqualTo(value);
  }

  @Test
  public void itWritesTextLongerThanTheEngineDefaultLimitOfCodePoints() {
    String longValue = "x".repeat(OVER_ENGINE_CODE_POINT_LIMIT) + NEL;
    String text = jackson3Text(map("k", longValue));
    assertThat(text.length()).isGreaterThan(OVER_ENGINE_CODE_POINT_LIMIT);

    String reemitted = Jackson2YamlReemitter.reemit(text);

    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setCodePointLimit(Integer.MAX_VALUE);
    Object parsed = new Yaml(loaderOptions).load(reemitted);
    assertThat(parsed).isEqualTo(map("k", longValue));
  }

  @Test
  public void itUsesTheMaskItIsGiven() {
    String text = jackson3Text(map("k", "a" + LS + "b"));
    assertThat(Jackson2YamlReemitter.reemit(text, LegacyLineBreakMask::new))
      .isEqualTo(Jackson2YamlReemitter.reemit(text));
  }

  @Test
  public void itReportsAParserFailure() {
    assertThatThrownBy(() -> Jackson2YamlReemitter.reemit("k: [unclosed"))
      .isInstanceOf(YamlReemitException.class)
      .hasMessageContaining("Could not re-emit")
      .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  public void itReportsAnAliasAsUnsupported() {
    assertThatThrownBy(() -> Jackson2YamlReemitter.reemit("a: &x 1\nb: *x\n"))
      .isInstanceOf(YamlReemitException.class)
      .hasRootCauseInstanceOf(IllegalStateException.class)
      .hasRootCauseMessage("Unsupported YAML event " + "=ALI *x");
  }

  @Test
  public void itReportsAFailingMask() {
    assertThatThrownBy(() ->
        Jackson2YamlReemitter.reemit(
          "k: v\n",
          yaml -> {
            throw new IllegalStateException("mask failed");
          }
        )
      )
      .isInstanceOf(YamlReemitException.class)
      .hasRootCauseMessage("mask failed");
  }

  @Test
  public void itConfiguresTheEmitterLikeJackson2() {
    DumperOptions options = Jackson2YamlReemitter.jackson2Options();
    assertThat(options.isCanonical()).isFalse();
    assertThat(options.getDefaultFlowStyle()).isEqualTo(DumperOptions.FlowStyle.BLOCK);
    assertThat(options.getSplitLines()).isTrue();
  }
}
