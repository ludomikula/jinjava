package com.hubspot.jinjava.lib.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.interpret.InvalidInputException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class ToYamlFilterTest extends BaseInterpretingTest {

  private static final String NEL = "\u0085";
  private static final String LS = " ";
  private static final String PS = " ";
  private static final String INVALID_INPUT_MESSAGE =
    "Invalid input for 'toyaml': input object could not be written as a string";
  private static final int THREADS = 32;
  private static final int ITERATIONS_PER_THREAD = 200;
  private static final int TIMEOUT_SECONDS = 120;

  private ToYamlFilter filter;

  @Before
  public void setup() {
    filter = new ToYamlFilter();
  }

  private static Map<String, Object> map(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  @Test
  public void itWritesObjectAsString() {
    int[] testArray = new int[] { 4, 1, 2 };
    assertThat(filter.filter(testArray, interpreter))
      .isEqualTo("- 4\n" + "- 1\n" + "- 2\n");

    Map<String, Object> testMap = new LinkedHashMap<>();
    testMap.put("testArray", testArray);
    testMap.put("testString", "testString");
    assertThat(filter.filter(testMap, interpreter))
      .isEqualTo(
        "testArray:\n" + "- 4\n" + "- 1\n" + "- 2\n" + "testString: \"testString\"\n"
      );
  }

  @Test
  public void itWritesLineBreakCharactersTheWayJackson2Did() {
    assertThat(filter.filter(map("k", "a" + LS + "b" + PS + "c" + NEL), interpreter))
      .isEqualTo("k: \"a\\Lb\\Pc\\N\"\n");
    assertThat(filter.filter(map(NEL, 1), interpreter))
      .isEqualTo("? '" + NEL + "  '\n: 1\n");
  }

  @Test
  public void itRejectsWhatJackson2CouldNotWrite() {
    for (Object unwritable : new Object[] {
      map("k", Optional.of("x")),
      map("k", LocalDate.of(2020, 1, 2)),
      new Object(),
    }) {
      assertThatThrownBy(() -> filter.filter(unwritable, interpreter))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(INVALID_INPUT_MESSAGE);
    }
  }

  @Test
  public void itNamesItselfToyaml() {
    assertThat(filter.getName()).isEqualTo("toyaml");
  }

  @Test
  public void itIsSafeToShareOneInstanceAcrossThreads() throws Exception {
    List<Object> inputs = List.of(
      map("plain", "value"),
      map(NEL, 1),
      map("k", "a" + LS + "b"),
      List.of("x" + PS, 1, true),
      new int[] { 4, 1, 2 },
      map("nested", map("deeper", List.of(map("k" + NEL, "v" + NEL))))
    );
    List<String> expected = new ArrayList<>();
    for (Object input : inputs) {
      expected.add((String) filter.filter(input, interpreter));
    }
    assertThat(expected.get(1)).isEqualTo("? '" + NEL + "  '\n: 1\n");

    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<List<String>>> futures = new ArrayList<>();
      for (int t = 0; t < THREADS; t++) {
        futures.add(
          executor.submit(() -> {
            start.await();
            List<String> mismatches = new ArrayList<>();
            for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
              int index = i % inputs.size();
              String actual = (String) filter.filter(inputs.get(index), interpreter);
              if (!expected.get(index).equals(actual)) {
                mismatches.add("input " + index + ": " + actual);
              }
            }
            return mismatches;
          })
        );
      }
      start.countDown();
      for (Future<List<String>> future : futures) {
        assertThat(get(future)).isEmpty();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static List<String> get(Future<List<String>> future)
    throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
    return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
}
