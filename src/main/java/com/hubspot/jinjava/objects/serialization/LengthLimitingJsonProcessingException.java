package com.hubspot.jinjava.objects.serialization;

import com.google.common.annotations.Beta;
import java.io.IOException;

@Beta
public class LengthLimitingJsonProcessingException extends IOException {

  private final int maxSize;
  private final int attemptedSize;

  protected LengthLimitingJsonProcessingException(int maxSize, int attemptedSize) {
    super(
      String.format(
        "Max length of %d chars reached when serializing. %d chars attempted.",
        maxSize,
        attemptedSize
      )
    );
    this.maxSize = maxSize;
    this.attemptedSize = attemptedSize;
  }

  public int getAttemptedSize() {
    return attemptedSize;
  }

  public int getMaxSize() {
    return maxSize;
  }
}
