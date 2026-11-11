package com.hubspot.jinjava.interpret;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Marker object of a `null` value. A null value in the map is usually considered
 * the key does not exist. For example map = {"a": null}, if map.get("a") == null,
 * we treat it as the there is not key "a" in the map.
 */
public final class NullValue {

  public static final NullValue INSTANCE = new NullValue();

  private NullValue() {}

  public static NullValue instance() {
    return INSTANCE;
  }

  /**
   * Read by both Jackson 2 and Jackson 3 (it is a jackson-annotations annotation):
   * serializes this marker as JSON <code>null</code>.
   */
  @JsonValue
  public Object toJsonValue() {
    return null;
  }

  @Override
  public String toString() {
    return "null";
  }
}
