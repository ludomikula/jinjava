package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.Test;

public class LineBreakMaskTest {

  private static final String NEL = "\u0085";
  private static final String LS = "\u2028";
  private static final String PS = "\u2029";
  private static final String BOM = "\ufeff";
  private static final String MARKER = "\ue000";
  private static final String ALL_MASKED =
    "a" + NEL + "b" + LS + "c" + PS + "d" + BOM + "e";
  private static final int EXPECTED_CANDIDATES = 1_111_799;
  private static final int FIRST_CANDIDATE = 0xE000;
  private static final int LAST_BMP_PRIVATE_USE = 0xF8FF;
  private static final int FIRST_PLANE_15_CANDIDATE = 0xF0000;
  private static final int[] TINY_POOL = { 0xE000, 0xE001, 0xE002 };
  private static final String TINY_ALPHABET =
    "\ue000\ue001\ue002NLPBa \u0085\u2028\u2029\ufeff";
  private static final int TINY_POOL_TEXTS = 20_000;
  private static final int TINY_POOL_SEED = 77;
  private static final int TINY_POOL_MAX_LENGTH = 14;
  private static final int CHUNK = 1024;
  private static final int[] CODE_POINTS_ABSENT_FROM_CANDIDATES = {
    0xFFFFE,
    0xFFFFF,
    0x10FFFE,
    0x10FFFF,
  };

  private static void assertNoMaskedCharacters(String text) {
    for (int codePoint : LineBreakMask.MASKED_CODE_POINTS) {
      assertThat(text.indexOf(codePoint)).as("U+%04X", codePoint).isNegative();
    }
  }

  @Test
  public void itLeavesTextWithoutMaskedCharactersUnchanged() {
    LineBreakMask mask = new LineBreakMask("k: \"v\"\n");
    assertThat(mask.masked()).isEqualTo("k: \"v\"\n");
    assertThat(mask.unmask("anything " + MARKER + "N"))
      .isEqualTo("anything " + MARKER + "N");
  }

  @Test
  public void itHidesAllFourCharactersAndRestoresThem() {
    LineBreakMask mask = new LineBreakMask(ALL_MASKED);
    assertNoMaskedCharacters(mask.masked());
    assertThat(mask.masked()).hasSize(ALL_MASKED.length() + 4 * 2 - 4);
    assertThat(mask.unmask(mask.masked())).isEqualTo(ALL_MASKED);
  }

  @Test
  public void itTagsEachCharacterWithItsLetter() {
    LineBreakMask mask = new LineBreakMask(ALL_MASKED);
    assertThat(mask.masked())
      .isEqualTo("a" + MARKER + "Nb" + MARKER + "Lc" + MARKER + "Pd" + MARKER + "Be");
  }

  @Test
  public void itChoosesTheFirstBmpPrivateUseCodePointAbsentFromTheText() {
    assertThat(new LineBreakMask("x" + NEL).masked()).isEqualTo("x" + MARKER + "N");
    assertThat(new LineBreakMask("\ue000\ue001" + NEL).masked())
      .isEqualTo("\ue000\ue001\ue002N");
  }

  @Test
  public void itCountsCodePointsAHexEscapeWouldProduceAsPresent() {
    assertThat(new LineBreakMask("\\uE000 " + NEL).masked()).isEqualTo("\\uE000 \ue001N");
    assertThat(new LineBreakMask("\\U0000E000 " + NEL).masked())
      .isEqualTo("\\U0000E000 \ue001N");
    assertThat(new LineBreakMask("\\xE0 " + NEL).masked())
      .isEqualTo("\\xE0 " + MARKER + "N");
  }

  @Test
  public void itIgnoresEscapesThatAreNotHex() {
    assertThat(new LineBreakMask("\\uZZZZ \\u12 \\U12 \\x1 \\" + NEL).masked())
      .isEqualTo("\\uZZZZ \\u12 \\U12 \\x1 \\" + MARKER + "N");
  }

  @Test
  public void itMovesOnToPlane15WhenBmpPrivateUseIsUsedUp() {
    String text = range(FIRST_CANDIDATE, LAST_BMP_PRIVATE_USE) + NEL;
    LineBreakMask mask = new LineBreakMask(text);
    assertThat(mask.masked())
      .isEqualTo(
        range(FIRST_CANDIDATE, LAST_BMP_PRIVATE_USE) +
        new String(Character.toChars(FIRST_PLANE_15_CANDIDATE)) +
        "N"
      );
    assertThat(mask.unmask(mask.masked())).isEqualTo(text);
  }

  @Test
  public void itUsesAPairMarkerWhenTheTextHoldsEveryCandidate() {
    String all = new String(LineBreakMask.candidates().toArray(), 0, EXPECTED_CANDIDATES);
    String text = all + NEL + "x" + LS + BOM;
    LineBreakMask mask = new LineBreakMask(text);
    assertNoMaskedCharacters(mask.masked());
    assertThat(mask.unmask(mask.masked())).isEqualTo(text);
    assertThat(mask.masked().length()).isGreaterThan(text.length());
  }

  @Test
  public void itUsesAPairMarkerWhenTheCandidatePoolIsUsedUp() {
    String text = "\ue000\ue001\ue002" + NEL + "a" + PS;
    LineBreakMask mask = new LineBreakMask(text, TINY_POOL, null);
    assertNoMaskedCharacters(mask.masked());
    assertThat(mask.unmask(mask.masked())).isEqualTo(text);
    assertThat(mask.masked()).isEqualTo("\ue000\ue001\ue002\ue000\ue002Na\ue000\ue002P");
  }

  @Test
  public void itFailsWhenNoMarkerPairIsAbsentFromTheText() {
    String everyPair = "\ue000\ue001\ue002\ue000\ue002\ue001\ue000" + NEL;
    assertThatThrownBy(() -> new LineBreakMask(everyPair, TINY_POOL, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No marker pair is absent from the text");
  }

  @Test
  public void itRoundTripsRandomTextsOverATinyCandidatePool() {
    SplittableRandom random = new SplittableRandom(TINY_POOL_SEED);
    int masked = 0;
    int noPair = 0;
    for (int i = 0; i < TINY_POOL_TEXTS; i++) {
      StringBuilder sb = new StringBuilder();
      int length = random.nextInt(TINY_POOL_MAX_LENGTH);
      for (int j = 0; j < length; j++) {
        sb.append(TINY_ALPHABET.charAt(random.nextInt(TINY_ALPHABET.length())));
      }
      String text = sb.toString();
      try {
        LineBreakMask mask = new LineBreakMask(text, TINY_POOL, null);
        assertThat(mask.unmask(mask.masked())).as(text).isEqualTo(text);
        assertNoMaskedCharacters(mask.masked());
        masked++;
      } catch (IllegalStateException e) {
        assertThat(e).hasMessage("No marker pair is absent from the text");
        noPair++;
      }
    }
    assertThat(masked).isGreaterThan(noPair);
  }

  @Test
  public void itUsesAForcedMarker() {
    LineBreakMask mask = new LineBreakMask("a" + NEL, null, "\ue123");
    assertThat(mask.masked()).isEqualTo("a\ue123N");
    assertThat(mask.unmask("x\ue123Ly")).isEqualTo("x" + LS + "y");
  }

  @Test
  public void itRejectsAMarkerWithoutATag() {
    LineBreakMask mask = new LineBreakMask("a" + NEL);
    assertThatThrownBy(() -> mask.unmask("a\ue000"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("without a tag");
  }

  @Test
  public void itRejectsAnUnknownTag() {
    LineBreakMask mask = new LineBreakMask("a" + NEL);
    assertThatThrownBy(() -> mask.unmask("a\ue000Z"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unknown token tag Z");
  }

  @Test
  public void itHasTheDocumentedCandidatesInTheDocumentedOrder() {
    int[] candidates = LineBreakMask.candidates().toArray();
    assertThat(candidates).hasSize(EXPECTED_CANDIDATES);
    assertThat(candidates[0]).isEqualTo(FIRST_CANDIDATE);
    assertThat(Arrays.stream(candidates).distinct().count())
      .isEqualTo(EXPECTED_CANDIDATES);
    assertThat(Arrays.stream(candidates).allMatch(YamlWriteCorpus::isUsable)).isTrue();
    assertThat(Arrays.stream(candidates).noneMatch(c -> c < 0x100)).isTrue();
  }

  @Test
  public void itAgreesWithTheCorpusAboutWhichCodePointsAreUsable() {
    for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
      boolean excluded = isAbsentFromCandidates(codePoint);
      assertThat(YamlWriteCorpus.isUsable(codePoint))
        .as("U+%04X", codePoint)
        .isEqualTo(LineBreakMask.isCandidate(codePoint) && !excluded);
    }
  }

  private static boolean isAbsentFromCandidates(int codePoint) {
    for (int absent : CODE_POINTS_ABSENT_FROM_CANDIDATES) {
      if (absent == codePoint) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void itDetectsMaskedCharacters() {
    assertThat(LineBreakMask.containsMasked("plain")).isFalse();
    for (String masked : new String[] { NEL, LS, PS, BOM }) {
      assertThat(LineBreakMask.containsMasked("a" + masked + "b")).isTrue();
    }
  }

  @Test
  public void itNeverEndsAReadChunkOnAHighSurrogate() throws IOException {
    String text = "a".repeat(CHUNK - 1) + "😀" + "b".repeat(CHUNK);
    try (Reader reader = LineBreakMask.reader(text)) {
      char[] buffer = new char[CHUNK];
      int first = reader.read(buffer, 0, CHUNK);
      assertThat(first).isEqualTo(CHUNK - 1);
      StringBuilder all = new StringBuilder().append(buffer, 0, first);
      int read;
      while ((read = reader.read(buffer, 0, CHUNK)) != -1) {
        assertThat(Character.isHighSurrogate(buffer[read - 1])).isFalse();
        all.append(buffer, 0, read);
      }
      assertThat(all.toString()).isEqualTo(text);
    }
  }

  @Test
  public void itReadsOneCharacterAtATimeAndEndsWithMinusOne() throws IOException {
    try (Reader reader = LineBreakMask.reader("a😀")) {
      char[] one = new char[1];
      assertThat(reader.read(one, 0, 1)).isEqualTo(1);
      assertThat(one[0]).isEqualTo('a');
      assertThat(reader.read(one, 0, 1)).isEqualTo(1);
      assertThat(reader.read(one, 0, 1)).isEqualTo(1);
      assertThat(reader.read(one, 0, 1)).isEqualTo(-1);
    }
  }

  private static String range(int first, int last) {
    StringBuilder sb = new StringBuilder();
    for (int c = first; c <= last; c++) {
      sb.appendCodePoint(c);
    }
    return sb.toString();
  }
}
