package com.hubspot.jinjava.lib.filter.yaml;

import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Writes a value as YAML on Jackson 3, producing the text Jackson 2's {@code YAMLMapper} wrote for
 * the {@code toyaml} filter. The steps are:
 * <ol>
 *   <li>write with {@link Jackson2ParityYamlMapper};</li>
 *   <li>if {@link LineBreakGuard} finds U+0085, U+2028, U+2029 or {@code \N} in the text,
 *       re-emit it with {@link Jackson2YamlReemitter}, otherwise return it as written.</li>
 * </ol>
 * Instances are immutable and thread safe.
 *
 * <p>Failures: the mapper throws a {@link JacksonException} for values Jackson 2 could not write
 * either. A failed re-emit throws a {@link YamlReemitException} and is logged; it is never
 * answered with the text as the mapper wrote it, because Jackson 2 would have written the value
 * and any fallback would be a difference visible in templates.
 *
 * <p>Limits: output equality with Jackson 2.20.1 was established on recorded sets (see {@code
 * ToYamlRecordedOutcomeTest}), not proven. Jackson 2 databind annotations and modules on user
 * classes are not read by Jackson 3. Custom serializers that write YAML tags, raw values or object
 * ids were not tested.
 */
public final class ToYamlWriter {

  private static final Logger LOG = LoggerFactory.getLogger(ToYamlWriter.class);

  private final YAMLMapper mapper;
  private final UnaryOperator<String> reemitter;

  public ToYamlWriter() {
    this(Jackson2ParityYamlMapper.create(), Jackson2YamlReemitter::reemit);
  }

  /** Test seam: lets a test replace the mapper or the re-emit step, to show what each one is for. */
  ToYamlWriter(YAMLMapper mapper, UnaryOperator<String> reemitter) {
    this.mapper = mapper;
    this.reemitter = reemitter;
  }

  /**
   * @throws JacksonException if the value cannot be written
   * @throws YamlReemitException if the text written needed a re-emit and that failed
   */
  public String write(Object value) {
    String yaml = mapper.writeValueAsString(value);
    if (!LineBreakGuard.needsReemit(yaml)) {
      return yaml;
    }
    try {
      return reemitter.apply(yaml);
    } catch (YamlReemitException e) {
      LOG.error("toyaml could not re-emit the YAML written for a value", e);
      throw e;
    }
  }
}
