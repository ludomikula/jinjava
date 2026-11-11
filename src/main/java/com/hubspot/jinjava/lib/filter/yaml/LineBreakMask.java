package com.hubspot.jinjava.lib.filter.yaml;

import java.io.Reader;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.snakeyaml.engine.v2.scanner.StreamReader;

/**
 * Hides U+0085, U+2028, U+2029 and U+FEFF from snakeyaml-engine 3.0.1's parser, which mishandles
 * them in text its own emitter wrote: a key starting with a raw U+0085 or U+FEFF raises a {@code
 * ParserException}, and a raw U+0085 inside a single quoted scalar is dropped.
 *
 * <p>Each masked character is replaced by a token: one <em>marker</em>, a code point (or, if the
 * text uses all of them, a pair of code points) that occurs nowhere in the text, followed by an
 * ASCII tag letter naming the character. Because the marker is absent from the text, every
 * marker in the masked text starts a token and {@link #unmask(String)} is exact. The marker is
 * chosen from the printable code points above U+00FF (excluding the four masked ones), in the
 * order BMP private use, planes 15 and 16 private use, U+F900..U+FFFD, planes 1 to 14, U+0100..
 * U+D7FF. Code points a hex escape in the text (the 2, 4 and 8 digit forms) would produce
 * count as present. A pair marker M1 M2 (M1 different from M2, the pair absent from the text as a
 * substring) is used only if the text holds every single candidate; one always exists, because
 * the candidates (about 1.1 million) squared exceed the length a Java String can have.
 *
 * <p>The re-emitter only reads scalar values, styles and tags from the parse, never positions,
 * so a token may be longer than the character it replaces. A masked simple key stays below the
 * scanner's 1024 character limit: the emitter writes simple keys only below 128 characters, and a
 * token is at most three characters.
 *
 * <p>Limits: the argument assumes text written by the snakeyaml-engine emitter. Escapes other
 * than the hex forms that could produce a candidate were not scanned for. Pair markers were
 * exercised by texts holding all candidates and by a three candidate test pool, not by arbitrary
 * input.
 */
final class LineBreakMask implements TextMask {

  private static final int BYTE_ORDER_MARK = 0xFEFF;

  static final int[] MASKED_CODE_POINTS = {
    LineBreakGuard.NEXT_LINE,
    LineBreakGuard.LINE_SEPARATOR,
    LineBreakGuard.PARAGRAPH_SEPARATOR,
    BYTE_ORDER_MARK,
  };

  private static final String TAGS = "NLPB";
  private static final int MAX_CODE_POINT = Character.MAX_CODE_POINT;
  private static final int FIRST_CANDIDATE = 0x100;
  private static final int HEX_ESCAPE_DIGITS = 2;
  private static final int UNICODE_ESCAPE_DIGITS = 4;
  private static final int WIDE_ESCAPE_DIGITS = 8;
  private static final int HEX_RADIX = 16;
  private static final int[][] CANDIDATE_TIERS = {
    { 0xE000, 0xF8FF },
    { 0xF0000, 0xFFFFD },
    { 0x100000, 0x10FFFD },
    { 0xF900, 0xFFFD },
    { 0x10000, 0xEFFFF },
    { FIRST_CANDIDATE, 0xD7FF },
  };

  private final String masked;
  private final String marker;

  LineBreakMask(String yaml) {
    this(yaml, null, null);
  }

  /**
   * Test seam: {@code candidateOverride} replaces the candidate code points, {@code
   * forcedMarker} replaces the choice of marker (it must be absent from the text).
   */
  LineBreakMask(String yaml, int[] candidateOverride, String forcedMarker) {
    if (!containsMasked(yaml)) {
      masked = yaml;
      marker = null;
      return;
    }
    marker = forcedMarker != null ? forcedMarker : chooseMarker(yaml, candidateOverride);
    StringBuilder sb = new StringBuilder(yaml.length() + yaml.length() / 8 + 16);
    int length = yaml.length();
    for (int i = 0; i < length;) {
      int codePoint = yaml.codePointAt(i);
      int index = indexOfMasked(codePoint);
      if (index < 0) {
        sb.appendCodePoint(codePoint);
      } else {
        sb.append(marker).append(TAGS.charAt(index));
      }
      i += Character.charCount(codePoint);
    }
    masked = sb.toString();
  }

  @Override
  public String masked() {
    return masked;
  }

  @Override
  public String unmask(String scalar) {
    if (marker == null) {
      return scalar;
    }
    int at = scalar.indexOf(marker);
    if (at < 0) {
      return scalar;
    }
    StringBuilder sb = new StringBuilder(scalar.length());
    int last = 0;
    while (at >= 0) {
      int tag = at + marker.length();
      if (tag >= scalar.length()) {
        throw new IllegalStateException("Marker at the end of a scalar, without a tag");
      }
      int index = TAGS.indexOf(scalar.charAt(tag));
      if (index < 0) {
        throw new IllegalStateException("Unknown token tag " + scalar.charAt(tag));
      }
      sb.append(scalar, last, at).appendCodePoint(MASKED_CODE_POINTS[index]);
      last = tag + 1;
      at = scalar.indexOf(marker, last);
    }
    return sb.append(scalar, last, scalar.length()).toString();
  }

  /**
   * A reader over the text that never ends a chunk on a high surrogate. snakeyaml-engine 3.0.1's
   * {@code StreamReader.extendIfTrailingHighSurrogate} throws {@code IndexOutOfBoundsException}
   * when a surrogate pair straddles the end of a read chunk; this reader keeps that code path from
   * being entered.
   */
  static Reader reader(String text) {
    return new Reader() {
      private int position = 0;

      @Override
      public int read(char[] buffer, int offset, int length) {
        if (position >= text.length()) {
          return -1;
        }
        int count = Math.min(length, text.length() - position);
        if (
          count > 1 &&
          position + count < text.length() &&
          Character.isHighSurrogate(text.charAt(position + count - 1))
        ) {
          count--;
        }
        text.getChars(position, position + count, buffer, offset);
        position += count;
        return count;
      }

      @Override
      public void close() {
        // nothing to release, the text is a String
      }
    };
  }

  static boolean containsMasked(String yaml) {
    for (int codePoint : MASKED_CODE_POINTS) {
      if (yaml.indexOf(codePoint) >= 0) {
        return true;
      }
    }
    return false;
  }

  /** Printable for the engine's reader, at or above U+0100, and not one of the masked code points. */
  static boolean isCandidate(int codePoint) {
    return (
      codePoint >= FIRST_CANDIDATE &&
      codePoint <= MAX_CODE_POINT &&
      (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE) &&
      indexOfMasked(codePoint) < 0 &&
      StreamReader.isPrintable(codePoint)
    );
  }

  /** The candidate marker code points, in the order they are tried. */
  static IntStream candidates() {
    IntStream stream = IntStream.empty();
    for (int[] tier : CANDIDATE_TIERS) {
      stream =
        IntStream.concat(
          stream,
          IntStream.rangeClosed(tier[0], tier[1]).filter(LineBreakMask::isCandidate)
        );
    }
    return stream;
  }

  private static int indexOfMasked(int codePoint) {
    for (int i = 0; i < MASKED_CODE_POINTS.length; i++) {
      if (MASKED_CODE_POINTS[i] == codePoint) {
        return i;
      }
    }
    return -1;
  }

  private static IntStream candidates(int[] override) {
    return override != null ? Arrays.stream(override) : candidates();
  }

  private static String chooseMarker(String yaml, int[] candidateOverride) {
    BitSet used = new BitSet(MAX_CODE_POINT + 1);
    yaml.codePoints().forEach(used::set);
    markHexEscapes(yaml, used);
    var single = candidates(candidateOverride).filter(c -> !used.get(c)).findFirst();
    return single.isPresent()
      ? new String(Character.toChars(single.getAsInt()))
      : choosePair(yaml, candidateOverride);
  }

  /** Marks the code points a hex escape in the text (the 2, 4 and 8 digit forms) would produce. */
  private static void markHexEscapes(String yaml, BitSet used) {
    for (int i = 0; i + 1 < yaml.length(); i++) {
      if (yaml.charAt(i) != '\\') {
        continue;
      }
      int digits = escapeDigits(yaml.charAt(i + 1));
      if (digits == 0 || i + 2 + digits > yaml.length()) {
        continue;
      }
      try {
        int codePoint = Integer.parseInt(
          yaml.substring(i + 2, i + 2 + digits),
          HEX_RADIX
        );
        if (codePoint >= 0 && codePoint <= MAX_CODE_POINT) {
          used.set(codePoint);
        }
      } catch (NumberFormatException e) {
        // not an escape, nothing is produced
      }
    }
  }

  private static int escapeDigits(char letter) {
    switch (letter) {
      case 'x':
        return HEX_ESCAPE_DIGITS;
      case 'u':
        return UNICODE_ESCAPE_DIGITS;
      case 'U':
        return WIDE_ESCAPE_DIGITS;
      default:
        return 0;
    }
  }

  /** Tries the first code points in order of increasing frequency in the text. */
  private static String choosePair(String yaml, int[] candidateOverride) {
    int[] candidates = candidates(candidateOverride).toArray();
    int[] count = new int[MAX_CODE_POINT + 1];
    yaml.codePoints().forEach(c -> count[c]++);
    long[] byFrequency = new long[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      byFrequency[i] = ((long) count[candidates[i]] << Integer.SIZE) | candidates[i];
    }
    Arrays.sort(byFrequency);
    int[] codePoints = yaml.codePoints().toArray();
    for (long entry : byFrequency) {
      int first = (int) entry;
      Set<Integer> followers = new HashSet<>();
      for (int i = 0; i + 1 < codePoints.length; i++) {
        if (codePoints[i] == first) {
          followers.add(codePoints[i + 1]);
        }
      }
      var second = Arrays
        .stream(candidates)
        .filter(c -> c != first && !followers.contains(c))
        .findFirst();
      if (second.isPresent()) {
        return (
          new String(Character.toChars(first)) +
          new String(Character.toChars(second.getAsInt()))
        );
      }
    }
    throw new IllegalStateException("No marker pair is absent from the text");
  }
}
