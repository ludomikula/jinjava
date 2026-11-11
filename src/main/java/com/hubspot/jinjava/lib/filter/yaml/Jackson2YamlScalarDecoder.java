/*
 * Parts of this file are adapted from com.fasterxml.jackson.dataformat.yaml.YAMLParser of
 * jackson-dataformat-yaml 2.20.1.
 *
 * Copyright (c) FasterXML, Tatu Saloranta and the Jackson project contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * Changes: reduced to the scalar typing, boolean matching, integer and float decoding and
 * underscore cleaning of YAMLParser (_decodeScalar, _matchYAMLBoolean, _decodeNumberScalar and
 * its radix helpers, _parseNumericValue, _cleanYamlInt, _cleanYamlFloat); returns the Java value
 * readValue(String, Object.class) produced instead of a token; failures throw
 * YamlReadException; the Jackson 2 settings jinjava used fixed in place; the numbers and the
 * boolean matcher restructured into named constants.
 */
package com.hubspot.jinjava.lib.filter.yaml;

import java.math.BigInteger;
import java.util.Optional;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;
import tools.jackson.core.Base64Variants;

/**
 * Turns the scalar events of SnakeYAML 2.4 into the Java values Jackson 2.20.1's {@code
 * YAMLParser} and {@code readValue(String, Object.class)} produced for {@code fromyaml}: a {@link
 * String}, {@link Integer}, {@link Long}, {@link BigInteger}, {@link Double}, {@link Boolean},
 * {@code byte[]} or null. Untagged scalars are typed by SnakeYAML's {@link Resolver} (YAML 1.1:
 * the words {@code yes}, {@code no}, {@code on}, {@code off}, the forms {@code 0755}, {@code
 * 0x1F}, {@code 0b101}, {@code 1_000}, {@code 190:20:30}) and then decoded as Jackson 2 did
 * ({@link #matchYamlBoolean(String)}, the number decoding and cleaning, {@code !!binary}).
 * Jackson 2's settings that jinjava used are fixed: an empty value is typed by the resolver
 * (null) and boolean-like words are booleans.
 *
 * <p>Limits: a number of more than {@value #MAX_NUMBER_LENGTH} characters is rejected, as in
 * Jackson 2; no limit applies to the length of a string. Only SnakeYAML 2.4 was compared; the
 * decoding mirrors Jackson 2.20.1 and later Jackson 2 fixes are not picked up. Equality with
 * Jackson 2 was established on recorded sets (see {@code FromYamlRecordedOutcomeTest}), not
 * proven. The messages of the exceptions are not Jackson's.
 *
 * <p>There is no instance state, so one instance serves all threads; the {@link Resolver} is
 * shared and only reads state built in its constructor.
 */
class Jackson2YamlScalarDecoder {

  /** Jackson 2.20.1's default maximum length of a number, sign included. */
  static final int MAX_NUMBER_LENGTH = 1000;

  private static final String YAML_TAG_PREFIX = "tag:yaml.org,2002:";
  private static final String BINARY_TAG = "binary";
  private static final String BOOL_TAG = "bool";
  private static final String INT_TAG = "int";
  private static final String FLOAT_TAG = "float";
  private static final String NULL_TAG = "null";
  private static final String NON_SPECIFIC_TAG = "!";
  private static final String TAG_ALTERNATIVES_SEPARATOR = ",";
  private static final String MINUS_SIGN = "-";

  private static final long MIN_INT_L = Integer.MIN_VALUE;
  private static final long MAX_INT_L = Integer.MAX_VALUE;

  private static final int BINARY_RADIX = 2;
  private static final int OCTAL_RADIX = 8;
  private static final int DECIMAL_RADIX = 10;
  private static final int HEX_RADIX = 16;

  /** The longest digit strings that fit an int and a long, per radix, and the length at which an int needs the sign check. */
  private static final int BINARY_INT_MAX_DIGITS = 31;
  private static final int BINARY_LONG_MAX_DIGITS = 63;
  private static final int BINARY_INT_CHECK_DIGITS = 32;
  private static final int OCTAL_INT_MAX_DIGITS = 10;
  private static final int OCTAL_LONG_MAX_DIGITS = 21;
  private static final int HEX_INT_MAX_DIGITS = 7;
  private static final int HEX_LONG_MAX_DIGITS = 15;
  private static final int HEX_INT_CHECK_DIGITS = 8;
  private static final int DECIMAL_INT_MAX_DIGITS = 9;
  private static final int DECIMAL_LONG_MAX_DIGITS = 18;
  private static final int DECIMAL_INT_CHECK_DIGITS = 10;
  private static final int DECIMAL_LONG_CHECK_DIGITS = 19;
  private static final int LONG_MAX_BIT_LENGTH = 63;

  private static final int BOOLEAN_Y_LENGTH = 1;
  private static final int BOOLEAN_NO_ON_LENGTH = 2;
  private static final int BOOLEAN_YES_OFF_LENGTH = 3;
  private static final int BOOLEAN_TRUE_LENGTH = 4;
  private static final int BOOLEAN_FALSE_LENGTH = 5;

  /** Stateless after construction, so one instance serves every read (Jackson 2 built one per parser). */
  private static final Resolver RESOLVER = new Resolver();

  /**
   * The value of a scalar, typed as Jackson 2 typed it.
   *
   * @throws YamlReadException if the scalar is tagged or typed as a number or {@code !!binary}
   *     and cannot be decoded, a number is longer than {@value #MAX_NUMBER_LENGTH} characters, or
   *     a lone sign is tagged {@code !!int}
   */
  Object decode(ScalarEvent scalar) {
    String value = scalar.getValue();
    String typeTag = scalar.getTag();
    int length = value.length();

    if (typeTag == null || typeTag.equals(NON_SPECIFIC_TAG)) {
      Tag nodeTag = RESOLVER.resolve(
        NodeId.scalar,
        value,
        scalar.getImplicit().canOmitTagInPlainScalar()
      );
      if (nodeTag == Tag.STR) {
        return value;
      }
      if (nodeTag == Tag.INT) {
        return decodeNumberScalar(value, length);
      }
      if (nodeTag == Tag.FLOAT) {
        return decodeFloat(cleanYamlFloat(value));
      }
      if (nodeTag == Tag.BOOL) {
        Optional<Boolean> bool = matchYamlBoolean(value);
        if (bool.isPresent()) {
          return bool.get();
        }
      } else if (nodeTag == Tag.NULL) {
        return null;
      } else {
        return value; // timestamp, merge, value, yaml
      }
      return value;
    }

    if (typeTag.startsWith(YAML_TAG_PREFIX)) {
      typeTag = typeTag.substring(YAML_TAG_PREFIX.length());
      if (typeTag.contains(TAG_ALTERNATIVES_SEPARATOR)) {
        String[] tags = typeTag.split(TAG_ALTERNATIVES_SEPARATOR);
        typeTag = tags.length == 0 ? "" : tags[0];
      }
    }
    if (BINARY_TAG.equals(typeTag)) {
      try {
        return Base64Variants.MIME.decode(value.trim());
      } catch (IllegalArgumentException e) {
        throw new YamlReadException(e.getMessage(), e);
      }
    }
    if (BOOL_TAG.equals(typeTag)) {
      Optional<Boolean> bool = matchYamlBoolean(value);
      if (bool.isPresent()) {
        return bool.get();
      }
    } else if (length > 0) {
      if (INT_TAG.equals(typeTag)) {
        return decodeNumberScalar(value, length);
      }
      if (FLOAT_TAG.equals(typeTag)) {
        return decodeFloat(cleanYamlFloat(value));
      }
      if (NULL_TAG.equals(typeTag)) {
        return null;
      }
    }
    return value;
  }

  /**
   * Jackson 2's matcher with {@code PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS} off: {@code y}, {@code
   * n}, {@code yes}, {@code no}, {@code on}, {@code off}, {@code true} and {@code false} in any
   * case. Jackson 3's own matcher accepts only {@code true} and {@code false}. Overridable so
   * that a test can put Jackson 3's rule back and show what that costs.
   *
   * @return the boolean, or empty if the value is not a boolean word
   */
  Optional<Boolean> matchYamlBoolean(String value) {
    switch (value.length()) {
      case BOOLEAN_Y_LENGTH:
        switch (value.charAt(0)) {
          case 'y':
          case 'Y':
            return Optional.of(Boolean.TRUE);
          case 'n':
          case 'N':
            return Optional.of(Boolean.FALSE);
          default:
            return Optional.empty();
        }
      case BOOLEAN_NO_ON_LENGTH:
        if ("no".equalsIgnoreCase(value)) {
          return Optional.of(Boolean.FALSE);
        }
        return "on".equalsIgnoreCase(value)
          ? Optional.of(Boolean.TRUE)
          : Optional.empty();
      case BOOLEAN_YES_OFF_LENGTH:
        if ("yes".equalsIgnoreCase(value)) {
          return Optional.of(Boolean.TRUE);
        }
        return "off".equalsIgnoreCase(value)
          ? Optional.of(Boolean.FALSE)
          : Optional.empty();
      case BOOLEAN_TRUE_LENGTH:
        return "true".equalsIgnoreCase(value)
          ? Optional.of(Boolean.TRUE)
          : Optional.empty();
      case BOOLEAN_FALSE_LENGTH:
        return "false".equalsIgnoreCase(value)
          ? Optional.of(Boolean.FALSE)
          : Optional.empty();
      default:
        return Optional.empty();
    }
  }

  // ---------------------------------------------------------------- integers

  /**
   * As in Jackson 2, a lone sign (reachable through {@code !!int +}) gives no value, which the
   * caller saw as the end of the input; here it is a {@link YamlReadException}.
   */
  private Object decodeNumberScalar(String value, int length) {
    int i;
    boolean negative;
    char ch = value.charAt(0);
    if (ch == '-') {
      negative = true;
      i = 1;
    } else if (ch == '+') {
      negative = false;
      i = 1;
    } else {
      negative = false;
      i = 0;
    }
    if (length == i) {
      throw new YamlReadException("No digits in the integer '" + value + "'");
    }

    if (value.charAt(i) == '0') {
      if (++i == length) {
        return 0;
      }
      switch (value.charAt(i)) {
        case 'b':
        case 'B':
          return decodeNumberIntBinary(value, i + 1, negative);
        case 'x':
        case 'X':
          return decodeNumberIntHex(value, i + 1, negative);
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
        case '_':
          return decodeNumberIntOctal(value, i, negative);
        default:
          return value;
      }
    }

    boolean underscores = false;
    while (true) {
      char c = value.charAt(i);
      if (c > '9' || c < '0') {
        if (c == '_') {
          underscores = true;
        } else {
          break;
        }
      }
      if (++i == length) {
        return decodeDecimalInteger(underscores ? cleanYamlInt(value) : value, negative);
      }
    }
    return value;
  }

  private Object decodeNumberIntBinary(String value, int from, boolean negative) {
    String digits = cleanUnderscores(value, from);
    int digitLength = digits.length();
    if (digitLength <= BINARY_INT_MAX_DIGITS) {
      return intValue(decodeInt(digits, BINARY_RADIX), negative);
    }
    if (digitLength <= BINARY_LONG_MAX_DIGITS) {
      return decodeFromLong(
        decodeLong(digits, BINARY_RADIX),
        negative,
        digitLength == BINARY_INT_CHECK_DIGITS
      );
    }
    return decodeFromBigInteger(decodeBigInt(digits, BINARY_RADIX), negative);
  }

  private Object decodeNumberIntOctal(String value, int from, boolean negative) {
    String digits = cleanUnderscores(value, from);
    int digitLength = digits.length();
    if (digitLength <= OCTAL_INT_MAX_DIGITS) {
      return intValue(decodeInt(digits, OCTAL_RADIX), negative);
    }
    if (digitLength <= OCTAL_LONG_MAX_DIGITS) {
      return decodeFromLong(decodeLong(digits, OCTAL_RADIX), negative, false);
    }
    return decodeFromBigInteger(decodeBigInt(digits, OCTAL_RADIX), negative);
  }

  private Object decodeNumberIntHex(String value, int from, boolean negative) {
    String digits = cleanUnderscores(value, from);
    int digitLength = digits.length();
    if (digitLength <= HEX_INT_MAX_DIGITS) {
      return intValue(decodeInt(digits, HEX_RADIX), negative);
    }
    if (digitLength <= HEX_LONG_MAX_DIGITS) {
      return decodeFromLong(
        decodeLong(digits, HEX_RADIX),
        negative,
        digitLength == HEX_INT_CHECK_DIGITS
      );
    }
    return decodeFromBigInteger(decodeBigInt(digits, HEX_RADIX), negative);
  }

  private static Integer intValue(int unsigned, boolean negative) {
    return negative ? -unsigned : unsigned;
  }

  private static Object decodeFromLong(
    long unsignedValue,
    boolean negative,
    boolean checkIfInt
  ) {
    long actualValue;
    if (negative) {
      actualValue = -unsignedValue;
      if (checkIfInt && actualValue >= MIN_INT_L) {
        return (int) actualValue;
      }
    } else {
      if (checkIfInt && unsignedValue < MAX_INT_L) {
        return (int) unsignedValue;
      }
      actualValue = unsignedValue;
    }
    return actualValue;
  }

  private static BigInteger decodeFromBigInteger(
    BigInteger unsignedValue,
    boolean negative
  ) {
    return negative ? unsignedValue.negate() : unsignedValue;
  }

  /** Integer, Long or BigInteger for a decimal integer text that may start with a sign. */
  private static Object decodeDecimalInteger(String cleaned, boolean negative) {
    int length = cleaned.length();
    if (negative) {
      length--;
    }
    if (length <= DECIMAL_INT_MAX_DIGITS) {
      return decodeInt(cleaned, DECIMAL_RADIX);
    }
    if (length <= DECIMAL_LONG_MAX_DIGITS) {
      long l = decodeLong(cleaned, DECIMAL_RADIX);
      if (length == DECIMAL_INT_CHECK_DIGITS) {
        boolean fitsInt = negative ? l >= Integer.MIN_VALUE : l <= Integer.MAX_VALUE;
        if (fitsInt) {
          return (int) l;
        }
      }
      return l;
    }
    BigInteger n = decodeBigInt(cleaned, DECIMAL_RADIX);
    if (length == DECIMAL_LONG_CHECK_DIGITS && n.bitLength() <= LONG_MAX_BIT_LENGTH) {
      return n.longValue();
    }
    return n;
  }

  private static int decodeInt(String digits, int radix) {
    try {
      return Integer.parseInt(digits, radix);
    } catch (NumberFormatException e) {
      throw invalidNumber(digits, radix, e);
    }
  }

  private static long decodeLong(String digits, int radix) {
    try {
      return Long.parseLong(digits, radix);
    } catch (NumberFormatException e) {
      throw invalidNumber(digits, radix, e);
    }
  }

  private static BigInteger decodeBigInt(String digits, int radix) {
    checkNumberLength(digits.length());
    try {
      return new BigInteger(digits, radix);
    } catch (NumberFormatException e) {
      throw invalidNumber(digits, radix, e);
    }
  }

  private static Double decodeFloat(String cleaned) {
    checkNumberLength(cleaned.length());
    try {
      return Double.parseDouble(cleaned);
    } catch (NumberFormatException e) {
      throw new YamlReadException("Malformed numeric value '" + cleaned + "'", e);
    }
  }

  private static void checkNumberLength(int length) {
    if (length > MAX_NUMBER_LENGTH) {
      throw new YamlReadException(
        "Number length (" +
        length +
        ") exceeds the maximum length allowed (" +
        MAX_NUMBER_LENGTH +
        ")"
      );
    }
  }

  private static YamlReadException invalidNumber(
    String digits,
    int radix,
    NumberFormatException cause
  ) {
    return new YamlReadException(
      String.format(
        "Invalid base-%d number ('%s'), problem: %s",
        radix,
        digits,
        cause.getMessage()
      ),
      cause
    );
  }

  // ---------------------------------------------------------------- cleaning

  private static String cleanYamlInt(String text) {
    String cleaned = withoutUnderscores(text);
    if (cleaned.isEmpty() || MINUS_SIGN.equals(cleaned)) {
      throw new YamlReadException(String.format("Invalid number ('%s')", text));
    }
    return cleaned;
  }

  private static String cleanYamlFloat(String text) {
    return text.indexOf('_') < 0 ? text : withoutUnderscores(text);
  }

  /** The text without a leading plus sign and without underscores. */
  private static String withoutUnderscores(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = text.charAt(0) == '+' ? 1 : 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '_') {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** The digits of {@code text} from {@code from} on, without underscores. */
  private static String cleanUnderscores(String text, int from) {
    StringBuilder sb = new StringBuilder(text.length() - from);
    for (int i = from; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '_') {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
