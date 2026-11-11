package com.hubspot.jinjava.lib.filter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;

public class Jackson2YamlScalarDecoderTest {

  private static final int MAX_NUMBER_LENGTH = 1000;
  private static final int MAX_INT_HEX_AS_LONG = 0x7FFFFFFF;

  private final Jackson2YamlScalarDecoder decoder = new Jackson2YamlScalarDecoder();

  /** The decoded value of the first scalar of {@code yaml}. */
  private Object decode(String yaml) {
    ParserImpl events = new ParserImpl(
      new StreamReader(new StringReader(yaml)),
      new LoaderOptions()
    );
    while (true) {
      Event event = events.getEvent();
      if (event.is(Event.ID.Scalar)) {
        return decoder.decode((ScalarEvent) event);
      }
      assertThat(event.is(Event.ID.StreamEnd))
        .as("the text %s has a scalar", yaml)
        .isFalse();
    }
  }

  @Test
  public void itTypesTheYamlOneOneBooleanWords() {
    for (String word : new String[] { "yes", "Yes", "YES", "on", "ON", "true", "True" }) {
      assertThat(decode(word)).as(word).isEqualTo(Boolean.TRUE);
    }
    for (String word : new String[] { "no", "No", "off", "OFF", "false", "FALSE" }) {
      assertThat(decode(word)).as(word).isEqualTo(Boolean.FALSE);
    }
    // SnakeYAML's resolver does not type y and n, so they are booleans only when tagged
    for (String word : new String[] {
      "y",
      "n",
      "yep",
      "ye",
      "o",
      "of",
      "tru",
      "falsey",
      "nope",
    }) {
      assertThat(decode(word)).as(word).isEqualTo(word);
    }
    assertThat(decode("!!bool y")).isEqualTo(Boolean.TRUE);
    assertThat(decode("!!bool N")).isEqualTo(Boolean.FALSE);
    assertThat(decode("!!bool maybe")).isEqualTo("maybe");
  }

  @Test
  public void itMatchesBooleanWordsByLength() {
    assertThat(decoder.matchYamlBoolean("y")).contains(true);
    assertThat(decoder.matchYamlBoolean("N")).contains(false);
    assertThat(decoder.matchYamlBoolean("x")).isEqualTo(Optional.empty());
    assertThat(decoder.matchYamlBoolean("No")).contains(false);
    assertThat(decoder.matchYamlBoolean("oN")).contains(true);
    assertThat(decoder.matchYamlBoolean("ox")).isEqualTo(Optional.empty());
    assertThat(decoder.matchYamlBoolean("yEs")).contains(true);
    assertThat(decoder.matchYamlBoolean("OFf")).contains(false);
    assertThat(decoder.matchYamlBoolean("abc")).isEqualTo(Optional.empty());
    assertThat(decoder.matchYamlBoolean("TRUE")).contains(true);
    assertThat(decoder.matchYamlBoolean("fals")).isEqualTo(Optional.empty());
    assertThat(decoder.matchYamlBoolean("False")).contains(false);
    assertThat(decoder.matchYamlBoolean("truee")).isEqualTo(Optional.empty());
    assertThat(decoder.matchYamlBoolean("")).isEqualTo(Optional.empty());
    assertThat(decoder.matchYamlBoolean("yesyes")).isEqualTo(Optional.empty());
  }

  @Test
  public void itDecodesIntegersInEveryRadix() {
    assertThat(decode("0755")).isEqualTo(493);
    assertThat(decode("0b1010")).isEqualTo(10);
    assertThat(decode("-0x1F")).isEqualTo(-31);
    assertThat(decode("1_000")).isEqualTo(1000);
    assertThat(decode("+12")).isEqualTo(12);
    assertThat(decode("0")).isEqualTo(0);
    assertThat(decode("2147483648")).isEqualTo(2147483648L);
    assertThat(decode("-2147483648")).isEqualTo(Integer.MIN_VALUE);
    assertThat(decode("0xFFFFFFFF")).isEqualTo(4294967295L);
    assertThat(decode("-0x80000000")).isEqualTo(Integer.MIN_VALUE);
    assertThat(decode("0x7FFFFFFFFFFFFFF")).isEqualTo(0x7FFFFFFFFFFFFFFL);
    assertThat(decode("0xFFFFFFFFFFFFFFFFFF"))
      .isEqualTo(new BigInteger("FFFFFFFFFFFFFFFFFF", 16));
    assertThat(decode("-0xFFFFFFFFFFFFFFFFFF"))
      .isEqualTo(new BigInteger("FFFFFFFFFFFFFFFFFF", 16).negate());
    assertThat(decode("0b" + "1".repeat(40))).isEqualTo((1L << 40) - 1);
    assertThat(decode("0b" + "1".repeat(64)))
      .isEqualTo(new BigInteger("1".repeat(64), 2));
    assertThat(decode("07777777777777777777"))
      .isEqualTo(Long.parseLong("7777777777777777777", 8));
    assertThat(decode("0" + "7".repeat(30))).isEqualTo(new BigInteger("7".repeat(30), 8));
    assertThat(decode("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    assertThat(decode("9223372036854775808"))
      .isEqualTo(new BigInteger("9223372036854775808"));
    assertThat(decode("-9223372036854775808")).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void itKeepsTheWidthJacksonTwoGaveAtTheIntBoundary() {
    // 0x7FFFFFFF is a Long: the comparison with the int maximum is strict, as in Jackson 2
    assertThat(decode("0x7FFFFFFF"))
      .as("0x7FFFFFFF")
      .isInstanceOf(Long.class)
      .isEqualTo((long) MAX_INT_HEX_AS_LONG);
    assertThat(decode("0x7FFFFFE")).as("0x7FFFFFE").isInstanceOf(Integer.class);
    assertThat(decode("0x1FFFFFFFFFFFFFFF"))
      .as("0x1FFFFFFFFFFFFFFF")
      .isInstanceOf(BigInteger.class)
      .isEqualTo(new BigInteger("1FFFFFFFFFFFFFFF", 16));
    assertThat(decode("2147483647")).as("Integer.MAX_VALUE").isInstanceOf(Integer.class);
    assertThat(decode("+1234567890"))
      .as("a plus sign counts as a digit of the length, as in Jackson 2")
      .isEqualTo(1234567890L);
    assertThat(decode("-0")).as("-0").isEqualTo(0);
  }

  @Test
  public void itDecodesFloatsAndKeepsOtherNumberFormsAsStrings() {
    assertThat(decode("1_0.5")).isEqualTo(10.5);
    assertThat(decode("+1.5")).isEqualTo(1.5);
    assertThat(decode("6.8523015e+5")).isEqualTo(685230.15);
    assertThat(decode("190:20:30")).isEqualTo("190:20:30");
    assertThat(decode("0o17")).isEqualTo("0o17");
    assertThat(decode("12abc")).isEqualTo("12abc");
    assertThat(decode("0x1G")).isEqualTo("0x1G");
  }

  @Test
  public void itReadsExplicitlyTaggedScalars() {
    assertThat(decode("!!str 123")).isEqualTo("123");
    assertThat(decode("!!int \"12\"")).isEqualTo(12);
    assertThat(decode("!!float 1")).isEqualTo(1.0);
    assertThat(decode("!!null x")).isNull();
    assertThat(decode("!!null ''")).isEqualTo("");
    assertThat(decode("!<tag:yaml.org,2002:int,float> 12")).isEqualTo(12);
    assertThat(decode("!foo 123")).isEqualTo("123");
    assertThat(decode("!!int abc")).isEqualTo("abc");
    assertThat(decode("!!int 1:30")).isEqualTo("1:30");
    assertThat(decode("!!int 12abc")).isEqualTo("12abc");
  }

  @Test
  public void itRejectsAnExplicitIntegerWithoutDigits() {
    for (String text : new String[] { "!!int +", "!!int '-'", "!!int _" }) {
      assertThatThrownBy(() -> decode(text))
        .as(text)
        .isInstanceOf(YamlReadException.class);
    }
    assertThatThrownBy(() -> decode("!!int 0x_"))
      .isInstanceOf(YamlReadException.class)
      .hasCauseInstanceOf(NumberFormatException.class);
  }

  @Test
  public void itRejectsFloatWordsThatJavaCannotParse() {
    for (String text : new String[] { ".inf", ".NaN" }) {
      assertThatThrownBy(() -> decode(text))
        .as(text)
        .isInstanceOf(YamlReadException.class)
        .hasCauseInstanceOf(NumberFormatException.class);
    }
  }

  @Test
  public void itDecodesBinaryAndRejectsBadBase64() {
    assertThat((byte[]) decode("!!binary aGVsbG8=")).isEqualTo("hello".getBytes());
    assertThat((byte[]) decode("!!binary YWJj\n")).isEqualTo("abc".getBytes());
    assertThatThrownBy(() -> decode("!!binary '!!!'"))
      .isInstanceOf(YamlReadException.class)
      .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void itAppliesJacksonTwosNumberLengthLimit() {
    assertThat(decode("1".repeat(MAX_NUMBER_LENGTH)))
      .as("1000 digits")
      .isEqualTo(new BigInteger("1".repeat(MAX_NUMBER_LENGTH)));
    assertThatThrownBy(() -> decode("1".repeat(MAX_NUMBER_LENGTH + 1)))
      .as("1001 digits")
      .isInstanceOf(YamlReadException.class);
    assertThatThrownBy(() -> decode("-" + "1".repeat(MAX_NUMBER_LENGTH)))
      .as("a sign and 1000 digits count 1001")
      .isInstanceOf(YamlReadException.class);
    assertThat(decode("1".repeat(MAX_NUMBER_LENGTH - 2) + ".5"))
      .as("a float of 1000 characters")
      .isInstanceOf(Double.class);
    assertThatThrownBy(() -> decode("1".repeat(MAX_NUMBER_LENGTH - 1) + ".5"))
      .as("a float of 1001 characters")
      .isInstanceOf(YamlReadException.class);
    assertThatThrownBy(() -> decode("0x" + "F".repeat(MAX_NUMBER_LENGTH + 1)))
      .as("a hex integer of 1001 digits")
      .isInstanceOf(YamlReadException.class);
  }

  @Test
  public void itSetsNoLimitOnTheLengthOfAString() {
    String longText = "x".repeat(MAX_NUMBER_LENGTH * 100);
    assertThat(decode(longText)).isEqualTo(longText);
  }
}
