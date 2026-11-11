package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.util.WhitespaceUtils;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Beta
public class PyishObjectMapper {

  public static final ObjectWriter PYISH_OBJECT_WRITER;
  public static final ObjectWriter SNAKE_CASE_PYISH_OBJECT_WRITER;
  public static final String ALLOW_SNAKE_CASE_ATTRIBUTE = "allowSnakeCase";
  private static final int MAX_WRAPPER_DEPTH = 16;

  static {
    PYISH_OBJECT_WRITER = pyishWriter(getPyishObjectMapper(false));
    SNAKE_CASE_PYISH_OBJECT_WRITER = pyishWriter(getPyishObjectMapper(true));
  }

  /**
   * A mapper builder for the pyish string form with Jackson 2's output, see
   * {@link Jackson2ParitySettings}: single quotes around strings, and no module registered
   * for {@link PyishSerializable}, so that a {@link PyishSerializable} is written like a bean.
   */
  static JsonMapper.Builder pyishMapperBuilder() {
    return Jackson2ParitySettings.applyTo(
      JsonMapper
        .builder(Jackson2ParitySettings.jsonFactoryBuilder().quoteChar('\'').build())
        .configureForJackson2()
    );
  }

  /** A writer of the mapper with the pyish pretty printer and character escapes. */
  static ObjectWriter pyishWriter(JsonMapper mapper) {
    return mapper
      .writer()
      .with(PyishPrettyPrinter.INSTANCE)
      .with(PyishCharacterEscapes.INSTANCE);
  }

  private static JsonMapper getPyishObjectMapper(boolean snakeCase) {
    JsonMapper.Builder builder = pyishMapperBuilder()
      .addModule(
        new SimpleModule()
          .setSerializerModifier(PyishBeanSerializerModifier.INSTANCE)
          .addSerializer(PyishSerializable.class, PyishSerializer.INSTANCE)
          .setDefaultNullKeySerializer(new NullKeySerializer())
      );
    if (snakeCase) {
      builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
    return builder.build();
  }

  public static String getAsUnquotedPyishString(Object val) {
    if (val == null) {
      return "";
    }
    if (val instanceof String || val instanceof Number || val instanceof Boolean) {
      return val.toString();
    }
    return WhitespaceUtils.unquoteAndUnescape(getAsPyishString(val, true));
  }

  public static String getAsPyishString(Object val) {
    return getAsPyishString(val, false);
  }

  private static String getAsPyishString(Object val, boolean forOutput) {
    try {
      return getAsPyishStringOrThrow(val, forOutput);
    } catch (IOException e) {
      // getAsPyishStringOrThrow adds this outer IOException around what was thrown
      Throwable failure = e.getCause();
      handleLengthLimitingException(failure);
      handleDeferredValueException(failure);
      return Objects.toString(val, "");
    }
  }

  private static void handleDeferredValueException(Throwable e) {
    Throwable unwrapped = stripJacksonWrappers(e);
    if (unwrapped instanceof DeferredValueException) {
      throw (DeferredValueException) unwrapped;
    }
  }

  public static void handleLengthLimitingException(IOException e) {
    handleLengthLimitingException((Throwable) e);
  }

  public static void handleLengthLimitingException(Throwable e) {
    Throwable unwrapped = stripJacksonWrappers(e);
    if (unwrapped instanceof LengthLimitingJsonProcessingException) {
      throw new OutputTooBigException(
        ((LengthLimitingJsonProcessingException) unwrapped).getMaxSize(),
        ((LengthLimitingJsonProcessingException) unwrapped).getAttemptedSize()
      );
    } else if (unwrapped instanceof OutputTooBigException) {
      throw (OutputTooBigException) unwrapped;
    }
  }

  /**
   * Steps to the cause while the current throwable is a Jackson wrapper (every
   * {@link JacksonException}, for example {@code DatabindException} and
   * {@code JacksonIOException}) and returns the first
   * throwable that is not one, so that a wrapper layer added by Jackson does not hide
   * the exception thrown by a serializer. It stops at any other throwable, for example
   * a {@link RuntimeException} wrapping the exception of interest, and gives up after
   * {@link #MAX_WRAPPER_DEPTH} steps, returning the throwable reached then.
   * It does not walk the whole cause chain.
   * @param candidate throwable to start from, may be null
   * @return the first non-wrapper throwable, or null if the wrappers have no cause
   */
  public static Throwable stripJacksonWrappers(Throwable candidate) {
    Throwable current = candidate;
    for (int i = 0; i < MAX_WRAPPER_DEPTH && isJacksonWrapper(current); i++) {
      current = current.getCause();
    }
    return current;
  }

  private static boolean isJacksonWrapper(Throwable throwable) {
    return throwable instanceof JacksonException;
  }

  public static String getAsPyishStringOrThrow(Object val) throws IOException {
    return getAsPyishStringOrThrow(val, false);
  }

  /**
   * Any failure is reported as an {@link IOException} whose cause is what Jackson, or a
   * serializer, threw: Jackson 3 throws unchecked exceptions, and a
   * {@link DeferredValueException} or {@link OutputTooBigException} thrown by a root serializer
   * is not wrapped by Jackson at all. The callers of this method catch {@link IOException}.
   * The wrappers below that outermost exception are unwrapped by {@link #stripJacksonWrappers}.
   */
  public static String getAsPyishStringOrThrow(Object val, boolean forOutput)
    throws IOException {
    boolean useSnakeCaseMappingOverride = JinjavaInterpreter
      .getCurrentMaybe()
      .map(interpreter ->
        interpreter.getConfig().getLegacyOverrides().isUseSnakeCasePropertyNaming()
      )
      .orElse(false);
    ObjectWriter objectWriter = useSnakeCaseMappingOverride
      ? SNAKE_CASE_PYISH_OBJECT_WRITER
      : PYISH_OBJECT_WRITER;
    Writer writer;
    Optional<Long> maxOutputSize = JinjavaInterpreter
      .getCurrentMaybe()
      .map(interpreter -> interpreter.getConfig().getMaxOutputSize())
      .filter(max -> max > 0);
    if (maxOutputSize.isPresent()) {
      AtomicInteger remainingLength = new AtomicInteger(
        (int) Math.min(Integer.MAX_VALUE, maxOutputSize.get())
      );
      objectWriter =
        objectWriter.withAttribute(
          LengthLimitingWriter.REMAINING_LENGTH_ATTRIBUTE,
          remainingLength
        );
      writer = new LengthLimitingWriter(new CharArrayWriter(), remainingLength);
    } else {
      writer = new CharArrayWriter();
    }
    if (!useSnakeCaseMappingOverride) {
      objectWriter = objectWriter.withAttribute(ALLOW_SNAKE_CASE_ATTRIBUTE, !forOutput);
    }
    try {
      objectWriter.writeValue(writer, val);
    } catch (RuntimeException e) {
      throw new IOException(e.getMessage(), e);
    }

    return writer.toString();
  }

  public static class NullKeySerializer extends ValueSerializer<Object> {

    @Override
    public void serialize(
      Object o,
      JsonGenerator jsonGenerator,
      SerializationContext serializationContext
    ) {
      jsonGenerator.writeName("");
    }
  }
}
