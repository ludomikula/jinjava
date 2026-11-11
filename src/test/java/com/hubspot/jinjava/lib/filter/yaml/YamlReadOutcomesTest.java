package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class YamlReadOutcomesTest {

  @Test
  public void itShowsScalarsWithTheirJavaType() {
    assertThat(YamlReadOutcomes.show(1)).isEqualTo("1<Integer>");
    assertThat(YamlReadOutcomes.show(1L)).isEqualTo("1<Long>");
    assertThat(YamlReadOutcomes.show(BigInteger.ONE)).isEqualTo("1<BigInteger>");
    assertThat(YamlReadOutcomes.show(1.5)).isEqualTo("1.5<Double>");
    assertThat(YamlReadOutcomes.show("1")).isEqualTo("1<String>");
    assertThat(YamlReadOutcomes.show(true)).isEqualTo("true<Boolean>");
    assertThat(YamlReadOutcomes.show(null)).isEqualTo("null");
  }

  @Test
  public void itShowsMapsAndListsInOrder() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("b", Arrays.asList(1, null));
    map.put("a", Map.of());
    assertThat(YamlReadOutcomes.show(map))
      .isEqualTo("Map{b<String>=List[1<Integer>, null], a<String>=Map{}}");
    assertThat(YamlReadOutcomes.show(List.of())).isEqualTo("List[]");
  }

  @Test
  public void itShowsLongStringsAndBytesByLengthAndHash() {
    String longString = "x".repeat(101);
    assertThat(YamlReadOutcomes.show(longString))
      .isEqualTo("String[len=101,hash=" + longString.hashCode() + "]<String>");
    assertThat(YamlReadOutcomes.show("x".repeat(100)))
      .isEqualTo("x".repeat(100) + "<String>");
    assertThat(YamlReadOutcomes.show(new byte[] { 1, 2 }))
      .isEqualTo("byte[2,hash=" + Arrays.hashCode(new byte[] { 1, 2 }) + "]");
  }

  @Test
  public void itKeepsShortOutcomesAndHashesLongOnes() {
    String atLimit = "y".repeat(YamlReadOutcomes.MAX_INLINE_CHARS);
    assertThat(YamlReadOutcomes.summarize(atLimit)).isEqualTo(atLimit);
    String overLimit = atLimit + "y";
    String summary = YamlReadOutcomes.summarize(overLimit);
    assertThat(summary)
      .startsWith("[" + overLimit.length() + " chars, sha256 ")
      .hasSize("[2001 chars, sha256 ]".length() + 64);
    assertThat(YamlReadOutcomes.summarize(atLimit + "z")).isNotEqualTo(summary);
    assertThat(YamlReadOutcomes.summarize(overLimit)).isEqualTo(summary);
  }

  @Test
  public void itReadsTheTextOfACaseAndShowsTheResult() {
    assertThat(
      YamlReadOutcomes
        .reader(text -> Map.of("t", text))
        .apply(YamlReadCorpus.text("l", "abc").value())
    )
      .isEqualTo("Map{t<String>=abc<String>}");
  }

  @Test
  public void itRecordsOneLinePerCaseWithTheClassOfAFailure() {
    List<Case> cases = List.of(
      YamlReadCorpus.text("ok", "1"),
      YamlReadCorpus.text("bad\ttab", "x")
    );
    List<String> lines = YamlReadOutcomes.fullLines(
      cases,
      YamlReadOutcomes.reader(text -> {
        if ("x".equals(text)) {
          throw new IllegalStateException();
        }
        return Integer.valueOf(text);
      })
    );
    assertThat(lines)
      .containsExactly("ok\tOK 1<Integer>", "bad\\ttab\tERR IllegalStateException");
  }

  @Test
  public void itHasItsOwnUpdateFlag() {
    assertThat(YamlReadOutcomes.UPDATE_PROPERTY).isEqualTo("jinjava.yaml.read.update");
    assertThat(YamlReadOutcomes.isUpdating())
      .isEqualTo(Boolean.getBoolean("jinjava.yaml.read.update"));
  }
}
