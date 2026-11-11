package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.lib.filter.FromYamlFilter;
import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Shows that the comparison against the Jackson 2 outcomes can fail, and for what: each test reads
 * without one part of the mechanism and counts the inputs whose outcome differs from {@link
 * FromYamlReader}'s, which {@link FromYamlRecordedOutcomeTest} has shown equal to Jackson 2's. A
 * part is only needed if taking it out changes outcomes. The expected corpus counts are the
 * numbers measured when the mechanism was chosen (the plan's evidence E1 and E6).
 *
 * <p>The third ablation is not an ablation of this reader: the 1025 character read chunk and the
 * line break handling are the behaviour of SnakeYAML 2.4's reader and scanner, which this reader
 * uses, and of snakeyaml-engine's, which Jackson 3's own YAML module uses. The test shows that
 * the engine-based module fails the sets that target them.
 */
public class FromYamlNegativeControlTest extends BaseInterpretingTest {

  /** Jackson 3's boolean matcher, true and false only, in place of Jackson 2's: failures of the corpus (the 75th input, 5 MB, reads alike). */
  static final int CORPUS_DIFFERENCES_WITH_TRUE_FALSE_ONLY_BOOLEANS = 13;
  /** {@code YAMLMapper.builder().configureForJackson2().build()} of Jackson 3, no custom reader: failures of the corpus (the 75th input, 5 MB, reads alike). */
  static final int CORPUS_DIFFERENCES_WITH_JACKSON_3_YAML_MODULE = 31;

  private static final String PARSE_FAILURE = "PARSE";
  private static final String DUPLICATE_KEY_LABEL = "dup keys across non-adjacent";

  private final FromYamlReader production = new FromYamlReader();

  /** The corpus without its last input, the 5 MB value, which both readers read alike and which takes seconds. */
  private static List<Case> withoutLargeValue(List<Case> corpus) {
    return corpus.subList(0, corpus.size() - 1);
  }

  /** Reads and turns every Jackson or YAML read exception into one marker, as the filter turns it into InvalidInputException. */
  private static Function<String, Object> asFilter(Function<String, Object> read) {
    return yaml -> {
      try {
        return read.apply(yaml);
      } catch (JacksonException | YamlReadException e) {
        throw new IllegalStateException(PARSE_FAILURE);
      }
    };
  }

  private static long differences(
    String label,
    List<Case> cases,
    Function<String, Object> reference,
    Function<String, Object> ablated
  ) {
    Function<Object, String> expected = YamlReadOutcomes.reader(asFilter(reference));
    Function<Object, String> actual = YamlReadOutcomes.reader(asFilter(ablated));
    long differing = cases
      .stream()
      .filter(c ->
        !YamlWriteOutcomes
          .outcome(expected, c.value())
          .equals(YamlWriteOutcomes.outcome(actual, c.value()))
      )
      .count();
    System.out.println(
      "[yaml read negative control] " +
      label +
      ": " +
      differing +
      " of " +
      cases.size() +
      " inputs differ"
    );
    return differing;
  }

  /** The scalar typing with Jackson 3's rule for booleans. */
  private static final class TrueFalseOnlyScalars extends Jackson2YamlScalarDecoder {

    @Override
    Optional<Boolean> matchYamlBoolean(String value) {
      if ("true".equalsIgnoreCase(value)) {
        return Optional.of(Boolean.TRUE);
      }
      return "false".equalsIgnoreCase(value)
        ? Optional.of(Boolean.FALSE)
        : Optional.empty();
    }
  }

  private static Function<String, Object> readWithTrueFalseOnlyBooleans() {
    return new FromYamlReader(new TrueFalseOnlyScalars())::read;
  }

  private static Function<String, Object> readWithJackson3YamlModule() {
    YAMLMapper mapper = YAMLMapper.builder().configureForJackson2().build();
    return yaml -> mapper.readValue(yaml, Object.class);
  }

  @Test
  public void itFailsTheCorpusWithJacksonThreesBooleanMatcher() {
    assertThat(
      differences(
        "corpus, true/false-only booleans",
        withoutLargeValue(YamlReadCorpus.corpus()),
        production::read,
        readWithTrueFalseOnlyBooleans()
      )
    )
      .isEqualTo(CORPUS_DIFFERENCES_WITH_TRUE_FALSE_ONLY_BOOLEANS);
  }

  @Test
  public void itFailsTheCorpusWithJacksonThreesYamlModule() {
    assertThat(
      differences(
        "corpus, Jackson 3 YAML module",
        withoutLargeValue(YamlReadCorpus.corpus()),
        production::read,
        readWithJackson3YamlModule()
      )
    )
      .isEqualTo(CORPUS_DIFFERENCES_WITH_JACKSON_3_YAML_MODULE);
  }

  @Test
  public void itFailsTheRecordedSetsWithJacksonThreesYamlModule() {
    Function<String, Object> module = readWithJackson3YamlModule();
    assertThat(
      differences(
        "line breaks, Jackson 3 YAML module",
        YamlReadCorpus.lineBreakInputs(),
        production::read,
        module
      )
    )
      .isPositive();
    assertThat(
      differences(
        "line break sweep, Jackson 3 YAML module",
        YamlReadCorpus.lineBreakSweep(),
        production::read,
        module
      )
    )
      .isPositive();
  }

  @Test
  public void itFailsTheSurrogatePairsAtChunkEndsWithJacksonThreesYamlModule() {
    Function<String, Object> module = readWithJackson3YamlModule();
    assertThat(
      differences(
        "surrogate pairs at chunk ends, Jackson 3 YAML module",
        YamlReadCorpus.surrogateChunkInputs(),
        production::read,
        module
      )
    )
      .isPositive();
  }

  /** The result with every map rebuilt in reverse key order and every Integer widened to Long. */
  private static Object reversedAndWidened(Object value) {
    if (value instanceof Map<?, ?> map) {
      List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
      Collections.reverse(entries);
      Map<Object, Object> rebuilt = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : entries) {
        rebuilt.put(entry.getKey(), reversedAndWidened(entry.getValue()));
      }
      return rebuilt;
    }
    if (value instanceof List<?> list) {
      List<Object> rebuilt = new ArrayList<>();
      list.forEach(item -> rebuilt.add(reversedAndWidened(item)));
      return rebuilt;
    }
    return value instanceof Integer integer ? Long.valueOf(integer) : value;
  }

  private List<String> brokenLines(List<Case> cases) {
    FromYamlFilter filter = new FromYamlFilter();
    return YamlReadOutcomes.fullLines(
      cases,
      YamlReadOutcomes.reader(text -> reversedAndWidened(filter.filter(text, interpreter))
      )
    );
  }

  @Test
  public void itFailsTheRecordedSetsWhenKeyOrderOrIntegerWidthChange()
    throws IOException {
    List<Case> targeted = YamlReadCorpus.targeted();
    List<String> expectedTargeted = YamlWriteOutcomes.read(
      FromYamlRecordedOutcomeTest.TARGETED_FILE
    );
    List<String> actualTargeted = brokenLines(targeted);
    String targetedReport = YamlWriteOutcomes.compareLines(
      FromYamlRecordedOutcomeTest.TARGETED_FILE,
      expectedTargeted,
      actualTargeted
    );
    assertThat(targetedReport).as("report of the targeted set").isNotEmpty();
    int duplicateKeyLine = indexOfLabel(targeted, DUPLICATE_KEY_LABEL);
    assertThat(actualTargeted.get(duplicateKeyLine))
      .as("the duplicate key case, whose key order is the point of its record")
      .isNotEqualTo(expectedTargeted.get(duplicateKeyLine));

    List<Case> corpus = withoutLargeValue(YamlReadCorpus.corpus());
    String corpusReport = YamlWriteOutcomes.compareLines(
      FromYamlRecordedOutcomeTest.CORPUS_FILE,
      YamlWriteOutcomes
        .read(FromYamlRecordedOutcomeTest.CORPUS_FILE)
        .subList(0, corpus.size()),
      brokenLines(corpus)
    );
    assertThat(corpusReport).as("report of the corpus").isNotEmpty();
    assertThat(corpus)
      .as("the report names a label of the corpus: %s", corpusReport)
      .anyMatch(c -> corpusReport.contains(YamlWriteOutcomes.escape(c.label()) + "\t"));
  }

  private static int indexOfLabel(List<Case> cases, String label) {
    for (int i = 0; i < cases.size(); i++) {
      if (cases.get(i).label().equals(label)) {
        return i;
      }
    }
    throw new AssertionError("no case labelled " + label);
  }

  @Test
  public void itFindsNoDifferenceBetweenTwoProductionReaders() {
    FromYamlReader other = new FromYamlReader();
    assertThat(
      differences(
        "line breaks, second production reader",
        YamlReadCorpus.lineBreakInputs(),
        production::read,
        other::read
      )
    )
      .isZero();
    assertThat(
      differences(
        "surrogate pairs, second production reader",
        YamlReadCorpus.surrogateChunkInputs(),
        production::read,
        other::read
      )
    )
      .isZero();
  }
}
