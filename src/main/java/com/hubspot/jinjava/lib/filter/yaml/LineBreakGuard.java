package com.hubspot.jinjava.lib.filter.yaml;

/**
 * Decides whether text written by Jackson 3 has to be re-emitted. Jackson 3 writes YAML with
 * snakeyaml-engine, which treats only U+000A as a line break; Jackson 2 wrote it with SnakeYAML
 * 2.4, which also treats U+0085, U+2028 and U+2029 as line breaks. Text holding one of them
 * written raw, or the two characters {@code \N} (what the engine writes for U+0085 inside a double
 * quoted scalar), may therefore differ from what Jackson 2 wrote. Everything else is identical and
 * is returned as written.
 *
 * <p>Limits: this looks at the written text, not at the value. A backslash followed by {@code N}
 * that was written for a literal backslash and N triggers a re-emit that changes nothing.
 * Whether the text is identical when it does not trigger is established by the recorded
 * comparison tests, not proven.
 */
final class LineBreakGuard {

  static final char NEXT_LINE = '\u0085';
  static final char LINE_SEPARATOR = '\u2028';
  static final char PARAGRAPH_SEPARATOR = '\u2029';
  private static final char ESCAPE = '\\';
  private static final char NEXT_LINE_ESCAPE_LETTER = 'N';

  private LineBreakGuard() {}

  static boolean needsReemit(String yaml) {
    int length = yaml.length();
    for (int i = 0; i < length; i++) {
      char c = yaml.charAt(i);
      if (
        c == NEXT_LINE ||
        c == LINE_SEPARATOR ||
        c == PARAGRAPH_SEPARATOR ||
        (c == ESCAPE && i + 1 < length && yaml.charAt(i + 1) == NEXT_LINE_ESCAPE_LETTER)
      ) {
        return true;
      }
    }
    return false;
  }
}
