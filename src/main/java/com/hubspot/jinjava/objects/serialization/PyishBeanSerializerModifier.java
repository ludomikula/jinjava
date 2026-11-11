package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import java.util.Map;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.UnrolledBeanSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;

@Beta
public class PyishBeanSerializerModifier extends ValueSerializerModifier {

  public static final PyishBeanSerializerModifier INSTANCE =
    new PyishBeanSerializerModifier();

  private PyishBeanSerializerModifier() {}

  @Override
  public ValueSerializer<?> modifySerializer(
    SerializationConfig config,
    BeanDescription.Supplier beanDesc,
    ValueSerializer<?> serializer
  ) {
    // Use the PyishSerializer if it extends the PyishSerializable class.
    // For example, a Map implementation could then have custom string serialization.
    if (!(PyishSerializable.class.isAssignableFrom(beanDesc.getBeanClass()))) {
      if (Map.Entry.class.isAssignableFrom(beanDesc.getBeanClass())) {
        return MapEntrySerializer.INSTANCE;
      }
      if (isBeanSerializer(serializer)) {
        return BothCasingBeanSerializer.wrapping(serializer);
      }
      return serializer;
    } else {
      return PyishSerializer.INSTANCE;
    }
  }

  /**
   * Jackson 3 builds an {@link UnrolledBeanSerializer} instead of a {@link BeanSerializer} for
   * beans with at most six properties, and it does not extend {@link BeanSerializer}. Both
   * are plain bean serializers. Not {@code BeanSerializerBase}, the superclass of both, which
   * also covers other bean-like serializers such as {@code BeanAsArraySerializer}.
   */
  static boolean isBeanSerializer(ValueSerializer<?> serializer) {
    return (
      serializer instanceof BeanSerializer || serializer instanceof UnrolledBeanSerializer
    );
  }
}
