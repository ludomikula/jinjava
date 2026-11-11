package com.hubspot.jinjava.testobjects;

import com.hubspot.jinjava.interpret.DeferredValueException;
import com.hubspot.jinjava.interpret.OutputTooBigException;
import com.hubspot.jinjava.objects.serialization.PyishSerializable;
import java.io.IOException;

/**
 * Values for the pyish outcome corpus and the pyish unit tests. They only override
 * {@link PyishSerializable#appendPyishString(Appendable)}, whose signature does not change with
 * the Jackson generation, so the same classes run against both the Jackson 2 and the Jackson 3
 * pyish stack.
 */
public class PyishOutcomeTestObjects {

  public static final String PLAIN_PYISH_TEXT = "ps()";
  public static final long TOO_BIG_MAX_SIZE = 10L;
  public static final long TOO_BIG_SIZE = 20L;
  public static final String DEFERRED_MESSAGE = "ps-deferred";
  public static final String IO_MESSAGE = "ps-io-wrapper";

  /** Writes {@link #PLAIN_PYISH_TEXT} as its pyish form. */
  public static class PlainPyishSerializable implements PyishSerializable {

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Appendable & CharSequence> T appendPyishString(T appendable)
      throws IOException {
      return (T) appendable.append(PLAIN_PYISH_TEXT);
    }

    @Override
    public String toString() {
      return "PlainPyishSerializable!";
    }
  }

  /** {@link #appendPyishString} throws a {@link DeferredValueException}. */
  public static class DeferredPyishSerializable implements PyishSerializable {

    @Override
    public <T extends Appendable & CharSequence> T appendPyishString(T appendable) {
      throw new DeferredValueException(DEFERRED_MESSAGE);
    }

    @Override
    public String toString() {
      return "DeferredPyishSerializable!";
    }
  }

  /** {@link #appendPyishString} throws an {@link IOException} caused by a deferred value. */
  public static class IoWrappedDeferredPyishSerializable implements PyishSerializable {

    @Override
    public <T extends Appendable & CharSequence> T appendPyishString(T appendable)
      throws IOException {
      throw new IOException(IO_MESSAGE, new DeferredValueException(DEFERRED_MESSAGE));
    }

    @Override
    public String toString() {
      return "IoWrappedDeferredPyishSerializable!";
    }
  }

  /** {@link #appendPyishString} throws a {@link RuntimeException} caused by a deferred value. */
  public static class RuntimeWrappedDeferredPyishSerializable
    implements PyishSerializable {

    @Override
    public <T extends Appendable & CharSequence> T appendPyishString(T appendable) {
      throw new RuntimeException(new DeferredValueException(DEFERRED_MESSAGE));
    }

    @Override
    public String toString() {
      return "RuntimeWrappedDeferredPyishSerializable!";
    }
  }

  /** {@link #appendPyishString} throws an {@link OutputTooBigException}. */
  public static class TooBigPyishSerializable implements PyishSerializable {

    @Override
    public <T extends Appendable & CharSequence> T appendPyishString(T appendable) {
      throw new OutputTooBigException(TOO_BIG_MAX_SIZE, TOO_BIG_SIZE);
    }

    @Override
    public String toString() {
      return "TooBigPyishSerializable!";
    }
  }

  /** {@link #appendPyishString} throws a plain {@link IOException}. */
  public static class IoFailingPyishSerializable implements PyishSerializable {

    @Override
    public <T extends Appendable & CharSequence> T appendPyishString(T appendable)
      throws IOException {
      throw new IOException(IO_MESSAGE);
    }

    @Override
    public String toString() {
      return "IoFailingPyishSerializable!";
    }
  }

  /**
   * A bean with one property more than the 6 of Jackson 3's unrolled bean serializer. The private
   * fields fix the property order, as in {@link GoldenTestObjects}.
   */
  public static class SevenPropertyBean {

    private final String fooBar = "1";
    private final String barBaz = "2";
    private final String bazQux = "3";
    private final String quxQuux = "4";
    private final String quuxCorge = "5";
    private final String corgeGrault = "6";
    private final String graultGarply = "7";

    public String getFooBar() {
      return "1";
    }

    public String getBarBaz() {
      return "2";
    }

    public String getBazQux() {
      return "3";
    }

    public String getQuxQuux() {
      return "4";
    }

    public String getQuuxCorge() {
      return "5";
    }

    public String getCorgeGrault() {
      return "6";
    }

    public String getGraultGarply() {
      return "7";
    }
  }
}
