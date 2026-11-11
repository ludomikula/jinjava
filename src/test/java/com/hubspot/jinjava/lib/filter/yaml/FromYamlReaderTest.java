package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hubspot.jinjava.BaseInterpretingTest;
import com.hubspot.jinjava.interpret.InvalidInputException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.FromYamlFilter;
import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Test;
import org.yaml.snakeyaml.error.YAMLException;

public class FromYamlReaderTest extends BaseInterpretingTest {

  static final int THREADS = 32;
  static final int READS_PER_THREAD = 3000;
  static final int RANDOM_POOL_SIZE = 4000;
  static final long RANDOM_SEED = 1000L;
  static final int EXECUTOR_TIMEOUT_SECONDS = 600;
  static final int MAX_NESTING = 1000;
  static final int MAX_NUMBER_LENGTH = 1000;
  /** Emoji at these offsets (after "k: ") straddle the 1024 character read chunk of SnakeYAML's reader. */
  static final int EMOJI_FIRST_OFFSET = 1018;
  static final int EMOJI_LAST_OFFSET = 1024;

  private final FromYamlReader reader = new FromYamlReader();

  @Test
  public void itReadsMapsListsAndScalarsWithJacksonTwosTypes() {
    assertThat(reader.read("a: 1\nb: [yes, 1.5, x, ~, 0x1F, 12345678901]\nc: {d: 'e'}"))
      .isEqualTo(
        Map.of(
          "a",
          1,
          "b",
          Arrays.asList(true, 1.5, "x", null, 31, 12345678901L),
          "c",
          Map.of("d", "e")
        )
      );
    assertThat(reader.read("plain")).isEqualTo("plain");
  }

  @Test
  public void itThrowsAYamlReadExceptionWhenThereIsNoDocument() {
    for (String text : new String[] { "", "# comment", "..." }) {
      assertThatThrownBy(() -> reader.read(text))
        .as("the text '%s' has no document", text)
        .isInstanceOf(YamlReadException.class);
    }
    assertThat(reader.read("---")).as("an empty document").isNull();
  }

  @Test
  public void itKeepsTheKeyOrderOfTheDocument() {
    assertThat(
      new ArrayList<Object>(((Map<?, ?>) reader.read("z: 1\ny: 2\nx: 3\nw: 4")).keySet())
    )
      .containsExactly("z", "y", "x", "w");
  }

  @Test
  public void itReplacesADuplicateKeyAndKeepsItsFirstPosition() {
    Map<?, ?> flat = (Map<?, ?>) reader.read("a: 1\nb: 2\na: 3");
    assertThat(new ArrayList<Object>(flat.keySet())).containsExactly("a", "b");
    assertThat(flat.get("a")).isEqualTo(3);
    Map<?, ?> nested = (Map<?, ?>) reader.read("a: {x: 1}\na: [2]");
    assertThat(nested).as("the last value of a repeated key wins").hasSize(1);
    assertThat(nested.get("a")).isEqualTo(List.of(2));
  }

  @Test
  public void itReadsKeysAsTheirRawText() {
    for (String key : new String[] { "~", "1", "yes", "1.5", "null", "0x1F" }) {
      assertThat(reader.read(key + ": v")).as("key %s", key).isEqualTo(Map.of(key, "v"));
    }
    assertThat(reader.read("!!int 7: v")).isEqualTo(Map.of("7", "v"));
    assertThat(reader.read("? \n: v"))
      .as("an empty explicit key")
      .isEqualTo(Map.of("", "v"));
  }

  @Test
  public void itReadsAnAliasAsTheAnchorNameAndDoesNotMergeKeys() {
    assertThat(reader.read("a: &x 1\nb: *x")).isEqualTo(Map.of("a", 1, "b", "x"));
    assertThat(reader.read("[&x 1, *x]")).isEqualTo(List.of(1, "x"));
    assertThat(reader.read("base: &base {a: 1}\nderived:\n  <<: *base"))
      .isEqualTo(Map.of("base", Map.of("a", 1), "derived", Map.of("<<", "base")));
  }

  @Test
  public void itRejectsAnAliasASequenceOrAMappingAsAKey() {
    for (String text : new String[] {
      "&x a: 1\n*x : 2",
      "? [a, b]\n: 1",
      "? {a: 1}\n: 2",
    }) {
      assertThatThrownBy(() -> reader.read(text))
        .as("the key of %s", text)
        .isInstanceOf(YamlReadException.class);
    }
  }

  @Test
  public void itReadsOnlyTheFirstDocument() {
    assertThat(reader.read("a: 1\n---\nb: 2")).isEqualTo(Map.of("a", 1));
    assertThat(reader.read("a: 1\n---\nb: ["))
      .as("an invalid second")
      .isEqualTo(Map.of("a", 1));
    assertThat(reader.read("a: 1\n---\n]\n"))
      .as("a stray close")
      .isEqualTo(Map.of("a", 1));
  }

  @Test
  public void itReadsEmptyValuesAsNullAndEmptyContainersAsEmptyCollections() {
    assertThat(reader.read("a:")).isEqualTo(Collections.singletonMap("a", null));
    assertThat(reader.read("- ")).isEqualTo(Collections.singletonList(null));
    assertThat(reader.read("{}")).isInstanceOf(LinkedHashMap.class).isEqualTo(Map.of());
    assertThat(reader.read("[]")).isInstanceOf(ArrayList.class).isEqualTo(List.of());
  }

  @Test
  public void itBuildsLinkedHashMapsAndArrayListsAtEveryLevel() {
    Map<?, ?> root = (Map<?, ?>) reader.read("a: {b: [c, {d: []}]}");
    assertThat(root).isInstanceOf(LinkedHashMap.class);
    Map<?, ?> a = (Map<?, ?>) root.get("a");
    assertThat(a).isInstanceOf(LinkedHashMap.class);
    List<?> b = (List<?>) a.get("b");
    assertThat(b).isInstanceOf(ArrayList.class);
    Map<?, ?> d = (Map<?, ?>) b.get(1);
    assertThat(d).isInstanceOf(LinkedHashMap.class);
    assertThat(d.get("d")).isInstanceOf(ArrayList.class);
  }

  @Test
  public void itIgnoresTagsOnCollections() {
    assertThat(reader.read("!!set {a, b}")).isEqualTo(setOfKeys("a", "b"));
    assertThat(reader.read("!!omap [{a: 1}]")).isEqualTo(List.of(Map.of("a", 1)));
    assertThat(reader.read("!foo {a: 1}")).isEqualTo(Map.of("a", 1));
  }

  @Test
  public void itDecodesTextTheWayTheScannerDoes() {
    assertThat(reader.read("k: 'a\u0085b'")).isEqualTo(Map.of("k", "a b"));
    String emoji = "\uD83D\uDE00";
    for (int offset = EMOJI_FIRST_OFFSET; offset <= EMOJI_LAST_OFFSET; offset++) {
      String text = "x".repeat(offset) + emoji + "y";
      assertThat(reader.read("k: " + text))
        .as("an emoji after %d characters", offset)
        .isEqualTo(Map.of("k", text));
    }
  }

  @Test
  public void itThrowsAYamlReadExceptionForInvalidYaml() {
    assertThatThrownBy(() -> reader.read("a: b:"))
      .isInstanceOf(YamlReadException.class)
      .hasCauseInstanceOf(YAMLException.class);
    assertThatThrownBy(() -> reader.read("]")).isInstanceOf(YamlReadException.class);
  }

  @Test
  public void itAppliesJacksonTwosNestingLimit() {
    for (String open : new String[] { "[", "{a: " }) {
      String close = open.equals("[") ? "]" : "}";
      assertThat(reader.read(open.repeat(MAX_NESTING) + close.repeat(MAX_NESTING)))
        .as("1000 levels of %s", open)
        .isNotNull();
      assertThatThrownBy(() ->
          reader.read(open.repeat(MAX_NESTING + 1) + close.repeat(MAX_NESTING + 1))
        )
        .as("1001 levels of %s", open)
        .isInstanceOf(YamlReadException.class);
    }
  }

  @Test
  public void itThrowsAYamlReadExceptionWhenANumberIsTooLong() {
    assertThat(reader.read("a: " + "1".repeat(MAX_NUMBER_LENGTH))).isNotNull();
    assertThatThrownBy(() -> reader.read("a: " + "1".repeat(MAX_NUMBER_LENGTH + 1)))
      .isInstanceOf(YamlReadException.class);
  }

  private static Map<String, Object> setOfKeys(String... keys) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (String key : keys) {
      map.put(key, null);
    }
    return map;
  }

  @Test
  public void itGivesTheSameResultsOnEveryThreadForOneSharedFilter() throws Exception {
    FromYamlFilter shared = new FromYamlFilter();
    List<String> pool = new ArrayList<>();
    for (List<Case> set : List.of(
      YamlReadCorpus.corpus().subList(0, YamlReadCorpus.corpus().size() - 1),
      YamlReadCorpus.surrogateChunkInputs(),
      YamlReadCorpus.surrogateSweep(),
      YamlReadCorpus.lineBreakInputs(),
      YamlReadCorpus.lineBreakSweep()
    )) {
      set.forEach(c -> pool.add(YamlReadCorpus.textOf(c)));
    }
    for (int i = 0; i < RANDOM_POOL_SIZE; i++) {
      pool.add(YamlReadCorpus.textOf(YamlReadCorpus.fuzz(i)));
    }
    Function<JinjavaInterpreter, Function<String, String>> readerFor = interpreter ->
      text -> {
        try {
          return YamlReadOutcomes.summarize(
            YamlReadOutcomes.show(shared.filter(text, interpreter))
          );
        } catch (InvalidInputException e) {
          return "PARSE";
        }
      };
    Function<String, String> single = readerFor.apply(interpreter);
    List<String> expected = new ArrayList<>();
    pool.forEach(text -> expected.add(single.apply(text)));
    assertThat(expected).contains("PARSE").anyMatch(outcome -> !"PARSE".equals(outcome));

    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (int thread = 0; thread < THREADS; thread++) {
        long seed = RANDOM_SEED + thread;
        Callable<Integer> task = () -> {
          Function<String, String> own = readerFor.apply(
            new JinjavaInterpreter(jinjava.newInterpreter())
          );
          Random random = new Random(seed);
          int mismatches = 0;
          for (int read = 0; read < READS_PER_THREAD; read++) {
            int index = random.nextInt(pool.size());
            if (!expected.get(index).equals(own.apply(pool.get(index)))) {
              mismatches++;
            }
          }
          return mismatches;
        };
        futures.add(executor.submit(task));
      }
      int mismatches = 0;
      for (Future<Integer> future : futures) {
        mismatches += future.get();
      }
      assertThat(mismatches).isZero();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .isTrue();
    }
  }
}
