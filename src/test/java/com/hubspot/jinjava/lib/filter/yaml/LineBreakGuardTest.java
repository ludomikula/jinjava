package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class LineBreakGuardTest {

  @Test
  public void itFlagsEachRawLineBreakCharacter() {
    assertThat(LineBreakGuard.needsReemit("k: a\u0085b\n")).isTrue();
    assertThat(LineBreakGuard.needsReemit("k: a\u2028b\n")).isTrue();
    assertThat(LineBreakGuard.needsReemit("k: a\u2029b\n")).isTrue();
    assertThat(LineBreakGuard.needsReemit("\u0085: 1\n")).isTrue();
  }

  @Test
  public void itFlagsTheEscapeTheEngineWritesForNextLine() {
    assertThat(LineBreakGuard.needsReemit("k: \"a\\Nb\"\n")).isTrue();
    assertThat(LineBreakGuard.needsReemit("k: \"\\N\"\n")).isTrue();
  }

  @Test
  public void itFlagsALiteralBackslashFollowedByNBecauseTheTextCannotTellThemApart() {
    assertThat(LineBreakGuard.needsReemit("k: \"a\\\\Nb\"\n")).isTrue();
  }

  @Test
  public void itLeavesOrdinaryTextAlone() {
    assertThat(LineBreakGuard.needsReemit("")).isFalse();
    assertThat(LineBreakGuard.needsReemit("k: \"v\"\n")).isFalse();
    assertThat(LineBreakGuard.needsReemit("- 1\n- \"two\\n\"\n")).isFalse();
    assertThat(LineBreakGuard.needsReemit("k: \"\\L\\P\"\n")).isFalse();
    assertThat(LineBreakGuard.needsReemit("k: \"trailing backslash\\")).isFalse();
    assertThat(LineBreakGuard.needsReemit("k: \"\uFEFF\u00a0\ud83d\ude00\"\n")).isFalse();
  }
}
