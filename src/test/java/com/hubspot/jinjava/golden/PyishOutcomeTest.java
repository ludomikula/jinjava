package com.hubspot.jinjava.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;

import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Compares the outcome class of every cell of {@link PyishOutcomeCorpus} (value text, deferred
 * value, output too big with its sizes, invalid input, other exception) with the outcomes
 * recorded in {@code src/test/resources/outcomes/pyish-outcomes.txt}. The recording was taken
 * on Jackson 2.20.1 before the pyish serializer moved to Jackson 3, and is the reason the
 * comparison survives the removal of Jackson 2 from the test classpath.
 *
 * <p>To (re)record the outcomes run with {@code -Djinjava.pyish.update=true}. In that mode the
 * file is written and the comparison passes. Only do that when a difference to the previous
 * outcomes is intended and reviewed.
 */
public class PyishOutcomeTest {

  public static final String UPDATE_PROPERTY = "jinjava.pyish.update";
  private static final Path OUTCOME_FILE = Paths.get(
    System.getProperty("basedir", "."),
    "src",
    "test",
    "resources",
    "outcomes",
    "pyish-outcomes.txt"
  );
  private static final String LINE_SEPARATOR = "\n";
  private static final int MAX_REPORTED_DIFFS = 20;
  private static final int MIN_CELLS = 2000;
  private static final int CELL_FIELDS = 3;

  @Test
  public void itMatchesRecordedOutcomes() throws IOException {
    List<String> actual = PyishOutcomeCorpus.record();

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.createDirectories(OUTCOME_FILE.getParent());
      Files.write(
        OUTCOME_FILE,
        (String.join(LINE_SEPARATOR, actual) + LINE_SEPARATOR).getBytes(
            StandardCharsets.UTF_8
          )
      );
      System.out.println(
        "[pyish outcomes] wrote " + OUTCOME_FILE + " (" + actual.size() + " cells)"
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
  public void itCoversEveryContextOperationAndOutcomeClass() {
    List<String> cells = PyishOutcomeCorpus.record();
    assertThat(cells.size()).isGreaterThan(MIN_CELLS);

    Set<String> contexts = new HashSet<>();
    Set<String> outcomeClasses = new HashSet<>();
    for (String cell : cells) {
      String[] fields = cell.split(JsonFilterOutcomeCorpus.FIELD_SEPARATOR, -1);
      assertThat(fields).hasSize(CELL_FIELDS);
      contexts.add(fields[0]);
      outcomeClasses.add(fields[2].split(" ", 2)[0]);
    }
    Set<String> expectedContexts = new HashSet<>();
    for (String context : PyishOutcomeCorpus.contexts().keySet()) {
      for (String operation : PyishOutcomeCorpus.OPERATIONS) {
        expectedContexts.add(context + "/" + operation);
      }
    }
    assertThat(contexts).isEqualTo(expectedContexts);
    assertThat(outcomeClasses).contains("VALUE", "DEFERRED", "TOO_BIG", "OTHER");
  }

  @Test
  public void itHasAnOperationForEveryName() {
    for (String name : PyishOutcomeCorpus.OPERATIONS) {
      assertThat(PyishOutcomeCorpus.operation(name)).isNotNull();
    }
    assertThatThrownBy(() -> PyishOutcomeCorpus.operation("unknown"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void itReportsAnyIoExceptionAsOneOutcomeClass() {
    assertThatThrownBy(() ->
        PyishOutcomeCorpus.getAsPyishStringOrThrow(
          new PyishOutcomeTestObjects.IoFailingPyishSerializable()
        )
      )
      .isInstanceOf(UncheckedIOException.class);
    assertThat(PyishOutcomeCorpus.getAsPyishStringOrThrow("x")).isEqualTo("'x'");
    assertThat(
      JsonFilterOutcomeCorpus.outcome(() -> {
        throw new UncheckedIOException(new IOException("x"));
      })
    )
      .isEqualTo("OTHER " + PyishOutcomeCorpus.IO_EXCEPTION_CLASS);
  }

  @Test
  public void itMasksTheContextLimitSizeOnlyForFailingGetters() {
    String tooBig = "TOO_BIG 103 byte output rendered, over limit of 60 bytes";
    assertThat(
      PyishOutcomeCorpus.maskIdentityHashSize(
        tooBig,
        "bean getter throwing RuntimeException"
      )
    )
      .isEqualTo("TOO_BIG <size> byte output rendered, over limit of 60 bytes");
    assertThat(PyishOutcomeCorpus.maskIdentityHashSize(tooBig, "bean 3 properties"))
      .isEqualTo(tooBig);
    assertThat(
      PyishOutcomeCorpus.maskIdentityHashSize(
        PyishOutcomeCorpus.GETTER_TOO_BIG_OUTCOME,
        "bean getter throwing OutputTooBigException"
      )
    )
      .isEqualTo(PyishOutcomeCorpus.GETTER_TOO_BIG_OUTCOME);
    assertThat(
      PyishOutcomeCorpus.maskIdentityHashSize("VALUE x", "bean getter throwing x")
    )
      .isEqualTo("VALUE x");
  }

  @Test
  public void itAddsPyishValuesToTheJsonFilterValues() {
    assertThat(PyishOutcomeCorpus.items().keySet())
      .containsAll(JsonFilterOutcomeCorpus.writeItems().keySet())
      .containsAll(PyishOutcomeCorpus.pyishItems().keySet());
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
