package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;

public class YamlWriteOutcomesTest {

  private static final int SIZE = 250;
  private static final int SHORT_LAST_BLOCK = 50;
  private static final int SHA256_HEX_LENGTH = 64;

  private static final Function<Object, String> WRITER = value -> {
    if ("boom".equals(value)) {
      throw new IllegalStateException("x");
    }
    return "w:" + value;
  };

  private static Case item(int i) {
    return new Case("case " + i, "v" + i);
  }

  @Test
  public void itEscapesToOneLinePrintableAscii() {
    assertThat(YamlWriteOutcomes.escape("a\nb\tc\rd\\e\u0085\u2028\u00e9\ud83d\ude00~ "))
      .isEqualTo("a\\nb\\tc\\rd\\\\e\\u0085\\u2028\\u00e9\\ud83d\\ude00~ ");
    assertThat(YamlWriteOutcomes.escape("")).isEmpty();
  }

  @Test
  public void itRecordsTheTextOrTheClassOfTheFailure() {
    assertThat(YamlWriteOutcomes.outcome(WRITER, "a\n")).isEqualTo("OK w:a\\n");
    assertThat(YamlWriteOutcomes.outcome(WRITER, "boom"))
      .isEqualTo("ERR IllegalStateException");
    assertThat(YamlWriteOutcomes.rawOutcome(WRITER, "boom"))
      .isEqualTo("ERR IllegalStateException");
    assertThat(YamlWriteOutcomes.rawOutcome(WRITER, "a")).isEqualTo("w:a");
  }

  @Test
  public void itWritesOneLinePerCaseWithEscapedLabel() {
    List<String> lines = YamlWriteOutcomes.fullLines(
      List.of(new Case("l\tabel", "x"), new Case("second", "boom")),
      WRITER
    );
    assertThat(lines)
      .containsExactly("l\\tabel\tOK w:x", "second\tERR IllegalStateException");
  }

  @Test
  public void itDigestsOneHashPerBlockOfHundred() {
    List<String> digests = YamlWriteOutcomes.blockDigests(
      SIZE,
      YamlWriteOutcomesTest::item,
      WRITER
    );
    assertThat(digests).hasSize(3);
    assertThat(digests).doesNotHaveDuplicates();
    assertThat(digests).allMatch(d -> d.matches("[0-9a-f]{" + SHA256_HEX_LENGTH + "}"));
    assertThat(YamlWriteOutcomes.blockCount(SIZE)).isEqualTo(3);
    assertThat(YamlWriteOutcomes.blockCount(YamlWriteOutcomes.BLOCK_SIZE)).isEqualTo(1);
    assertThat(YamlWriteOutcomes.blockCount(YamlWriteOutcomes.BLOCK_SIZE + 1))
      .isEqualTo(2);
  }

  @Test
  public void itDigestsDeterministicallyAndSeparatesNeighbouringOutcomes() {
    List<String> first = YamlWriteOutcomes.blockDigests(
      SIZE,
      YamlWriteOutcomesTest::item,
      WRITER
    );
    List<String> second = YamlWriteOutcomes.blockDigests(
      SIZE,
      YamlWriteOutcomesTest::item,
      WRITER
    );
    assertThat(second).isEqualTo(first);

    List<String> joined = YamlWriteOutcomes.blockDigests(
      2,
      i -> new Case("c", i == 0 ? "ab" : "c"),
      Object::toString
    );
    List<String> shifted = YamlWriteOutcomes.blockDigests(
      2,
      i -> new Case("c", i == 0 ? "a" : "bc"),
      Object::toString
    );
    assertThat(joined).isNotEqualTo(shifted);
  }

  @Test
  public void itDigestsTextLongerThanOneChunk() {
    String longText = "x".repeat(3 * 8192 + 5);
    List<String> a = YamlWriteOutcomes.blockDigests(
      1,
      i -> new Case("c", longText),
      Object::toString
    );
    List<String> b = YamlWriteOutcomes.blockDigests(
      1,
      i -> new Case("c", longText + "y"),
      Object::toString
    );
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  public void itFindsNoDifferenceInEqualDigests() {
    List<String> digests = YamlWriteOutcomes.blockDigests(
      SIZE,
      YamlWriteOutcomesTest::item,
      WRITER
    );
    assertThat(
      YamlWriteOutcomes.compareDigests(
        "n",
        digests,
        digests,
        YamlWriteOutcomesTest::item,
        SIZE,
        WRITER
      )
    )
      .isEmpty();
  }

  @Test
  public void itReportsTheFirstDifferingBlockWithBothHashesAndTheActualOutputs() {
    List<String> expected = YamlWriteOutcomes.blockDigests(
      SIZE,
      YamlWriteOutcomesTest::item,
      WRITER
    );
    List<String> actual = new ArrayList<>(expected);
    actual.set(1, "0".repeat(SHA256_HEX_LENGTH));
    actual.set(2, "1".repeat(SHA256_HEX_LENGTH));

    String report = YamlWriteOutcomes.compareDigests(
      "set-file",
      expected,
      actual,
      YamlWriteOutcomesTest::item,
      SIZE,
      WRITER
    );

    assertThat(report)
      .contains("2 of 3 blocks of 100 differ in set-file")
      .contains("first differing block 1 (cases 100..199)")
      .contains("expected sha256 " + expected.get(1))
      .contains("actual   sha256 " + actual.get(1))
      .contains("#100 case 100 -> w:v100")
      .contains("#111 case 111 -> w:v111")
      .doesNotContain("#112");
  }

  @Test
  public void itReportsAShortLastBlockAndMissingDigests() {
    List<String> expected = YamlWriteOutcomes.blockDigests(
      SIZE,
      YamlWriteOutcomesTest::item,
      WRITER
    );
    List<String> actual = new ArrayList<>(expected.subList(0, 2));

    String report = YamlWriteOutcomes.compareDigests(
      "set-file",
      expected,
      actual,
      YamlWriteOutcomesTest::item,
      SIZE,
      WRITER
    );

    assertThat(report)
      .contains("1 of 3 blocks")
      .contains("first differing block 2 (cases 200.." + (SIZE - 1) + ")")
      .contains("actual   sha256 <none>")
      .contains("#200 case 200");
    assertThat(SIZE - 2 * YamlWriteOutcomes.BLOCK_SIZE).isEqualTo(SHORT_LAST_BLOCK);
  }

  @Test
  public void itReportsDifferingLines() {
    assertThat(YamlWriteOutcomes.compareLines("f", List.of("a", "b"), List.of("a", "b")))
      .isEmpty();
    assertThat(YamlWriteOutcomes.compareLines("f", List.of("a", "b"), List.of("a", "c")))
      .contains("1 of 2 lines differ from f")
      .contains("@@ line 2 @@")
      .contains("-b")
      .contains("+c");
    assertThat(YamlWriteOutcomes.compareLines("f", List.of("a"), List.of("a", "extra")))
      .contains("1 of 2 lines differ")
      .contains("-<none>")
      .contains("+extra");
  }

  @Test
  public void itClipsLongOutputsInReports() {
    String report = YamlWriteOutcomes.compareLines(
      "f",
      List.of("x".repeat(1000)),
      List.of("y")
    );
    assertThat(report).contains("...[1000 chars]").doesNotContain("x".repeat(200));
  }

  @Test
  public void itNamesTheUpdateSwitchWhenAnOutcomeFileIsMissing() throws IOException {
    org.assertj.core.api.Assertions
      .assertThatThrownBy(() -> YamlWriteOutcomes.read("no-such-file.txt"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(YamlWriteOutcomes.UPDATE_PROPERTY);
  }
}
