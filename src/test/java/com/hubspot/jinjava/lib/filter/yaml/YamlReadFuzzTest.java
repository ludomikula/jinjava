package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.Test;

public class YamlReadFuzzTest {

  private static final int SAMPLE = 5000;
  private static final long OTHER_SEED = 1L;
  private static final int READ_CHUNK = 1025;

  @Test
  public void itDependsOnlyOnTheSeedAndTheIndex() {
    YamlReadFuzz first = new YamlReadFuzz(YamlReadCorpus.FUZZ_SEED);
    YamlReadFuzz second = new YamlReadFuzz(YamlReadCorpus.FUZZ_SEED);
    assertThat(first.input(0)).isEqualTo(second.input(0));
    assertThat(first.input(99_999)).isEqualTo(second.input(99_999));
    assertThat(first.input(0)).isNotEqualTo(first.input(1));
    assertThat(first.input(0)).isNotEqualTo(new YamlReadFuzz(OTHER_SEED).input(0));
  }

  @Test
  public void itRendersHostileTextAndLongLines() {
    YamlReadFuzz fuzz = new YamlReadFuzz(YamlReadCorpus.FUZZ_SEED);
    long withLineBreakCharacters = IntStream
      .range(0, SAMPLE)
      .filter(i ->
        fuzz.input(i).chars().anyMatch(c -> c == 0x85 || c == 0x2028 || c == 0x2029)
      )
      .count();
    long longer = IntStream
      .range(0, SAMPLE)
      .filter(i -> fuzz.input(i).length() > READ_CHUNK)
      .count();
    long withDocumentMarkers = IntStream
      .range(0, SAMPLE)
      .filter(i -> fuzz.input(i).contains("---"))
      .count();
    assertThat(withLineBreakCharacters).isPositive();
    assertThat(longer).isPositive();
    assertThat(withDocumentMarkers).isPositive();
  }
}
