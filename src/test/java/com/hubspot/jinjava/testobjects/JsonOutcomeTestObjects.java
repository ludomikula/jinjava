package com.hubspot.jinjava.testobjects;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hubspot.jinjava.interpret.NullValue;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.RandomAccess;

/**
 * Beans for the JSON filter outcome corpus. As in {@link GoldenTestObjects}, private fields are
 * declared in the intended property order so the output does not depend on the order in which
 * the JVM returns the getters.
 */
public class JsonOutcomeTestObjects {

  /** A public field next to an acronym getter, an is-getter and a camel case getter. */
  public static class MixedBean {

    public String pubField = "pf";
    private final String url = "u";
    private final String xvalue = "x";
    private final boolean active = true;
    private final String midValue = "m";

    public String getURL() {
      return "u";
    }

    public String getXValue() {
      return "x";
    }

    public boolean isActive() {
      return true;
    }

    public String getMidValue() {
      return "m";
    }
  }

  /** A bean without properties, so its JSON form depends on the "fail on empty beans" setting. */
  public static class PlainObject {

    @Override
    public String toString() {
      return "PlainObject!";
    }
  }

  /** A bean property holding {@link NullValue}, which has to be written as JSON null. */
  public static class NullValueBean {

    private final NullValue n = NullValue.instance();

    public NullValue getN() {
      return NullValue.instance();
    }
  }

  /**
   * A value whose {@link JsonValue} method throws, so that the failure is raised by a serializer
   * at the root of the value instead of inside a bean property.
   */
  public static class ThrowingJsonValue {

    private final RuntimeException toThrow;

    public ThrowingJsonValue(RuntimeException toThrow) {
      this.toThrow = toThrow;
    }

    @JsonValue
    public String value() {
      throw toThrow;
    }
  }

  /**
   * A list with one element whose access throws, so that the failure is raised while the list
   * serializer, which is the root serializer when this is the written value, reads the element.
   */
  public static class ThrowingList extends AbstractList<Object> implements RandomAccess {

    private final RuntimeException toThrow;

    public ThrowingList(RuntimeException toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public Object get(int index) {
      throw toThrow;
    }

    @Override
    public int size() {
      return 1;
    }
  }

  /** bean to map to list to inner object, to check failures raised deeper than the root. */
  public static class Holder {

    private final Object inner;

    public Holder(Object inner) {
      this.inner = inner;
    }

    public Map<String, Object> getData() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("list", new ArrayList<>(Arrays.asList(1, inner)));
      return map;
    }
  }
}
