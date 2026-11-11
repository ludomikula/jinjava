package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class YamlWriteCorpusTest {

  private static final int FUZZ_SAMPLE = 2_000;
  private static final int FIRST_NON_ASCII = 0x100;

  @Test
  public void itBuildsTheSameSetsEveryTime() {
    assertThat(YamlWriteCorpus.targeted()).hasSameSizeAs(YamlWriteCorpus.targeted());
    assertThat(YamlWriteCorpus.targeted().get(100).label())
      .isEqualTo(YamlWriteCorpus.targeted().get(100).label());
    for (int i = 0; i < FUZZ_SAMPLE; i++) {
      assertThat(YamlWriteCorpus.fuzz(i).value())
        .isEqualTo(YamlWriteCorpus.fuzz(i).value());
    }
  }

  @Test
  public void itGeneratesDifferentFuzzStructures() {
    Set<String> distinct = new HashSet<>();
    for (int i = 0; i < FUZZ_SAMPLE; i++) {
      distinct.add(String.valueOf(YamlWriteCorpus.fuzz(i).value()));
    }
    assertThat(distinct.size()).isGreaterThan(FUZZ_SAMPLE / 4);
  }

  @Test
  public void itLabelsEveryCase() {
    for (List<Case> set : List.of(
      YamlWriteCorpus.corpus(),
      YamlWriteCorpus.extras(),
      YamlWriteCorpus.namingBeans(),
      YamlWriteCorpus.targeted(),
      YamlWriteCorpus.exhaustion()
    )) {
      assertThat(set).allMatch(c -> c.label() != null && !c.label().isEmpty());
    }
  }

  @Test
  public void itMakesTheCharactersThatMatterVisible() {
    assertThat(YamlWriteCorpus.visible("a\n\t\r\u0085\u2028\u2029\u00a0\u0007\u007fz"))
      .isEqualTo("a\\n\\t\\r<NEL><LS><PS><NBSP><07><7F>z");
  }

  @Test
  public void itDefinesTheUsableCodePointsAsPrintableAndAboveLatin1() {
    assertThat(YamlWriteCorpus.isUsable(FIRST_NON_ASCII)).isTrue();
    assertThat(YamlWriteCorpus.isUsable(FIRST_NON_ASCII - 1)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0x2028)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0x2029)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0xFEFF)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0xD800)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0xFFFE)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0x10000)).isTrue();
    assertThat(YamlWriteCorpus.isUsable(0xFFFFE)).isFalse();
    assertThat(YamlWriteCorpus.isUsable(0x10FFFD)).isTrue();
    assertThat(YamlWriteCorpus.isUsable(0x10FFFF)).isFalse();
  }
}
