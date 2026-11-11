package com.hubspot.jinjava.lib.filter;

import com.hubspot.jinjava.doc.annotations.JinjavaDoc;
import com.hubspot.jinjava.doc.annotations.JinjavaParam;
import com.hubspot.jinjava.doc.annotations.JinjavaSnippet;
import com.hubspot.jinjava.interpret.InvalidInputException;
import com.hubspot.jinjava.interpret.InvalidReason;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.yaml.ToYamlWriter;
import com.hubspot.jinjava.lib.filter.yaml.YamlReemitException;
import tools.jackson.core.JacksonException;

@JinjavaDoc(
  value = "Writes object as a YAML string",
  input = @JinjavaParam(
    value = "object",
    desc = "Object to write to YAML",
    required = true
  ),
  snippets = { @JinjavaSnippet(code = "{{object|toyaml}}") }
)
public class ToYamlFilter implements Filter {

  private static final ToYamlWriter YAML_WRITER = new ToYamlWriter();

  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    try {
      return YAML_WRITER.write(var);
    } catch (JacksonException | YamlReemitException e) {
      throw new InvalidInputException(interpreter, this, InvalidReason.JSON_WRITE);
    }
  }

  @Override
  public String getName() {
    return "toyaml";
  }
}
