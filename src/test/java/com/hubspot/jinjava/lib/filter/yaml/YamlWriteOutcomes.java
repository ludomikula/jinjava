package com.hubspot.jinjava.lib.filter.yaml;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Turns {@code toyaml} results into the text recorded under {@code src/test/resources/outcomes}
 * and compares them. Small sets are recorded as full text (one line per case: label, tab,
 * outcome); large sets as one SHA-256 per {@link #BLOCK_SIZE} cases, which keeps the resource
 * files small and still names the block that changed.
 *
 * <p>The expected files were recorded from {@code ToYamlFilter} on Jackson 2.20.1 before the
 * filter moved to Jackson 3. To record again run the test with
 * {@code -D}{@value #UPDATE_PROPERTY}{@code =true}; the comparison then passes and the files are
 * rewritten from whatever the filter produces now. Do that only for a reviewed, intended change
 * of the output, and never to make a failing comparison pass.
 */
final class YamlWriteOutcomes {

  static final String UPDATE_PROPERTY = "jinjava.yaml.update";
  static final int BLOCK_SIZE = 100;

  private static final Path OUTCOME_DIRECTORY = Paths.get(
    System.getProperty("basedir", "."),
    "src",
    "test",
    "resources",
    "outcomes"
  );
  private static final String LINE_SEPARATOR = "\n";
  private static final String FIELD_SEPARATOR = "\t";
  private static final String OK_PREFIX = "OK ";
  private static final String ERROR_PREFIX = "ERR ";
  private static final String DIGEST_ALGORITHM = "SHA-256";
  private static final int DIGEST_CHUNK_CHARS = 8192;
  private static final int MAX_CLIPPED_LENGTH = 160;
  private static final int HEX_RADIX = 16;
  private static final int NIBBLE_BITS = 4;
  private static final int NIBBLE_MASK = 0xF;
  private static final int MAX_REPORTED_ITEMS = 12;
  private static final int MAX_REPORTED_DIFFS = 20;

  private YamlWriteOutcomes() {}

  static boolean isUpdating() {
    return Boolean.getBoolean(UPDATE_PROPERTY);
  }

  static Path file(String name) {
    return OUTCOME_DIRECTORY.resolve(name);
  }

  /** The recorded outcome of writing {@code value}: the text, or the class of the failure. */
  static String outcome(Function<Object, String> writer, Object value) {
    try {
      return OK_PREFIX + escape(writer.apply(value));
    } catch (RuntimeException e) {
      return ERROR_PREFIX + e.getClass().getSimpleName();
    }
  }

  /** The outcome as it was before escaping, for reports that print the real text. */
  static String rawOutcome(Function<Object, String> writer, Object value) {
    try {
      return writer.apply(value);
    } catch (RuntimeException e) {
      return ERROR_PREFIX + e.getClass().getSimpleName();
    }
  }

  /** One line text: backslash, line breaks and tab as escapes, everything outside printable ASCII as \\uXXXX. */
  static String escape(String text) {
    StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c >= ' ' && c <= '~') {
            sb.append(c);
          } else {
            sb.append("\\u");
            for (int shift = 3 * NIBBLE_BITS; shift >= 0; shift -= NIBBLE_BITS) {
              sb.append(Character.forDigit((c >> shift) & NIBBLE_MASK, HEX_RADIX));
            }
          }
      }
    }
    return sb.toString();
  }

  /** One line per case: {@code escaped label TAB outcome}. */
  static List<String> fullLines(List<Case> cases, Function<Object, String> writer) {
    return cases
      .parallelStream()
      .map(c -> escape(c.label()) + FIELD_SEPARATOR + outcome(writer, c.value()))
      .collect(Collectors.toList());
  }

  /** One lower case hex SHA-256 per block of {@link #BLOCK_SIZE} cases (the last block may be short). */
  static List<String> blockDigests(
    int size,
    IntFunction<Case> items,
    Function<Object, String> writer
  ) {
    int blocks = blockCount(size);
    return IntStream
      .range(0, blocks)
      .parallel()
      .mapToObj(block -> digestBlock(block, size, items, writer))
      .collect(Collectors.toList());
  }

  static int blockCount(int size) {
    return (size + BLOCK_SIZE - 1) / BLOCK_SIZE;
  }

  private static String digestBlock(
    int block,
    int size,
    IntFunction<Case> items,
    Function<Object, String> writer
  ) {
    MessageDigest digest = newDigest();
    int end = Math.min(size, (block + 1) * BLOCK_SIZE);
    for (int i = block * BLOCK_SIZE; i < end; i++) {
      update(digest, outcome(writer, items.apply(i).value()));
    }
    return hex(digest.digest());
  }

  /** Feeds the length and then the UTF-16 code units, so no two outcome sequences collide by concatenation. */
  private static void update(MessageDigest digest, String outcome) {
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(outcome.length()).array());
    for (int from = 0; from < outcome.length(); from += DIGEST_CHUNK_CHARS) {
      int to = Math.min(outcome.length(), from + DIGEST_CHUNK_CHARS);
      ByteBuffer buffer = ByteBuffer.allocate((to - from) * Character.BYTES);
      buffer.asCharBuffer().put(outcome, from, to);
      digest.update(buffer.array());
    }
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance(DIGEST_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(DIGEST_ALGORITHM + " is required on every JVM", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> NIBBLE_BITS) & NIBBLE_MASK, HEX_RADIX));
      sb.append(Character.forDigit(b & NIBBLE_MASK, HEX_RADIX));
    }
    return sb.toString();
  }

  static void write(String name, List<String> lines) throws IOException {
    Files.createDirectories(OUTCOME_DIRECTORY);
    Files.write(
      file(name),
      (String.join(LINE_SEPARATOR, lines) + LINE_SEPARATOR).getBytes(
          StandardCharsets.UTF_8
        )
    );
    System.out.println(
      "[yaml outcomes] wrote " + file(name) + " (" + lines.size() + " lines)"
    );
  }

  static List<String> read(String name) throws IOException {
    Path file = file(name);
    if (!Files.exists(file)) {
      throw new IllegalStateException(
        "Outcome file missing: " +
        file.toAbsolutePath() +
        " (run with -D" +
        UPDATE_PROPERTY +
        "=true)"
      );
    }
    return Files.readAllLines(file, StandardCharsets.UTF_8);
  }

  /** Empty when equal; otherwise a report of the first differing lines. */
  static String compareLines(String name, List<String> expected, List<String> actual) {
    List<String> differences = new ArrayList<>();
    int max = Math.max(expected.size(), actual.size());
    for (int i = 0; i < max; i++) {
      String e = i < expected.size() ? expected.get(i) : null;
      String a = i < actual.size() ? actual.get(i) : null;
      if (e == null || !e.equals(a)) {
        differences.add("@@ line " + (i + 1) + " @@\n-" + clip(e) + "\n+" + clip(a));
      }
    }
    if (differences.isEmpty()) {
      return "";
    }
    return (
      differences.size() +
      " of " +
      max +
      " lines differ from " +
      name +
      "\n" +
      String.join(
        LINE_SEPARATOR,
        differences.subList(0, Math.min(differences.size(), MAX_REPORTED_DIFFS))
      )
    );
  }

  /**
   * Empty when every block digest is equal; otherwise the number of differing blocks, the first
   * differing block with expected and actual digest, and the actual output of the cases in that
   * block (the digests alone cannot tell which case of the block differs).
   */
  static String compareDigests(
    String name,
    List<String> expected,
    List<String> actual,
    IntFunction<Case> items,
    int size,
    Function<Object, String> writer
  ) {
    int max = Math.max(expected.size(), actual.size());
    int differing = 0;
    int first = -1;
    for (int block = 0; block < max; block++) {
      String e = block < expected.size() ? expected.get(block) : null;
      String a = block < actual.size() ? actual.get(block) : null;
      if (e == null || !e.equals(a)) {
        differing++;
        if (first < 0) {
          first = block;
        }
      }
    }
    if (first < 0) {
      return "";
    }
    StringBuilder report = new StringBuilder();
    report
      .append(differing)
      .append(" of ")
      .append(max)
      .append(" blocks of ")
      .append(BLOCK_SIZE)
      .append(" differ in ")
      .append(name)
      .append("\nfirst differing block ")
      .append(first)
      .append(" (cases ")
      .append(first * BLOCK_SIZE)
      .append("..")
      .append(Math.min(size, (first + 1) * BLOCK_SIZE) - 1)
      .append(")\nexpected sha256 ")
      .append(first < expected.size() ? expected.get(first) : "<none>")
      .append("\nactual   sha256 ")
      .append(first < actual.size() ? actual.get(first) : "<none>")
      .append("\nactual outputs (first ")
      .append(MAX_REPORTED_ITEMS)
      .append(" cases of the block):");
    int end = Math.min(
      Math.min(size, (first + 1) * BLOCK_SIZE),
      first * BLOCK_SIZE + MAX_REPORTED_ITEMS
    );
    for (int i = first * BLOCK_SIZE; i < end; i++) {
      Case c = items.apply(i);
      report
        .append("\n  #")
        .append(i)
        .append(' ')
        .append(clip(escape(c.label())))
        .append(" -> ")
        .append(clip(escape(rawOutcome(writer, c.value()))));
    }
    return report.toString();
  }

  private static String clip(String text) {
    if (text == null) {
      return "<none>";
    }
    return text.length() <= MAX_CLIPPED_LENGTH
      ? text
      : text.substring(0, MAX_CLIPPED_LENGTH) + "...[" + text.length() + " chars]";
  }
}
