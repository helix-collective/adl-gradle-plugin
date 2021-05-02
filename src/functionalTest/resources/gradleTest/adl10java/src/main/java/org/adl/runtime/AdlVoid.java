package org.adl.runtime;

// Bug in ADL 1.0 - this file does not get generated so we need to add it manually

/**
 * Representation for the ADL Void primitive type.
 *
 * java.lang.Void cannot be used, as it's runtime representation is null, and the ADL framework
 * avoids null values.
 */
public class AdlVoid {

  public static AdlVoid INSTANCE = new AdlVoid();

  private AdlVoid() {
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof AdlVoid)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }
};