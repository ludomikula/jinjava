package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import java.util.Objects;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Writes an object the way Jinjava prints it: a {@link PyishSerializable} writes itself, and
 * anything else is written as a boolean, a number or a string depending on its text.
 *
 * <p>Jackson 2 let this serializer switch the generator to {@link PyishPrettyPrinter} in the
 * middle of a document; Jackson 3 generators cannot change their pretty printer. Jinjava's own
 * writers ({@link PyishObjectMapper#PYISH_OBJECT_WRITER},
 * {@link PyishObjectMapper#SNAKE_CASE_PYISH_OBJECT_WRITER}, {@link PyishSerializable#SELF_WRITER})
 * are configured with the printer, so their output is unchanged. A mapper that only registers
 * this serializer, for example for {@code PyishSerializable.class}, no longer gets the printer
 * and has to call {@code writer().with(PyishPrettyPrinter.INSTANCE)} itself.
 */
@Beta
public class PyishSerializer extends ValueSerializer<Object> {

  public static final PyishSerializer INSTANCE = new PyishSerializer();

  private PyishSerializer() {}

  @Override
  public void serialize(
    Object object,
    JsonGenerator jsonGenerator,
    SerializationContext serializationContext
  ) {
    jsonGenerator.setCharacterEscapes(PyishCharacterEscapes.INSTANCE);
    String string;
    Object wrappedObject = JinjavaInterpreter
      .getCurrentMaybe()
      .map(interpreter -> interpreter.wrap(object))
      .orElse(object);
    if (wrappedObject instanceof PyishSerializable) {
      ((PyishSerializable) wrappedObject).writePyishSelf(
          jsonGenerator,
          serializationContext
        );
    } else if (wrappedObject instanceof Boolean) {
      jsonGenerator.writeBoolean((Boolean) wrappedObject);
    } else if (wrappedObject instanceof Number) {
      jsonGenerator.writeNumber(wrappedObject.toString());
    } else if (wrappedObject instanceof String) {
      jsonGenerator.writeString((String) wrappedObject);
    } else {
      string = Objects.toString(wrappedObject, "");
      try {
        double number = Double.parseDouble(string);
        if (
          string.equals(String.valueOf(number)) ||
          string.equals(String.valueOf((long) number))
        ) {
          jsonGenerator.writeNumber(string);
        } else {
          jsonGenerator.writeString(string);
        }
      } catch (NumberFormatException e) {
        if ("true".equalsIgnoreCase(string) || "false".equalsIgnoreCase(string)) {
          jsonGenerator.writeBoolean(Boolean.parseBoolean(string));
        } else {
          jsonGenerator.writeString(string);
        }
      }
    }
  }
}
