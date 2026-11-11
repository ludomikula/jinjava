package com.hubspot.jinjava.objects.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hubspot.jinjava.testobjects.PyishObjectMapperTestObjects;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.PlainPyishSerializable;
import com.hubspot.jinjava.testobjects.PyishOutcomeTestObjects.SevenPropertyBean;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.UnrolledBeanSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;

public class PyishBeanSerializerModifierTest {

  /**
   * Serializes as a JSON array. Jackson picks that shape after the modifier ran, so the modifier
   * sees a plain bean serializer, in Jackson 2 as in Jackson 3.
   */
  @JsonFormat(shape = JsonFormat.Shape.ARRAY)
  public static class ArrayShapedBean {

    private final String first = "a";
    private final String second = "b";

    public String getFirst() {
      return first;
    }

    public String getSecond() {
      return second;
    }
  }

  /** The serializers that Jackson built for each class, before the modifier under test ran. */
  private static Map<Class<?>, ValueSerializer<?>> buildSerializers(Class<?>... types) {
    Map<Class<?>, ValueSerializer<?>> built = new HashMap<>();
    JsonMapper mapper = JsonMapper
      .builder()
      .addModule(
        new SimpleModule()
          .setSerializerModifier(
            new ValueSerializerModifier() {
              @Override
              public ValueSerializer<?> modifySerializer(
                SerializationConfig config,
                BeanDescription.Supplier beanDesc,
                ValueSerializer<?> serializer
              ) {
                built.put(beanDesc.getBeanClass(), serializer);
                return serializer;
              }
            }
          )
      )
      .build();
    for (Class<?> type : types) {
      mapper.writeValueAsString(newInstance(type));
    }
    return built;
  }

  private static Object newInstance(Class<?> type) {
    if (type == PyishObjectMapperTestObjects.Foo.class) {
      return new PyishObjectMapperTestObjects.Foo("bar");
    } else if (type == SevenPropertyBean.class) {
      return new SevenPropertyBean();
    }
    return new ArrayShapedBean();
  }

  @Test
  public void itRecognizesJacksonsBeanSerializersOnBothSidesOfTheUnrollLimit() {
    Map<Class<?>, ValueSerializer<?>> built = buildSerializers(
      PyishObjectMapperTestObjects.Foo.class,
      SevenPropertyBean.class
    );
    ValueSerializer<?> small = built.get(PyishObjectMapperTestObjects.Foo.class);
    ValueSerializer<?> large = built.get(SevenPropertyBean.class);
    assertThat(small).isInstanceOf(UnrolledBeanSerializer.class);
    assertThat(small).isNotInstanceOf(BeanSerializer.class);
    assertThat(large).isInstanceOf(BeanSerializer.class);
    assertThat(PyishBeanSerializerModifier.isBeanSerializer(small)).isTrue();
    assertThat(PyishBeanSerializerModifier.isBeanSerializer(large)).isTrue();
  }

  @Test
  public void itRecognizesTheSerializerOfAnArrayShapedBeanBeforeItsShapeIsResolved() {
    ValueSerializer<?> arrayShaped = buildSerializers(ArrayShapedBean.class)
      .get(ArrayShapedBean.class);
    assertThat(PyishBeanSerializerModifier.isBeanSerializer(arrayShaped)).isTrue();
  }

  @Test
  public void itDoesNotRecognizeOtherSerializersAsBeanSerializers() {
    assertThat(PyishBeanSerializerModifier.isBeanSerializer(MapEntrySerializer.INSTANCE))
      .isFalse();
    assertThat(PyishBeanSerializerModifier.isBeanSerializer(PyishSerializer.INSTANCE))
      .isFalse();
  }

  /** Written as it was with Jackson 2.20.1: wrapped like any other bean. */
  @Test
  public void itWrapsArrayShapedBeansLikeAnyBean() {
    assertThat(PyishObjectMapper.getAsPyishString(new ArrayShapedBean()))
      .isEqualTo("['a', 'b']|allow_snake_case");
  }

  @Test
  public void itReplacesMapEntriesAndPyishSerializables() {
    assertThat(
      PyishObjectMapper.getAsPyishString(new AbstractMap.SimpleEntry<>("k", "v"))
    )
      .isEqualTo("fn:map_entry('k', 'v')");
    assertThat(PyishObjectMapper.getAsPyishString(new PlainPyishSerializable()))
      .isEqualTo("ps()");
  }
}
