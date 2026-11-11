package com.hubspot.jinjava.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Compares the outcome class of every cell of {@link JsonFilterOutcomeCorpus} (value text,
 * deferred value, output too big with its sizes, invalid input with its reason, other
 * exception) with the outcomes recorded in {@code src/test/resources/outcomes}. The recording
 * was taken on Jackson 2.20.1 before {@code tojson}, {@code fromjson} and {@code pprint} moved
 * to Jackson 3, and is the reason the comparison survives the removal of Jackson 2 from the
 * test classpath.
 *
 * <p>To (re)record the outcomes run with {@code -Djinjava.outcomes.update=true}. In that mode
 * the file is written and the comparison passes. Only do that when a difference to the previous
 * outcomes is intended and reviewed.
 */
public class JsonFilterOutcomeTest {

  public static final String UPDATE_PROPERTY = "jinjava.outcomes.update";
  private static final Path OUTCOME_FILE = Paths.get(
    System.getProperty("basedir", "."),
    "src",
    "test",
    "resources",
    "outcomes",
    "json-filter-outcomes.txt"
  );
  private static final String LINE_SEPARATOR = "\n";
  private static final int MAX_REPORTED_DIFFS = 20;
  private static final int MIN_CELLS = 500;

  @Test
  public void itMatchesRecordedOutcomes() throws IOException {
    List<String> actual = JsonFilterOutcomeCorpus.record();

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.createDirectories(OUTCOME_FILE.getParent());
      Files.write(
        OUTCOME_FILE,
        (String.join(LINE_SEPARATOR, actual) + LINE_SEPARATOR).getBytes(
            StandardCharsets.UTF_8
          )
      );
      System.out.println(
        "[outcomes] wrote " + OUTCOME_FILE + " (" + actual.size() + " cells)"
      );
      return;
    }

    if (!Files.exists(OUTCOME_FILE)) {
      fail(
        "Outcome file missing: " +
        OUTCOME_FILE.toAbsolutePath() +
        " (run with -D" +
        UPDATE_PROPERTY +
        "=true)"
      );
    }
    List<String> expected = Files.readAllLines(OUTCOME_FILE, StandardCharsets.UTF_8);
    List<String> differences = differences(expected, actual);
    if (!differences.isEmpty()) {
      fail(
        differences.size() +
        " of " +
        Math.max(expected.size(), actual.size()) +
        " cells differ from " +
        OUTCOME_FILE.toAbsolutePath() +
        "\n" +
        String.join(
          LINE_SEPARATOR,
          differences.subList(0, Math.min(differences.size(), MAX_REPORTED_DIFFS))
        )
      );
    }
  }

  @Test
  public void itCoversEveryContextAndAllOutcomeClasses() {
    List<String> cells = JsonFilterOutcomeCorpus.record();
    assertThat(cells.size()).isGreaterThan(MIN_CELLS);

    Set<String> contexts = new HashSet<>();
    Set<String> outcomeClasses = new HashSet<>();
    for (String cell : cells) {
      String[] fields = cell.split(JsonFilterOutcomeCorpus.FIELD_SEPARATOR, -1);
      assertThat(fields).hasSize(3);
      contexts.add(fields[0]);
      outcomeClasses.add(fields[2].split(" ", 2)[0]);
    }
    assertThat(contexts)
      .containsExactlyInAnyOrder(
        "tojson/default/nolimit",
        "tojson/default/limit20",
        "tojson/default/limit10000",
        "pprint/default",
        "fromjson/default",
        "tojson/snake/nolimit",
        "tojson/snake/limit20",
        "tojson/snake/limit10000",
        "pprint/snake",
        "fromjson/snake"
      );
    assertThat(outcomeClasses).contains("VALUE", "DEFERRED", "TOO_BIG", "INVALID_INPUT");
  }

  @Test
  public void itReducesValuesToOneLine() {
    assertThat(JsonFilterOutcomeCorpus.escape("a\nb\tc\\\u0001\u00e9"))
      .isEqualTo("a\\nb\\tc\\\\\\u0001\\u00e9");
    assertThat(JsonFilterOutcomeCorpus.describe(null)).isEqualTo("null");
    assertThat(JsonFilterOutcomeCorpus.describe("x")).isEqualTo("java.lang.String:x");
    assertThat(
      JsonFilterOutcomeCorpus.describe(
        "x".repeat(JsonFilterOutcomeCorpus.MAX_INLINE_LENGTH)
      )
    )
      .startsWith("<length ")
      .contains(" sha256 ");
  }

  @Test
  public void itClassifiesFailuresByOutcomeClass() {
    assertThat(
      JsonFilterOutcomeCorpus.outcome(() -> {
        throw new com.hubspot.jinjava.interpret.DeferredValueException("d");
      })
    )
      .isEqualTo("DEFERRED d");
    assertThat(
      JsonFilterOutcomeCorpus.outcome(() -> {
        throw new com.hubspot.jinjava.interpret.OutputTooBigException(10, 20);
      })
    )
      .isEqualTo("TOO_BIG 20 byte output rendered, over limit of 10 bytes");
    assertThat(
      JsonFilterOutcomeCorpus.outcome(() -> {
        throw new IllegalStateException("x");
      })
    )
      .isEqualTo("OTHER java.lang.IllegalStateException");
  }

  private static List<String> differences(List<String> expected, List<String> actual) {
    List<String> differences = new ArrayList<>();
    int max = Math.max(expected.size(), actual.size());
    for (int i = 0; i < max; i++) {
      String e = i < expected.size() ? expected.get(i) : null;
      String a = i < actual.size() ? actual.get(i) : null;
      if (e == null || !e.equals(a)) {
        differences.add("@@ cell " + (i + 1) + " @@\n-" + e + "\n+" + a);
      }
    }
    return differences;
  }
}
