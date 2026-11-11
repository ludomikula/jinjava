package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

public class YamlReadCorpusTest {

  private static final String HIGH_SURROGATE_MARKER = "hi@";
  private static final int FUZZ_SAMPLE = 2000;

  private static Case byLabel(List<Case> cases, String label) {
    return cases
      .stream()
      .filter(c -> c.label().equals(label))
      .findFirst()
      .orElseThrow(() -> new AssertionError("no case labelled " + label));
  }

  @Test
  public void itSuppliesTheTextOfACase() {
    assertThat(YamlReadCorpus.textOf(YamlReadCorpus.text("l", "a: 1"))).isEqualTo("a: 1");
    assertThat(YamlReadCorpus.textOf(YamlReadCorpus.lazy("l", () -> "b"))).isEqualTo("b");
  }

  @Test
  public void itHasUniqueLabelsInEverySet() {
    for (List<Case> set : List.of(
      YamlReadCorpus.corpus(),
      YamlReadCorpus.surrogateChunkInputs(),
      YamlReadCorpus.surrogateSweep(),
      YamlReadCorpus.lineBreakInputs(),
      YamlReadCorpus.lineBreakSweep(),
      YamlReadCorpus.targeted()
    )) {
      Set<String> labels = set.stream().map(Case::label).collect(Collectors.toSet());
      assertThat(labels).hasSameSizeAs(set);
    }
  }

  @Test
  public void itPlacesTheHighSurrogateAtTheNamedIndexOfEveryChunkInput() {
    for (Case c : YamlReadCorpus.surrogateChunkInputs()) {
      String text = YamlReadCorpus.textOf(c);
      int position = Integer.parseInt(
        c
          .label()
          .substring(
            c.label().indexOf(HIGH_SURROGATE_MARKER) + HIGH_SURROGATE_MARKER.length()
          )
      );
      int length = Integer.parseInt(
        c
          .label()
          .substring(
            c.label().indexOf("L=") + 2,
            c.label().indexOf(' ', c.label().indexOf("L="))
          )
      );
      assertThat(text).as(c.label()).hasSize(length);
      assertThat(Character.isHighSurrogate(text.charAt(position))).as(c.label()).isTrue();
      assertThat(Character.isLowSurrogate(text.charAt(position + 1)))
        .as(c.label())
        .isTrue();
    }
  }

  @Test
  public void itSweepsTheHighSurrogateAcrossEachChunkEnd() {
    List<Case> sweep = YamlReadCorpus.surrogateSweep();
    for (Case c : sweep) {
      String text = YamlReadCorpus.textOf(c);
      assertThat(text).hasSize(4110);
      int position = Integer.parseInt(
        c
          .label()
          .substring(
            c.label().indexOf(HIGH_SURROGATE_MARKER) + HIGH_SURROGATE_MARKER.length()
          )
      );
      assertThat(Character.isHighSurrogate(text.charAt(position))).isTrue();
    }
    assertThat(sweep.get(0).label()).endsWith("hi@1015");
    assertThat(sweep.get(sweep.size() - 1).label()).endsWith("hi@4105");
  }

  @Test
  public void itPutsTheLineBreakCharacterInEveryPositionOfTheSweep() {
    for (Case c : YamlReadCorpus.lineBreakSweep()) {
      String text = YamlReadCorpus.textOf(c);
      assertThat(text).as(c.label()).doesNotContain("@");
      assertThat(
        text
          .chars()
          .anyMatch(ch -> ch == 0x85 || ch == 0x2028 || ch == 0x2029 || ch == 0xFEFF)
      )
        .as(c.label())
        .isTrue();
    }
  }

  @Test
  public void itBuildsTheInputsAtTheCodePointLimitAndTheStringLimit() {
    List<Case> targeted = YamlReadCorpus.targeted();
    assertThat(
      YamlReadCorpus.textOf(
        byLabel(
          targeted,
          "doc plain value (words) total=" + YamlReadCorpus.CODE_POINT_LIMIT
        )
      )
    )
      .hasSize(YamlReadCorpus.CODE_POINT_LIMIT);
    assertThat(
      YamlReadCorpus.textOf(
        byLabel(
          targeted,
          "doc dq value (words) total=" + (YamlReadCorpus.CODE_POINT_LIMIT + 1)
        )
      )
    )
      .hasSize(YamlReadCorpus.CODE_POINT_LIMIT + 1);
    assertThat(YamlReadCorpus.textOf(byLabel(targeted, "doc literal lines 3MiB-1")))
      .hasSize(YamlReadCorpus.CODE_POINT_LIMIT - 1);
    assertThat(
      YamlReadCorpus.textOf(
        byLabel(targeted, "doc comment lines total=" + YamlReadCorpus.CODE_POINT_LIMIT)
      )
    )
      .hasSize(YamlReadCorpus.CODE_POINT_LIMIT);
    assertThat(
      YamlReadCorpus.textOf(
        byLabel(targeted, "dq string value (words) " + (YamlReadCorpus.MAX_STRING + 1))
      )
    )
      .hasSize(YamlReadCorpus.MAX_STRING + 1 + "a: \"\"".length());
  }

  @Test
  public void itGeneratesTheSameFuzzInputForTheSameIndex() {
    assertThat(YamlReadCorpus.textOf(YamlReadCorpus.fuzz(7)))
      .isEqualTo(YamlReadCorpus.textOf(YamlReadCorpus.fuzz(7)));
    assertThat(YamlReadCorpus.fuzz(7).label()).isEqualTo("#7");
    Set<String> distinct = new HashSet<>();
    IntStream
      .range(0, FUZZ_SAMPLE)
      .forEach(i -> distinct.add(YamlReadCorpus.textOf(YamlReadCorpus.fuzz(i))));
    assertThat(distinct.size()).isGreaterThan((FUZZ_SAMPLE * 9) / 10);
    assertThat(YamlReadCorpus.FUZZ_SIZE).isGreaterThanOrEqualTo(100_000);
  }
}
