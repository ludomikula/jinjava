package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.lib.filter.yaml.YamlWriteCorpus.Case;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Shows that the comparison against the Jackson 2 outcomes can fail, and for what: each test
 * builds a writer without one part of the mechanism and counts the inputs whose outcome differs
 * from {@link ToYamlWriter}'s, which {@link ToYamlRecordedOutcomeTest} has shown equal to Jackson
 * 2's. A part is only needed if taking it out changes outcomes; the expected counts are the
 * numbers measured when the mechanism was chosen (the plan's evidence E4, E4b and E4c).
 */
public class ToYamlNegativeControlTest {

  /** Without the re-emit: the single corpus value and the targeted values with line breaks. */
  static final int CORPUS_DIFFERENCES_WITHOUT_REEMIT = 1;
  static final int TARGETED_DIFFERENCES_WITHOUT_REEMIT = 10_573;
  /** With the first mask: the exhaustion values on which it runs out of mask characters. */
  static final int EXHAUSTION_DIFFERENCES_WITH_LEGACY_MASK = 96;
  /** Without Jackson 2's accessor naming: the beans that have an acronym or a lower case second letter. */
  static final int NAMING_DIFFERENCES_WITHOUT_LEGACY_NAMING = 10;

  private final ToYamlWriter production = new ToYamlWriter();

  private static long differences(
    List<Case> cases,
    Function<Object, String> reference,
    Function<Object, String> ablated
  ) {
    return cases
      .parallelStream()
      .filter(c ->
        !YamlWriteOutcomes
          .outcome(reference, c.value())
          .equals(YamlWriteOutcomes.outcome(ablated, c.value()))
      )
      .count();
  }

  private static YAMLMapper mapperWithoutLegacyNaming() {
    return YAMLMapper
      .builder(YAMLFactory.builder().build())
      .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
      .configureForJackson2()
      .build();
  }

  @Test
  public void itFailsTheCorpusAndTheTargetedSetWithoutTheReemit() {
    ToYamlWriter withoutReemit = new ToYamlWriter(
      Jackson2ParityYamlMapper.create(),
      yaml -> yaml
    );
    assertThat(
      differences(YamlWriteCorpus.corpus(), production::write, withoutReemit::write)
    )
      .isEqualTo(CORPUS_DIFFERENCES_WITHOUT_REEMIT);
    assertThat(
      differences(YamlWriteCorpus.targeted(), production::write, withoutReemit::write)
    )
      .isEqualTo(TARGETED_DIFFERENCES_WITHOUT_REEMIT);
  }

  @Test
  public void itFailsTheExhaustionSetWithTheFirstMask() {
    ToYamlWriter withLegacyMask = new ToYamlWriter(
      Jackson2ParityYamlMapper.create(),
      yaml -> Jackson2YamlReemitter.reemit(yaml, LegacyLineBreakMask::new)
    );
    assertThat(
      differences(YamlWriteCorpus.exhaustion(), production::write, withLegacyMask::write)
    )
      .isEqualTo(EXHAUSTION_DIFFERENCES_WITH_LEGACY_MASK);
  }

  @Test
  public void itFailsTheBeansWithoutJackson2AccessorNaming() {
    ToYamlWriter withoutNaming = new ToYamlWriter(
      mapperWithoutLegacyNaming(),
      Jackson2YamlReemitter::reemit
    );
    assertThat(
      differences(YamlWriteCorpus.namingBeans(), production::write, withoutNaming::write)
    )
      .isEqualTo(NAMING_DIFFERENCES_WITHOUT_LEGACY_NAMING);
  }

  @Test
  public void itPassesEverySetWithTheFullMechanism() {
    ToYamlWriter same = new ToYamlWriter();
    assertThat(differences(YamlWriteCorpus.corpus(), production::write, same::write))
      .isZero();
    assertThat(differences(YamlWriteCorpus.namingBeans(), production::write, same::write))
      .isZero();
  }
}
