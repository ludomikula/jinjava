package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.lib.filter.ToYamlFilter;
import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.junit.Before;
import org.junit.Test;

/**
 * Compares what {@code toyaml} writes with what it wrote on Jackson 2.20.1: the 119 corpus
 * values and the extras, the 14 naming beans, the 21,688 targeted values, the 320 exhaustion
 * values, the 10,640 chunk edge values and {@value YamlWriteCorpus#FUZZ_SIZE} seeded random
 * structures. See {@link YamlWriteOutcomes} for the file format and how to record again
 * ({@code -Djinjava.yaml.update=true}).
 */
public class ToYamlRecordedOutcomeTest extends BaseInterpretingTest {

  static final String CORPUS_FILE = "yaml-write-corpus.txt";
  static final String NAMING_FILE = "yaml-write-naming.txt";
  static final String TARGETED_FILE = "yaml-write-targeted-sha256.txt";
  static final String EXHAUSTION_FILE = "yaml-write-exhaustion-sha256.txt";
  static final String CHUNK_FILE = "yaml-write-chunk-sha256.txt";
  static final String FUZZ_FILE = "yaml-write-fuzz-sha256.txt";

  static final int CORPUS_SIZE = 119;
  static final int TARGETED_SIZE = 21_688;
  static final int EXHAUSTION_SIZE = 320;
  static final int CHUNK_SIZE = 10_640;
  static final int NAMING_SIZE = 14;

  private ToYamlFilter filter;
  private Function<Object, String> writer;

  @Before
  public void setup() {
    filter = new ToYamlFilter();
    writer = value -> (String) filter.filter(value, interpreter);
  }

  @Test
  public void itHasTheDocumentedSetSizes() {
    assertThat(YamlWriteCorpus.corpus()).hasSize(CORPUS_SIZE);
    assertThat(YamlWriteCorpus.namingBeans()).hasSize(NAMING_SIZE);
    assertThat(YamlWriteCorpus.targeted()).hasSize(TARGETED_SIZE);
    assertThat(YamlWriteCorpus.exhaustion()).hasSize(EXHAUSTION_SIZE);
    assertThat(YamlWriteCorpus.chunkEdges()).hasSize(CHUNK_SIZE);
  }

  @Test
  public void itMatchesTheRecordedCorpus() throws IOException {
    List<Case> cases = new ArrayList<>(YamlWriteCorpus.corpus());
    cases.addAll(YamlWriteCorpus.extras());
    assertFullText(CORPUS_FILE, cases);
  }

  @Test
  public void itMatchesTheRecordedBeanPropertyNames() throws IOException {
    assertFullText(NAMING_FILE, YamlWriteCorpus.namingBeans());
  }

  @Test
  public void itMatchesTheRecordedTargetedSet() throws IOException {
    List<Case> cases = YamlWriteCorpus.targeted();
    assertDigests(TARGETED_FILE, cases.size(), cases::get);
  }

  @Test
  public void itMatchesTheRecordedExhaustionSet() throws IOException {
    List<Case> cases = YamlWriteCorpus.exhaustion();
    assertDigests(EXHAUSTION_FILE, cases.size(), cases::get);
  }

  @Test
  public void itMatchesTheRecordedChunkEdgeSet() throws IOException {
    List<Case> cases = YamlWriteCorpus.chunkEdges();
    assertDigests(CHUNK_FILE, cases.size(), cases::get);
  }

  @Test
  public void itMatchesTheRecordedFuzzSet() throws IOException {
    assertDigests(FUZZ_FILE, YamlWriteCorpus.FUZZ_SIZE, YamlWriteCorpus::fuzz);
  }

  private void assertFullText(String fileName, List<Case> cases) throws IOException {
    List<String> actual = YamlWriteOutcomes.fullLines(cases, writer);
    if (YamlWriteOutcomes.isUpdating()) {
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
    List<String> actual = YamlWriteOutcomes.blockDigests(size, items, writer);
    if (YamlWriteOutcomes.isUpdating()) {
      YamlWriteOutcomes.write(fileName, actual);
      return;
    }
    String report = YamlWriteOutcomes.compareDigests(
      fileName,
      YamlWriteOutcomes.read(fileName),
      actual,
      items,
      size,
      writer
    );
    assertThat(report).isEmpty();
  }
}
