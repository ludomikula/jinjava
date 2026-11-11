package com.hubspot.jinjava.objects.serialization;

import com.hubspot.jinjava.lib.filter.AllowSnakeCaseFilter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class BothCasingBeanSerializer<T> extends ValueSerializer<T> {

  private final ValueSerializer<T> orignalSerializer;

  private BothCasingBeanSerializer(ValueSerializer<T> serializer) {
    this.orignalSerializer = serializer;
  }

  public static <T> BothCasingBeanSerializer<T> wrapping(ValueSerializer<T> serializer) {
    return new BothCasingBeanSerializer<>(serializer);
  }

  @Override
  public void serialize(
    T value,
    JsonGenerator gen,
    SerializationContext serializationContext
  ) {
    if (
      Boolean.TRUE.equals(
        serializationContext.getAttribute(PyishObjectMapper.ALLOW_SNAKE_CASE_ATTRIBUTE)
      )
    ) {
      // if it's directly for output, then we don't want to add the additional filter characters,
      // as doing so would make the "|allow_snake_case" appear in the final output.
      StringBuilder sb = new StringBuilder();
      sb
        .append(PyishSerializable.writeValueAsString(value))
        .append('|')
        .append(AllowSnakeCaseFilter.NAME);
      gen.writeRawValue(sb.toString());
    } else {
      orignalSerializer.serialize(value, gen, serializationContext);
    }
  }
}
