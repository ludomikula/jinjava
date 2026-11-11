package com.hubspot.jinjava.lib.filter;

import com.hubspot.jinjava.doc.annotations.JinjavaDoc;
import com.hubspot.jinjava.doc.annotations.JinjavaParam;
import com.hubspot.jinjava.doc.annotations.JinjavaSnippet;
import com.hubspot.jinjava.interpret.InvalidInputException;
import com.hubspot.jinjava.interpret.InvalidReason;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.yaml.FromYamlReader;
import com.hubspot.jinjava.lib.filter.yaml.YamlReadException;

@JinjavaDoc(
  value = "Converts a YAML string to an object",
  input = @JinjavaParam(
    value = "string",
    desc = "YAML String to convert to an object",
    required = true
  ),
  snippets = { @JinjavaSnippet(code = "{{object|fromYaml}}") }
)
public class FromYamlFilter implements Filter {

  private static final FromYamlReader READER = new FromYamlReader();

  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    if (var == null) {
      return null;
    }

    if (!(var instanceof String)) {
      throw new InvalidInputException(interpreter, this, InvalidReason.STRING);
    }
    try {
      return READER.read((String) var);
    } catch (YamlReadException e) {
      throw new InvalidInputException(interpreter, this, InvalidReason.JSON_READ);
    }
  }

  @Override
  public String getName() {
    return "fromyaml";
  }
}
