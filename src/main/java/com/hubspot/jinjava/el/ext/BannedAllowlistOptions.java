package com.hubspot.jinjava.el.ext;

import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class BannedAllowlistOptions {

  // These aren't required, but they prevent someone from misconfiguring Jinjava to allow sandbox bypass unintentionally
  private static final String JAVA_LANG_REFLECT_PACKAGE =
    Method.class.getPackage().getName(); // java.lang.reflect
  // The Jackson 2 package stays banned for good: Jackson 2 databind remains on the classpath
  // through other dependencies even after jinjava itself no longer uses it.
  // It is a string literal because jinjava no longer compiles against Jackson 2.
  private static final String JACKSON2_DATABIND_PACKAGE =
    "com.fasterxml.jackson.databind";
  private static final String JACKSON3_DATABIND_PACKAGE =
    tools.jackson.databind.ObjectMapper.class.getPackage().getName(); // tools.jackson.databind

  private static final String[] BANNED_PREFIXES = {
    Class.class.getCanonicalName(),
    Object.class.getCanonicalName(),
    JAVA_LANG_REFLECT_PACKAGE,
    JACKSON2_DATABIND_PACKAGE,
    JACKSON3_DATABIND_PACKAGE,
  };

  private static final Set<String> ALLOWED_JINJAVA_PREFIXES = Stream
    .concat(
      Stream.of("com.hubspot.jinjava.testobjects."),
      Arrays
        .stream(AllowlistGroup.values())
        .flatMap(g ->
          Stream
            .of(
              g.allowedDeclaredMethodsFromCanonicalClassPrefixes(),
              g.allowedReturnTypeCanonicalClassPrefixes(),
              g.allowedDeclaredMethodsFromClasses(),
              g.allowedReturnTypeClasses()
            )
            .flatMap(Arrays::stream)
        )
    )
    .collect(ImmutableSet.toImmutableSet());

  public static List<String> findBannedPrefixes(Stream<String> prefixes) {
    return prefixes
      .filter(prefixOrName ->
        Arrays
          .stream(BANNED_PREFIXES)
          .anyMatch(banned ->
            banned.startsWith(prefixOrName) || prefixOrName.startsWith(banned)
          ) ||
        isIllegalJinjavaClass(prefixOrName)
      )
      .toList();
  }

  private static boolean isIllegalJinjavaClass(String prefixOrName) {
    if (!prefixOrName.startsWith("com.hubspot.jinjava")) {
      return false;
    }
    // e.g. com.hubspot.jinjava.lib.exptest is allowed, but com.hubspot.jinjava.Jinjava will not be
    return ALLOWED_JINJAVA_PREFIXES.stream().noneMatch(prefixOrName::startsWith);
  }
}
