package com.hubspot.jinjava.interpret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.interpret.LazyExpression.Memoization;
import java.util.List;
import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;

public class LazyExpressionTest {

  @Test
  public void itSerializesUnderlyingValue() {
    LazyExpression expression = LazyExpression.of(
      () -> ImmutableMap.of("test", "hello", "test2", "hello2"),
      "{}"
    );
    Object evaluated = expression.get();
    assertThat(evaluated).isNotNull();
    assertThat(JsonMapper.builder().build().writeValueAsString(expression))
      .isEqualTo("{\"test\":\"hello\",\"test2\":\"hello2\"}");
  }

  @Test
  public void itSerializesNonEvaluatedValueToEmpty() {
    LazyExpression expression = LazyExpression.of(
      () -> ImmutableMap.of("test", "hello", "test2", "hello2"),
      "{}"
    );
    assertThat(JsonMapper.builder().build().writeValueAsString(expression))
      .isEqualTo("\"\"");
  }

  @Test
  public void itMemoizesByDefault() {
    List mock = mock(List.class);
    LazyExpression expression = LazyExpression.of(mock::isEmpty, "");
    expression.get();
    expression.get();
    verify(mock).isEmpty();
  }

  @Test
  public void itAllowsMemoizationToBeDisabled() {
    List mock = mock(List.class);
    LazyExpression expression = LazyExpression.of(mock::isEmpty, "", Memoization.OFF);
    expression.get();
    expression.get();
    verify(mock, times(2)).isEmpty();
  }

  @Test
  public void itAllowsMemoizationToBeEnabled() {
    List mock = mock(List.class);
    LazyExpression expression = LazyExpression.of(mock::isEmpty, "", Memoization.ON);
    expression.get();
    expression.get();
    verify(mock).isEmpty();
  }
}
