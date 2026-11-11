package com.hubspot.jinjava.lib.filter.yaml;

/**
 * The re-emit step of {@link ToYamlWriter} failed. Jackson 2 would have written the value, so
 * this is never a legitimate refusal to write; it means the text written by Jackson 3 could not
 * be converted. The cause is the parser, emitter or I/O failure.
 */
public class YamlReemitException extends RuntimeException {

  public YamlReemitException(String message, Throwable cause) {
    super(message, cause);
  }
}
