package com.hubspot.jinjava.lib.filter.yaml;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Function;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import tools.jackson.dataformat.yaml.util.StringQuotingChecker;

/**
 * Re-emits YAML written by Jackson 3 (snakeyaml-engine) the way Jackson 2 wrote it (SnakeYAML
 * 2.4). The text is parsed into snakeyaml-engine events, the events Jackson 2's generator would
 * have produced are rebuilt from them, and those are emitted with SnakeYAML 2.4's {@link
 * Emitter}, configured like Jackson 2's {@code YAMLGenerator}: not canonical, block flow style,
 * split lines, no document start marker. The SnakeYAML 2.4 emitter escapes U+0085, U+2028 and
 * U+2029 as {@code \N}, {@code \L}, {@code \P} in double quoted scalars, counts them as line breaks
 * (so a key holding one is written in the complex form {@code ? key}) and folds long scalars by
 * its own column count; the engine's emitter does none of this.
 *
 * <p>Rebuilt events: scalar value, tag and style from the parse, except that the style of an
 * untagged key that is not plain becomes plain unless {@link StringQuotingChecker} demands double
 * quotes (the engine may have downgraded a requested plain style); implicit tuple {@code (true,
 * true)} unless the scalar is tagged; block style for collections.
 *
 * <p>The engine's parser is fed a text in which {@link LineBreakMask} has hidden four characters
 * it mishandles, through a reader that avoids a surrogate pair bug, and without its default limit
 * of 3,145,728 code points (an input over that limit would otherwise fail where Jackson 2
 * wrote it).
 *
 * <p>Limits: the output equals Jackson 2's only if the rebuilt events equal the events Jackson 2's
 * generator would have produced, which is what the recorded comparison tests check for the
 * configuration of {@link ToYamlWriter}; custom serializers that write tags, raw values or object
 * ids were not tested. Anchors, aliases, several documents and directives are not handled
 * (that configuration writes none). Any failure is reported as a {@link YamlReemitException}.
 */
final class Jackson2YamlReemitter {

  private static final StringQuotingChecker QUOTING_CHECKER =
    StringQuotingChecker.Default.instance();
  private static final int UNLIMITED_CODE_POINTS = Integer.MAX_VALUE;

  private Jackson2YamlReemitter() {}

  /** Re-emits {@code yaml} with the production mask. */
  static String reemit(String yaml) {
    return reemit(yaml, LineBreakMask::new);
  }

  /** Test seam: the mask is a parameter so that a weaker mask can be shown to fail. */
  static String reemit(String yaml, Function<String, ? extends TextMask> masking) {
    try {
      StringWriter out = new StringWriter();
      Emitter emitter = new Emitter(out, jackson2Options());
      Deque<Collection> collections = new ArrayDeque<>();
      TextMask mask = masking.apply(yaml);
      LoadSettings settings = LoadSettings
        .builder()
        .setCodePointLimit(UNLIMITED_CODE_POINTS)
        .build();
      for (Event event : new Parse(settings)
        .parseReader(LineBreakMask.reader(mask.masked()))) {
        emit(emitter, event, collections, mask);
      }
      return out.toString();
    } catch (RuntimeException | IOException e) {
      throw new YamlReemitException("Could not re-emit YAML as Jackson 2 writes it", e);
    }
  }

  static DumperOptions jackson2Options() {
    DumperOptions options = new DumperOptions();
    options.setCanonical(false);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setSplitLines(true);
    return options;
  }

  private static void emit(
    Emitter emitter,
    Event event,
    Deque<Collection> collections,
    TextMask mask
  ) throws IOException {
    switch (event.getEventId()) {
      case StreamStart:
        emitter.emit(new StreamStartEvent(null, null));
        break;
      case StreamEnd:
        emitter.emit(new StreamEndEvent(null, null));
        break;
      case DocumentStart:
        emitter.emit(
          new DocumentStartEvent(null, null, false, null, Collections.emptyMap())
        );
        break;
      case DocumentEnd:
        emitter.emit(new DocumentEndEvent(null, null, false));
        break;
      case MappingStart:
        registerChild(collections);
        collections.push(new Collection(true));
        Optional<String> mappingTag =
          ((org.snakeyaml.engine.v2.events.MappingStartEvent) event).getTag();
        emitter.emit(
          new MappingStartEvent(
            null,
            mappingTag.orElse(null),
            mappingTag.isEmpty(),
            null,
            null,
            DumperOptions.FlowStyle.BLOCK
          )
        );
        break;
      case SequenceStart:
        registerChild(collections);
        collections.push(new Collection(false));
        Optional<String> sequenceTag =
          ((org.snakeyaml.engine.v2.events.SequenceStartEvent) event).getTag();
        emitter.emit(
          new SequenceStartEvent(
            null,
            sequenceTag.orElse(null),
            sequenceTag.isEmpty(),
            null,
            null,
            DumperOptions.FlowStyle.BLOCK
          )
        );
        break;
      case MappingEnd:
        collections.pop();
        emitter.emit(new MappingEndEvent(null, null));
        break;
      case SequenceEnd:
        collections.pop();
        emitter.emit(new SequenceEndEvent(null, null));
        break;
      case Scalar:
        emitScalar(emitter, (ScalarEvent) event, registerChild(collections), mask);
        break;
      default:
        throw new IllegalStateException("Unsupported YAML event " + event);
    }
  }

  private static void emitScalar(
    Emitter emitter,
    ScalarEvent scalar,
    boolean isKey,
    TextMask mask
  ) throws IOException {
    ScalarStyle style = scalar.getScalarStyle();
    if (isKey && scalar.getTag().isEmpty() && style != ScalarStyle.PLAIN) {
      style =
        QUOTING_CHECKER.needToQuoteName(scalar.getValue())
          ? ScalarStyle.DOUBLE_QUOTED
          : ScalarStyle.PLAIN;
    }
    boolean tagged = scalar.getTag().isPresent();
    emitter.emit(
      new org.yaml.snakeyaml.events.ScalarEvent(
        null,
        scalar.getTag().orElse(null),
        new ImplicitTuple(!tagged, !tagged),
        mask.unmask(scalar.getValue()),
        null,
        null,
        toSnakeYamlStyle(style)
      )
    );
  }

  private static DumperOptions.ScalarStyle toSnakeYamlStyle(ScalarStyle style) {
    switch (style) {
      case PLAIN:
        return DumperOptions.ScalarStyle.PLAIN;
      case SINGLE_QUOTED:
        return DumperOptions.ScalarStyle.SINGLE_QUOTED;
      case DOUBLE_QUOTED:
        return DumperOptions.ScalarStyle.DOUBLE_QUOTED;
      case LITERAL:
        return DumperOptions.ScalarStyle.LITERAL;
      case FOLDED:
        return DumperOptions.ScalarStyle.FOLDED;
      default:
        throw new IllegalStateException("Unsupported scalar style " + style);
    }
  }

  /** Counts one more child in the enclosing collection; true if that child is a mapping key. */
  private static boolean registerChild(Deque<Collection> collections) {
    Collection enclosing = collections.peek();
    if (enclosing == null) {
      return false;
    }
    boolean isKey = enclosing.mapping && enclosing.children % 2 == 0;
    enclosing.children++;
    return isKey;
  }

  /** A mapping or sequence being read, and how many children it has had so far. */
  private static final class Collection {

    private final boolean mapping;
    private int children;

    private Collection(boolean mapping) {
      this.mapping = mapping;
    }
  }
}
