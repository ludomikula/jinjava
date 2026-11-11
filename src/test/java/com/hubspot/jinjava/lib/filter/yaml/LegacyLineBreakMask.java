package com.hubspot.jinjava.lib.filter.yaml;

import java.util.BitSet;

/**
 * The first mask, kept as a negative control: it replaces each masked character with the next
 * private use code point that does not occur in the text, but only looks at U+E000..U+F8FF. On a
 * text that already holds those code points it runs out of candidates, continues at U+F900 without
 * checking, and can turn a real character into one of the four masked characters in the output.
 */
final class LegacyLineBreakMask implements TextMask {

  private static final int[] MASKED = LineBreakMask.MASKED_CODE_POINTS;
  private static final int TRACKED_FIRST = 0xE000;
  private static final int TRACKED_LAST = 0xF8FF;

  private final String masked;
  private final int[] replacements = new int[MASKED.length];

  LegacyLineBreakMask(String yaml) {
    BitSet used = new BitSet();
    yaml
      .codePoints()
      .filter(c -> c >= TRACKED_FIRST && c <= TRACKED_LAST)
      .forEach(used::set);
    int next = TRACKED_FIRST;
    for (int i = 0; i < MASKED.length; i++) {
      while (used.get(next)) {
        next++;
      }
      replacements[i] = next++;
    }
    StringBuilder sb = new StringBuilder(yaml.length());
    yaml.codePoints().forEach(c -> sb.appendCodePoint(replace(c)));
    masked = sb.toString();
  }

  private int replace(int codePoint) {
    for (int i = 0; i < MASKED.length; i++) {
      if (codePoint == MASKED[i]) {
        return replacements[i];
      }
    }
    return codePoint;
  }

  @Override
  public String masked() {
    return masked;
  }

  @Override
  public String unmask(String scalar) {
    StringBuilder sb = new StringBuilder(scalar.length());
    scalar
      .codePoints()
      .forEach(c -> {
        int restored = c;
        for (int i = 0; i < replacements.length; i++) {
          if (c == replacements[i]) {
            restored = MASKED[i];
          }
        }
        sb.appendCodePoint(restored);
      });
    return sb.toString();
  }
}
