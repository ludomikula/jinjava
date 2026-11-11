package com.hubspot.jinjava.lib.filter.yaml;

import com.hubspot.jinjava.objects.serialization.Jackson2ParitySettings;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * The Jackson 3 YAML mapper whose output, before {@link LineBreakGuard} and {@link
 * Jackson2YamlReemitter}, is closest to what Jackson 2's {@code YAMLMapper} wrote for {@code
 * toyaml}: no document start marker, Jackson 2's defaults from {@code configureForJackson2()}, the
 * settings of {@link Jackson2ParitySettings} (stream limits, accessor naming, {@code java.time}
 * rejection, {@code Locale} as {@code toString()}), and the rejection of {@code
 * java.util.Optional}, {@code OptionalInt}, {@code OptionalLong} and {@code OptionalDouble}.
 * Jackson 2 without its jdk8 module could not write them; Jackson 3 writes them out of the box.
 *
 * <p>Limits: what the mapper writes differently from Jackson 2 for U+0085, U+2028 and U+2029 is
 * left to the re-emit step. Types beyond those exercised by the recorded comparison tests were
 * not compared.
 */
final class Jackson2ParityYamlMapper {

  private static final String OPTIONAL_MODULE_NAME =
    "JinjavaJackson2YamlOptionalRejection";
  private static final String OPTIONAL_CLASS_PREFIX = "java.util.Optional";

  private Jackson2ParityYamlMapper() {}

  static YAMLMapper create() {
    YAMLMapper.Builder builder = YAMLMapper
      .builder(
        Jackson2ParitySettings.applyStreamConstraints(YAMLFactory.builder()).build()
      )
      .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
      .configureForJackson2();
    return Jackson2ParitySettings.applyTo(builder).addModule(optionalRejection()).build();
  }

  private static JacksonModule optionalRejection() {
    return new SimpleModule(OPTIONAL_MODULE_NAME)
      .setSerializerModifier(new OptionalRejectingModifier());
  }

  private static final class OptionalRejectingModifier extends ValueSerializerModifier {

    @Override
    public ValueSerializer<?> modifySerializer(
      SerializationConfig config,
      BeanDescription.Supplier beanDesc,
      ValueSerializer<?> serializer
    ) {
      Class<?> beanClass = beanDesc.getBeanClass();
      return beanClass.getName().startsWith(OPTIONAL_CLASS_PREFIX)
        ? new OptionalRejectingSerializer(beanClass)
        : serializer;
    }
  }

  private static final class OptionalRejectingSerializer extends StdSerializer<Object> {

    private final Class<?> rejectedType;

    private OptionalRejectingSerializer(Class<?> rejectedType) {
      super(Object.class);
      this.rejectedType = rejectedType;
    }

    @Override
    public void serialize(Object value, JsonGenerator g, SerializationContext ctxt) {
      ctxt.reportBadDefinition(
        rejectedType,
        "Optional types are not supported, as in Jackson 2 without jdk8",
        null
      );
    }
  }
}
