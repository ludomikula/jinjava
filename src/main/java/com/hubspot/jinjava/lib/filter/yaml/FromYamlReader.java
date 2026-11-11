package com.hubspot.jinjava.lib.filter.yaml;

import com.hubspot.jinjava.objects.serialization.Jackson2ParitySettings;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

/**
 * Reads a YAML text into maps, lists and scalars for the {@code fromyaml} filter, returning what
 * Jackson 2.20.1's {@code YAMLMapper.readValue(String, Object.class)} returned: a {@code
 * LinkedHashMap}, {@code ArrayList}, {@code String}, {@code Integer}, {@code Long}, {@code
 * BigInteger}, {@code Double}, {@code Boolean}, {@code byte[]} or null, from the first document
 * of the text. A key is the raw text of a scalar, never typed; an alias is the anchor name as a
 * string; a repeated key replaces the value and keeps its first position; tags on mappings and
 * sequences are ignored.
 *
 * <p>The walk over SnakeYAML 2.4's parser events reproduces the behaviour of Jackson 2.20.1's
 * {@code YAMLParser.nextToken} and databind's untyped deserializer (they are the reference; no
 * code of them is copied here). The scalars are typed by {@link Jackson2YamlScalarDecoder}. No
 * Jackson parser class is used.
 *
 * <p>Limits, as in Jackson 2: {@value Jackson2ParitySettings#MAX_NESTING_DEPTH} levels of
 * nesting, {@value Jackson2YamlScalarDecoder#MAX_NUMBER_LENGTH} characters per number, and
 * SnakeYAML's own limit on the length of the text. There is no limit on the length of a string or
 * a key. The whole text is held in memory as a {@link String}.
 *
 * <p>Not covered: reading into types other than {@code Object}, byte or stream input, and
 * strict duplicate key detection. Equality with Jackson 2.20.1 was established on recorded sets
 * (see {@code FromYamlRecordedOutcomeTest}), not proven. Instances have no mutable state and are
 * thread safe.
 */
public final class FromYamlReader {

  private static final String KEY_EXPECTED_MESSAGE =
    "Expected a mapping key (a scalar), got this instead: ";
  private static final String VALUE_EXPECTED_MESSAGE =
    "Expected a value, got this instead: ";
  private static final String UNEXPECTED_SEQUENCE_END_MESSAGE =
    "Unexpected end of a sequence outside a sequence";
  private static final String NO_DOCUMENT_MESSAGE =
    "No YAML document or an unexpected end of the input";
  private static final String NESTING_MESSAGE =
    "Nesting depth (%d) exceeds the maximum allowed (%d)";

  private final Jackson2YamlScalarDecoder scalars;

  public FromYamlReader() {
    this(new Jackson2YamlScalarDecoder());
  }

  /** Test seam: lets a test read with other scalar typing, to show what the decoder is for. */
  FromYamlReader(Jackson2YamlScalarDecoder scalars) {
    this.scalars = scalars;
  }

  /**
   * @throws YamlReadException if the text is not YAML that Jackson 2 could read, or exceeds
   *     Jackson 2's limits
   */
  public Object read(String yaml) {
    try (StringReader input = new StringReader(yaml)) {
      return readFirstNode(new ParserImpl(new StreamReader(input), new LoaderOptions()));
    }
  }

  /**
   * Walks the events up to the end of the first root node and no further, like {@code
   * readValue}, so a later document is never scanned. Iterative, so deep nesting cannot overflow
   * the thread stack.
   */
  private Object readFirstNode(ParserImpl events) {
    Deque<Container> open = new ArrayDeque<>();
    while (true) {
      Event event = nextEvent(events);
      Container parent = open.peek();
      Object value;
      if (parent != null && parent.awaitsKey()) {
        if (event.is(Event.ID.Scalar)) {
          parent.setKey(((ScalarEvent) event).getValue());
          continue;
        }
        if (!event.is(Event.ID.MappingEnd)) {
          throw new YamlReadException(KEY_EXPECTED_MESSAGE + event);
        }
        open.pop();
        value = parent.value();
      } else {
        switch (event.getEventId()) {
          case Scalar:
            value = scalars.decode((ScalarEvent) event);
            break;
          case Alias:
            value = ((AliasEvent) event).getAnchor();
            break;
          case MappingStart:
            open.push(Container.mapping());
            checkDepth(open.size());
            continue;
          case SequenceStart:
            open.push(Container.sequence());
            checkDepth(open.size());
            continue;
          case SequenceEnd:
            if (parent == null || !parent.isSequence()) {
              throw new YamlReadException(UNEXPECTED_SEQUENCE_END_MESSAGE);
            }
            open.pop();
            value = parent.value();
            break;
          case MappingEnd:
            throw new YamlReadException(VALUE_EXPECTED_MESSAGE + event);
          case StreamEnd:
            throw new YamlReadException(NO_DOCUMENT_MESSAGE);
          default:
            continue; // stream and document markers, comments: no value
        }
      }
      Container target = open.peek();
      if (target == null) {
        return value;
      }
      target.add(value);
    }
  }

  /** One event, and no look-ahead: an event Jackson 2 never requested could raise an error it never saw. */
  private static Event nextEvent(ParserImpl events) {
    try {
      return events.getEvent();
    } catch (YAMLException | NumberFormatException e) {
      throw new YamlReadException(e.getMessage(), e);
    }
  }

  private static void checkDepth(int depth) {
    if (depth > Jackson2ParitySettings.MAX_NESTING_DEPTH) {
      throw new YamlReadException(
        String.format(NESTING_MESSAGE, depth, Jackson2ParitySettings.MAX_NESTING_DEPTH)
      );
    }
  }

  /** An open mapping or sequence, and for a mapping the key whose value is awaited. */
  private static final class Container {

    private final Map<String, Object> mapping;
    private final List<Object> sequence;
    private String key;

    private Container(Map<String, Object> mapping, List<Object> sequence) {
      this.mapping = mapping;
      this.sequence = sequence;
    }

    static Container mapping() {
      return new Container(new LinkedHashMap<>(), null);
    }

    static Container sequence() {
      return new Container(null, new ArrayList<>());
    }

    boolean isSequence() {
      return sequence != null;
    }

    boolean awaitsKey() {
      return mapping != null && key == null;
    }

    void setKey(String key) {
      this.key = key;
    }

    void add(Object value) {
      if (sequence != null) {
        sequence.add(value);
        return;
      }
      mapping.put(key, value);
      key = null;
    }

    Object value() {
      return sequence != null ? sequence : mapping;
    }
  }
}
