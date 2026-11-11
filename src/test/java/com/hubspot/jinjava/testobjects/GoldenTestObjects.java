package com.hubspot.jinjava.testobjects;

import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import java.util.Arrays;
import java.util.List;

/**
 * Beans for TemplateGoldenTest. Neither fields nor getters are in alphabetical order and there is
 * no @JsonPropertyOrder. The private fields are declared on purpose: without them the property
 * order of a getter-only bean follows Class.getDeclaredMethods(), which the JVM does not keep
 * stable between runs (observed: the same bean serialized in a different order in the full
 * test suite than in an isolated run). Field declaration order is stable.
 */
public class GoldenTestObjects {

  public enum Color {
    RED,
    GREEN;

    @Override
    public String toString() {
      return "color-" + name().toLowerCase();
    }
  }

  public static class SmallBean {

    private final String zeta = "z";
    private final int alpha = 1;
    private final String middle = "m";
    private final boolean active = true;
    private final Double ratio = 1.5;
    private final String beta = "b/\"q\"";

    public String getZeta() {
      return "z";
    }

    public int getAlpha() {
      return 1;
    }

    public String getMiddle() {
      return "m";
    }

    public boolean isActive() {
      return true;
    }

    public Double getRatio() {
      return 1.5;
    }

    public String getBeta() {
      return "b/\"q\"";
    }
  }

  public static class BigBean {

    private final String zulu = "z";
    private final int alpha = 1;
    private final String yankee = "y";
    private final long bravo = 2L;
    private final boolean xray = false;
    private final String charlie = "c";
    private final List<Integer> whiskey = Arrays.asList(3, 2, 1);
    private final Color delta = Color.GREEN;

    public String getZulu() {
      return "z";
    }

    public int getAlpha() {
      return 1;
    }

    public String getYankee() {
      return "y";
    }

    public long getBravo() {
      return 2L;
    }

    public boolean isXray() {
      return false;
    }

    public String getCharlie() {
      return "c";
    }

    public List<Integer> getWhiskey() {
      return Arrays.asList(3, 2, 1);
    }

    public Color getDelta() {
      return Color.GREEN;
    }
  }

  public static class CamelBean {

    private final String lastName = "Doe";
    private final String firstName = "Jane";
    private final int httpStatusCode = 200;

    public String getLastName() {
      return "Doe";
    }

    public String getFirstName() {
      return "Jane";
    }

    public int getHttpStatusCode() {
      return 200;
    }
  }

  /**
   * Getters whose names start with an acronym or a single capital letter. Jackson 2 and Jackson 3
   * derive different property names from them by default (getURL becomes "url" in Jackson 2), so
   * this bean pins the naming a migration has to keep.
   */
  public static class AcronymBean {

    // Field names match the property names Jackson 2 derives from the getters (url, xvalue), so
    // the declaration order below fixes the property order.
    private final String url = "http://x/y";
    private final int xvalue = 7;
    private final String name = "n";

    public String getURL() {
      return "http://x/y";
    }

    public int getXValue() {
      return 7;
    }

    public String getName() {
      return "n";
    }
  }

  public static class EmptyBean {}

  public static class ThrowingBean {

    private final String ok = "ok";
    private final String boom = null;

    public String getOk() {
      return "ok";
    }

    public String getBoom() {
      throw new IllegalStateException("boom");
    }
  }

  /** Getter throws DeferredValueException directly. */
  public static class DeferredGetterBean {

    private final String ok = "ok";
    private final String boom = null;

    public String getOk() {
      return "ok";
    }

    public String getBoom() {
      throw new DeferredValueException("x");
    }
  }

  /** Getter throws DeferredValueException wrapped one level in a RuntimeException. */
  public static class WrappedDeferredGetterBean {

    private final String ok = "ok";
    private final String boom = null;

    public String getOk() {
      return "ok";
    }

    public String getBoom() {
      throw new RuntimeException(new DeferredValueException("x"));
    }
  }

  /** Getter throws OutputTooBigException directly. */
  public static class TooBigGetterBean {

    private final String ok = "ok";
    private final String boom = null;

    public String getOk() {
      return "ok";
    }

    public String getBoom() {
      throw new OutputTooBigException(10, 20);
    }
  }

  /** Getter throws OutputTooBigException wrapped one level in a RuntimeException. */
  public static class WrappedTooBigGetterBean {

    private final String ok = "ok";
    private final String boom = null;

    public String getOk() {
      return "ok";
    }

    public String getBoom() {
      throw new RuntimeException(new OutputTooBigException(10, 20));
    }
  }
}
