package com.hubspot.jinjava.objects.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.databind.ObjectWriter;

public class PyishPrettyPrinterTest {

  private static final ObjectWriter WRITER = PyishObjectMapper.pyishWriter(
    PyishObjectMapper.pyishMapperBuilder().build()
  );

  @Test
  public void itWritesOneSpaceAfterEveryEntrySeparator() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("b", 1);
    map.put("a", Arrays.asList(1, 2));
    assertThat(WRITER.writeValueAsString(map)).isEqualTo("{'b': 1, 'a': [1, 2]} ");
  }

  @Test
  public void itClosesObjectsWithATrailingSpaceAndArraysWithout() {
    assertThat(WRITER.writeValueAsString(ImmutableMap.of())).isEqualTo("{} ");
    assertThat(WRITER.writeValueAsString(Arrays.asList())).isEqualTo("[]");
    assertThat(WRITER.writeValueAsString(Arrays.asList(ImmutableMap.of("k", "v"))))
      .isEqualTo("[{'k': 'v'} ]");
  }

  @Test
  public void itSharesOneInstance() {
    assertThat(PyishPrettyPrinter.INSTANCE.createInstance())
      .isSameAs(PyishPrettyPrinter.INSTANCE);
  }

  @Test
  public void itKeepsWhitespaceUnescapedAndEscapesTheApostropheWithACustomSequence() {
    int[] escapes = PyishCharacterEscapes.INSTANCE.getEscapeCodesForAscii();
    assertThat(escapes['\'']).isEqualTo(CharacterEscapes.ESCAPE_CUSTOM);
    assertThat(escapes['\n']).isEqualTo(CharacterEscapes.ESCAPE_NONE);
    assertThat(escapes['\t']).isEqualTo(CharacterEscapes.ESCAPE_NONE);
    assertThat(escapes['\r']).isEqualTo(CharacterEscapes.ESCAPE_NONE);
    assertThat(escapes['\f']).isEqualTo(CharacterEscapes.ESCAPE_NONE);
    assertThat(escapes['\\']).isEqualTo((int) '\\');
    assertThat(PyishCharacterEscapes.INSTANCE.getEscapeSequence('\'').getValue())
      .isEqualTo("\\'");
    assertThat(PyishCharacterEscapes.INSTANCE.getEscapeSequence('a')).isNull();
  }

  @Test
  public void itDoesNotExposeItsEscapeTableToModification() {
    PyishCharacterEscapes.INSTANCE.getEscapeCodesForAscii()['\''] =
      CharacterEscapes.ESCAPE_NONE;
    assertThat(PyishCharacterEscapes.INSTANCE.getEscapeCodesForAscii()['\''])
      .isEqualTo(CharacterEscapes.ESCAPE_CUSTOM);
  }
}
