package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.lib.filter.FromYamlFilter;
import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.junit.Before;
import org.junit.Test;

/**
 * Compares what {@code fromyaml} returns with what it returned on Jackson 2.20.1: the 75 corpus
 * inputs, the surrogate pair groups (274 and 48), the line break groups (19 and 68), the 797
 * targeted inputs and {@value YamlReadCorpus#FUZZ_SIZE} seeded random documents. See {@link
 * YamlReadOutcomes} for the file format and how to record again ({@code
 * -Djinjava.yaml.read.update=true}).
 */
public class FromYamlRecordedOutcomeTest extends BaseInterpretingTest {

  static final String CORPUS_FILE = "yaml-read-corpus.txt";
  static final String SURROGATE_FILE = "yaml-read-surrogate.txt";
  static final String SURROGATE_SWEEP_FILE = "yaml-read-surrogate-sweep.txt";
  static final String LINE_BREAK_FILE = "yaml-read-linebreak.txt";
  static final String LINE_BREAK_SWEEP_FILE = "yaml-read-linebreak-sweep.txt";
  static final String TARGETED_FILE = "yaml-read-targeted.txt";
  static final String FUZZ_FILE = "yaml-read-fuzz-sha256.txt";

  static final int CORPUS_SIZE = 75;
  static final int SURROGATE_SIZE = 274;
  static final int SURROGATE_SWEEP_SIZE = 48;
  static final int LINE_BREAK_SIZE = 19;
  static final int LINE_BREAK_SWEEP_SIZE = 68;
  static final int TARGETED_SIZE = 797;

  private Function<Object, String> reader;

  @Before
  public void setup() {
    FromYamlFilter filter = new FromYamlFilter();
    reader = YamlReadOutcomes.reader(text -> filter.filter(text, interpreter));
  }

  @Test
  public void itHasTheDocumentedSetSizes() {
    assertThat(YamlReadCorpus.corpus()).hasSize(CORPUS_SIZE);
    assertThat(YamlReadCorpus.surrogateChunkInputs()).hasSize(SURROGATE_SIZE);
    assertThat(YamlReadCorpus.surrogateSweep()).hasSize(SURROGATE_SWEEP_SIZE);
    assertThat(YamlReadCorpus.lineBreakInputs()).hasSize(LINE_BREAK_SIZE);
    assertThat(YamlReadCorpus.lineBreakSweep()).hasSize(LINE_BREAK_SWEEP_SIZE);
    assertThat(YamlReadCorpus.targeted()).hasSize(TARGETED_SIZE);
  }

  @Test
  public void itMatchesTheRecordedCorpus() throws IOException {
    assertFullText(CORPUS_FILE, YamlReadCorpus.corpus());
  }

  @Test
  public void itMatchesTheRecordedSurrogatePairsAtReadChunkEnds() throws IOException {
    assertFullText(SURROGATE_FILE, YamlReadCorpus.surrogateChunkInputs());
    assertFullText(SURROGATE_SWEEP_FILE, YamlReadCorpus.surrogateSweep());
  }

  @Test
  public void itMatchesTheRecordedLineBreakInputs() throws IOException {
    assertFullText(LINE_BREAK_FILE, YamlReadCorpus.lineBreakInputs());
    assertFullText(LINE_BREAK_SWEEP_FILE, YamlReadCorpus.lineBreakSweep());
  }

  @Test
  public void itMatchesTheRecordedTargetedSet() throws IOException {
    assertFullText(TARGETED_FILE, YamlReadCorpus.targeted());
  }

  @Test
  public void itMatchesTheRecordedFuzzSet() throws IOException {
    assertDigests(FUZZ_FILE, YamlReadCorpus.FUZZ_SIZE, YamlReadCorpus::fuzz);
  }

  private void assertFullText(String fileName, List<Case> cases) throws IOException {
    List<String> actual = YamlReadOutcomes.fullLines(cases, reader);
    if (YamlReadOutcomes.isUpdating()) {
      YamlWriteOutcomes.write(fileName, actual);
      return;
    }
    String report = YamlWriteOutcomes.compareLines(
      fileName,
      YamlWriteOutcomes.read(fileName),
      actual
    );
    assertThat(report).isEmpty();
  }

  private void assertDigests(String fileName, int size, IntFunction<Case> items)
    throws IOException {
    List<String> actual = YamlWriteOutcomes.blockDigests(size, items, reader);
    if (YamlReadOutcomes.isUpdating()) {
      YamlWriteOutcomes.write(fileName, actual);
      return;
    }
    String report = YamlWriteOutcomes.compareDigests(
      fileName,
      YamlWriteOutcomes.read(fileName),
      actual,
      items,
      size,
      reader
    );
    assertThat(report).isEmpty();
  }
}
