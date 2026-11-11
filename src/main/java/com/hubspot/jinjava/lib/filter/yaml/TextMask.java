package com.hubspot.jinjava.lib.filter.yaml;

/**
 * Hides characters from the YAML parser and gives them back afterwards. {@link
 * Jackson2YamlReemitter} parses {@link #masked()} and passes every scalar it reads through {@link
 * #unmask(String)}.
 */
interface TextMask {
  /** The text to parse. */
  String masked();

  /** Restores the characters hidden in a scalar of the parsed {@link #masked()} text. */
  String unmask(String scalar);
}
