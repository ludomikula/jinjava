package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

public class ToYamlWriterTest {

  private static final String NEL = "\u0085";
  private static final String LS = "\u2028";

  private ListAppender<ILoggingEvent> logAppender;
  private Logger writerLogger;

  @Before
  public void attachLogAppender() {
    writerLogger = (Logger) LoggerFactory.getLogger(ToYamlWriter.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    writerLogger.addAppender(logAppender);
  }

  @After
  public void detachLogAppender() {
    writerLogger.detachAppender(logAppender);
  }

  private static Map<String, Object> map(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  @Test
  public void itWritesPlainValuesAsWritten() {
    ToYamlWriter writer = new ToYamlWriter();
    assertThat(writer.write(new int[] { 4, 1, 2 })).isEqualTo("- 4\n- 1\n- 2\n");
    assertThat(writer.write(map("testString", "testString")))
      .isEqualTo("testString: \"testString\"\n");
  }

  @Test
  public void itWritesLineBreakCharactersTheWayJackson2Did() {
    ToYamlWriter writer = new ToYamlWriter();
    assertThat(writer.write(map("k", "a" + LS + "b"))).isEqualTo("k: \"a\\Lb\"\n");
    assertThat(writer.write(map(NEL, 1))).isEqualTo("? '" + NEL + "  '\n: 1\n");
  }

  @Test
  public void itOnlyReemitsWhenTheGuardFindsALineBreakCharacter() {
    AtomicInteger reemits = new AtomicInteger();
    ToYamlWriter writer = new ToYamlWriter(
      Jackson2ParityYamlMapper.create(),
      yaml -> {
        reemits.incrementAndGet();
        return Jackson2YamlReemitter.reemit(yaml);
      }
    );

    writer.write(map("k", "plain"));
    assertThat(reemits.get()).isZero();

    writer.write(map("k", "a" + LS + "b"));
    assertThat(reemits.get()).isEqualTo(1);
  }

  @Test
  public void itFailsAndLogsInsteadOfFallingBackWhenTheReemitFails() {
    YamlReemitException failure = new YamlReemitException(
      "re-emit failed",
      new IllegalStateException("cause")
    );
    ToYamlWriter writer = new ToYamlWriter(
      Jackson2ParityYamlMapper.create(),
      yaml -> {
        throw failure;
      }
    );

    assertThatThrownBy(() -> writer.write(map("k", "a" + LS + "b"))).isSameAs(failure);

    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage()).contains("could not re-emit");
    assertThat(event.getThrowableProxy().getMessage()).isEqualTo("re-emit failed");
  }

  @Test
  public void itDoesNotCallTheReemitterForTextWithoutLineBreakCharacters() {
    ToYamlWriter writer = new ToYamlWriter(
      Jackson2ParityYamlMapper.create(),
      yaml -> {
        throw new AssertionError("re-emit must not run");
      }
    );
    assertThat(writer.write(map("k", "v"))).isEqualTo("k: \"v\"\n");
    assertThat(logAppender.list).isEmpty();
  }

  @Test
  public void itPropagatesTheMapperFailureForValuesJackson2CouldNotWrite() {
    ToYamlWriter writer = new ToYamlWriter();
    assertThatThrownBy(() -> writer.write(map("k", Optional.of("x"))))
      .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> writer.write(new Object()))
      .isInstanceOf(JacksonException.class);
    assertThat(logAppender.list).isEmpty();
  }
}
