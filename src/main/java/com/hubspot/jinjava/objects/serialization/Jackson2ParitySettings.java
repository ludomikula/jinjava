package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import java.util.Locale;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.TSFBuilder;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AccessorNamingStrategy;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Settings that make a Jackson 3 mapper write and read what Jinjava's Jackson 2 mappers did, so
 * that template output does not change with the Jackson generation. Jackson 3's own
 * {@link MapperBuilder#configureForJackson2()} and
 * {@link tools.jackson.core.json.JsonFactory#builderWithJackson2Defaults()} cover most defaults
 * but not all (their Javadoc says so); the settings here fill the gaps found by comparing
 * Jackson 2.20.1 and Jackson 3.1.8 output:
 * <ol>
 *   <li>{@link #applyStreamConstraints(TSFBuilder)}: Jackson 2's limits of
 *       {@value #MAX_NESTING_DEPTH} levels of nesting and {@value #MAX_STRING_LENGTH}
 *       characters per string (Jackson 3 defaults: 500 and 100,000,000).</li>
 *   <li>{@link #applyTo(MapperBuilder)}, accessor naming: {@code getURL()} is the property
 *       {@code url} and {@code getXValue()} is {@code xvalue}, as in Jackson 2 (Jackson 3 names
 *       them {@code URL} and {@code XValue}).</li>
 *   <li>{@link #applyTo(MapperBuilder)}, {@code java.time} types are rejected: without the jsr310
 *       module Jackson 2 could not write them, and callers relied on that failure (Jackson 3
 *       writes them out of the box).</li>
 *   <li>{@link #applyTo(MapperBuilder)}, {@link Locale} is written with {@code toString()}
 *       ({@code en_US}); Jackson 3 writes the language tag ({@code en-US}).</li>
 * </ol>
 * {@link #jsonMapperBuilder()} combines these with {@code configureForJackson2()} for JSON.
 *
 * <p>Not covered: {@code NullValue} needs no setting, it writes {@code null} through its
 * {@code @JsonValue} method. {@code configureForJackson2()} is documented as incomplete, so
 * parity is established for the value types Jinjava's tests exercise, not for arbitrary
 * objects. Jackson 2 databind annotations and Jackson 2 modules on user classes are not read by
 * Jackson 3 and cannot be emulated here.
 */
@Beta
public final class Jackson2ParitySettings {

  /** Jackson 2's maximum nesting depth when reading and writing. */
  public static final int MAX_NESTING_DEPTH = 1000;
  /** Jackson 2's maximum length of a string value when reading. */
  public static final int MAX_STRING_LENGTH = 20_000_000;

  private static final String MODULE_NAME = "JinjavaJackson2Parity";
  private static final String JAVA_TIME_PACKAGE_PREFIX = "java.time.";

  private Jackson2ParitySettings() {}

  /**
   * A JSON factory builder with Jackson 2's defaults and Jackson 2's stream constraints.
   */
  public static JsonFactoryBuilder jsonFactoryBuilder() {
    return applyStreamConstraints(JsonFactory.builderWithJackson2Defaults());
  }

  /**
   * A JSON mapper builder that reads and writes like Jackson 2: {@link #jsonFactoryBuilder()},
   * {@code configureForJackson2()} and {@link #applyTo(MapperBuilder)}.
   */
  public static JsonMapper.Builder jsonMapperBuilder() {
    return applyTo(
      JsonMapper.builder(jsonFactoryBuilder().build()).configureForJackson2()
    );
  }

  /**
   * Sets Jackson 2's nesting and string length limits on a stream factory builder, for JSON or
   * any other format.
   */
  public static <B extends TSFBuilder<?, B>> B applyStreamConstraints(B builder) {
    return builder
      .streamReadConstraints(
        StreamReadConstraints
          .builder()
          .maxNestingDepth(MAX_NESTING_DEPTH)
          .maxStringLength(MAX_STRING_LENGTH)
          .build()
      )
      .streamWriteConstraints(
        StreamWriteConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build()
      );
  }

  /**
   * Adds Jackson 2's accessor naming, the {@code java.time} rejection and the {@link Locale}
   * serializer to a mapper builder, for JSON or any other format.
   */
  public static <B extends MapperBuilder<?, B>> B applyTo(B builder) {
    return builder.accessorNaming(new LegacyAccessorNaming()).addModule(parityModule());
  }

  private static JacksonModule parityModule() {
    return new SimpleModule(MODULE_NAME)
      .addSerializer(Locale.class, ToStringSerializer.instance)
      .setSerializerModifier(new JavaTimeRejectingModifier());
  }

  /**
   * Jackson 2's rule for the property name of a getter: the whole leading run of upper case
   * characters is lower cased ({@code getURL} gives {@code url}), where Jackson 3 lower cases only
   * the first character unless the second is also upper case, and keeps the acronym otherwise.
   */
  private static final class LegacyAccessorNaming
    extends DefaultAccessorNamingStrategy.Provider {

    @Override
    public AccessorNamingStrategy forPOJO(
      MapperConfig<?> config,
      AnnotatedClass targetClass
    ) {
      return new DefaultAccessorNamingStrategy(
        config,
        targetClass,
        _setterPrefix,
        _getterPrefix,
        _isGetterPrefix,
        _baseNameValidator
      ) {
        @Override
        protected String stdManglePropertyName(String basename, int offset) {
          return legacyManglePropertyName(basename, offset);
        }
      };
    }
  }

  /**
   * Jackson 2's {@code legacyManglePropertyName}, copied from
   * {@code com.fasterxml.jackson.databind.util.BeanUtil} of Jackson 2.20 (Apache License 2.0,
   * Copyright FasterXML), which Jackson 3 no longer applies by default.
   */
  static String legacyManglePropertyName(String basename, int offset) {
    final int end = basename.length();
    if (end == offset) {
      return null;
    }
    char c = basename.charAt(offset);
    char lower = Character.toLowerCase(c);
    if (c == lower) {
      return basename.substring(offset);
    }
    StringBuilder sb = new StringBuilder(end - offset);
    sb.append(lower);
    for (int i = offset + 1; i < end; ++i) {
      c = basename.charAt(i);
      lower = Character.toLowerCase(c);
      if (c == lower) {
        sb.append(basename, i, end);
        break;
      }
      sb.append(lower);
    }
    return sb.toString();
  }

  private static final class JavaTimeRejectingModifier extends ValueSerializerModifier {

    @Override
    public ValueSerializer<?> modifySerializer(
      SerializationConfig config,
      BeanDescription.Supplier beanDesc,
      ValueSerializer<?> serializer
    ) {
      Class<?> beanClass = beanDesc.getBeanClass();
      return beanClass.getName().startsWith(JAVA_TIME_PACKAGE_PREFIX)
        ? new JavaTimeRejectingSerializer(beanClass)
        : serializer;
    }
  }

  private static final class JavaTimeRejectingSerializer extends StdSerializer<Object> {

    private final Class<?> rejectedType;

    private JavaTimeRejectingSerializer(Class<?> rejectedType) {
      super(Object.class);
      this.rejectedType = rejectedType;
    }

    @Override
    public void serialize(Object value, JsonGenerator g, SerializationContext ctxt) {
      ctxt.reportBadDefinition(
        rejectedType,
        "java.time types are not supported, as in Jackson 2 without jsr310",
        null
      );
    }
  }
}
