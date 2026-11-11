package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.objects.PyWrapper;
import com.hubspot.jinjava.util.LengthLimitingStringBuilder;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;

@Beta
public interface PyishSerializable extends PyWrapper {
  ObjectWriter SELF_WRITER = PyishObjectMapper.pyishWriter(
    PyishObjectMapper.pyishMapperBuilder().build()
  );

  /**
   * Allows for a class to append the custom string representation in Jinjava.
   * This method will be used by {@link #writePyishSelf(JsonGenerator, SerializationContext)}
   * to specify what will be written to the json generator.
   * <p>
   * @param appendable Appendable to append the pyish string representation to.
   * @return The same appendable with an appended result
   */
  @SuppressWarnings("unchecked")
  default <T extends Appendable & CharSequence> T appendPyishString(T appendable)
    throws IOException {
    return (T) appendable.append(writeValueAsString(this));
  }

  /**
   * Allows for a class to specify how its pyish string representation will
   * be written to the json generator.
   * <p>
   * If the object's serialization can be broken up into multiple jsonGenerator writes,
   * then this method can be overridden to do so instead of a single call to
   * {@link JsonGenerator#writeRawValue(String)}.
   * <p>
   * Jackson 3 methods do not declare {@link IOException}, so the default implementation reports
   * the {@link IOException} of {@link #appendPyishString(Appendable)} as a
   * {@link JacksonIOException}.
   * @param jsonGenerator The JsonGenerator to write to.
   * @param serializationContext Provides default value serialization and attributes stored on the ObjectWriter if needed.
   */
  default void writePyishSelf(
    JsonGenerator jsonGenerator,
    SerializationContext serializationContext
  ) {
    AtomicInteger remainingLength = (AtomicInteger) serializationContext.getAttribute(
      LengthLimitingWriter.REMAINING_LENGTH_ATTRIBUTE
    );
    try {
      jsonGenerator.writeRawValue(
        appendPyishString(
          remainingLength == null
            ? new StringBuilder()
            : new LengthLimitingStringBuilder(remainingLength.get())
        )
          .toString()
      );
    } catch (IOException e) {
      throw JacksonIOException.construct(e);
    }
  }

  /**
   * Utility method to assist implementations of PyishSerializable in
   * overriding <code>toPyishString()</code>.
   * @param value Value to write to a string
   * @return Pyish string value in JSON format
   */
  static String writeValueAsString(Object value) {
    try {
      return SELF_WRITER.writeValueAsString(value);
    } catch (JacksonException e) {
      Throwable cause = PyishObjectMapper.stripJacksonWrappers(e);
      if (cause instanceof DeferredValueException) {
        throw (DeferredValueException) cause;
      }
      return '\'' + Objects.toString(value) + '\'';
    }
  }
}
