package com.hubspot.jinjava.lib.filter.yaml;

/**
 * {@link FromYamlReader#read(String)} rejected its input: a SnakeYAML scanner, parser or reader
 * error; a structure Jackson 2 rejected (no document, a key that is not a scalar, a lone sign
 * under {@code !!int}); a number or {@code !!binary} value that cannot be decoded; or a nesting
 * depth or number length over the limit. The cause, if any, is the SnakeYAML or number format
 * failure.
 */
public class YamlReadException extends RuntimeException {

  public YamlReadException(String message) {
    super(message);
  }

  public YamlReadException(String message, Throwable cause) {
    super(message, cause);
  }
}
