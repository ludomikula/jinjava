package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;

@Beta
public class PyishPrettyPrinter extends DefaultPrettyPrinter {

  /**
   * No spacing around the separators: the only whitespace written between entries is the single
   * space of {@link FixedSpaceIndenter}, as in Jackson 2. Jackson 3's spacing {@code BOTH}
   * would write {@code {'b': 1 ,  'a': 2}}.
   */
  private static final Separators SEPARATORS = Separators
    .createDefaultInstance()
    .withObjectNameValueSpacing(Separators.Spacing.NONE)
    .withObjectEntrySpacing(Separators.Spacing.NONE)
    .withArrayElementSpacing(Separators.Spacing.NONE);

  public static final PyishPrettyPrinter INSTANCE = new PyishPrettyPrinter();

  @Override
  public DefaultPrettyPrinter createInstance() {
    return INSTANCE;
  }

  private PyishPrettyPrinter() {
    super(SEPARATORS);
    _objectIndenter = FixedSpaceIndenter.instance();
  }

  @Override
  public void beforeArrayValues(JsonGenerator jg) {}

  @Override
  public void writeEndArray(JsonGenerator jg, int nrOfValues) {
    if (!this._arrayIndenter.isInline()) {
      --this._nesting;
    }
    jg.writeRaw(']');
  }

  @Override
  public void writeObjectNameValueSeparator(JsonGenerator jg) {
    jg.writeRaw(": ");
  }

  @Override
  public void beforeObjectEntries(JsonGenerator jg) {}

  @Override
  public void writeEndObject(JsonGenerator jg, int nrOfEntries) {
    if (!this._objectIndenter.isInline()) {
      --this._nesting;
    }
    jg.writeRaw("} ");
  }
}
