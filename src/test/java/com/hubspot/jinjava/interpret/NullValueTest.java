package com.hubspot.jinjava.interpret;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;

public class NullValueTest {

  @Test
  public void itSerializesUnderlyingValue() throws JsonProcessingException {
    LazyExpression expression = LazyExpression.of(
      () -> ImmutableMap.of("test", "hello", "test2", NullValue.instance()),
      "{}"
    );
    Object evaluated = expression.get();
    assertThat(evaluated).isNotNull();
    assertThat(new ObjectMapper().writeValueAsString(expression))
      .isEqualTo("{\"test\":\"hello\",\"test2\":null}");
  }

  @Test
  public void itSerializesAsNullWithJackson3() {
    assertThat(
      JsonMapper
        .builder()
        .build()
        .writeValueAsString(ImmutableMap.of("test2", NullValue.instance()))
    )
      .isEqualTo("{\"test2\":null}");
  }
}
