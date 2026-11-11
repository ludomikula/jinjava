package com.hubspot.jinjava.el.ext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.exptest.ExpTest;
import com.hubspot.jinjava.lib.filter.Filter;
import java.lang.reflect.Method;
import org.junit.Test;

public class ValidatorConfigBannedConstructsTest {

  // Jackson 2 is not a compile-time dependency any more (it is only on the runtime classpath
  // through other dependencies), so its banned names are spelled out.
  private static final String JACKSON2_DATABIND_PACKAGE =
    "com.fasterxml.jackson.databind";
  private static final String JACKSON2_OBJECT_MAPPER_NAME =
    JACKSON2_DATABIND_PACKAGE + ".ObjectMapper";

  // MethodValidatorConfig: allowedMethods() path

  @Test
  public void itRejectsObjectMethodInAllowedMethods() throws NoSuchMethodException {
    Method toStringMethod = Object.class.getMethod("toString");
    assertThatThrownBy(() ->
        MethodValidatorConfig.builder().addAllowedMethods(toStringMethod).build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsClassMethodInAllowedMethods() throws NoSuchMethodException {
    Method getNameMethod = Class.class.getMethod("getName");
    assertThatThrownBy(() ->
        MethodValidatorConfig.builder().addAllowedMethods(getNameMethod).build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  // MethodValidatorConfig: allowedDeclaredMethodsFromCanonicalClassNames() path

  @Test
  public void itRejectsObjectClassInAllowedDeclaredMethodClassNames() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassNames(
            Object.class.getCanonicalName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsClassClassInAllowedDeclaredMethodClassNames() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassNames(
            Class.class.getCanonicalName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsObjectMapperInAllowedDeclaredMethodClassNames() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassNames(JACKSON2_OBJECT_MAPPER_NAME)
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJackson3ObjectMapperInAllowedDeclaredMethodClassNames() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassNames(
            tools.jackson.databind.ObjectMapper.class.getCanonicalName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJinjavaInterpreterInAllowedDeclaredMethodClassNames() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassNames(
            JinjavaInterpreter.class.getCanonicalName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  // MethodValidatorConfig: allowedDeclaredMethodsFromCanonicalClassPrefixes() path

  @Test
  public void itRejectsReflectPackageInAllowedDeclaredMethodPrefixes() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassPrefixes(
            Method.class.getPackageName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJacksonDatabindPackageInAllowedDeclaredMethodPrefixes() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassPrefixes(JACKSON2_DATABIND_PACKAGE)
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJackson3DatabindPackageInAllowedDeclaredMethodPrefixes() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassPrefixes(
            tools.jackson.databind.ObjectMapper.class.getPackageName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsEvilJinjavaFilterPathInAllowedDeclaredMethodPrefixes() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassPrefixes(
            Filter.class.getPackageName() + "_evil"
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsEvilJinjavaExptestPathInAllowedDeclaredMethodPrefixes() {
    assertThatThrownBy(() ->
        MethodValidatorConfig
          .builder()
          .addAllowedDeclaredMethodsFromCanonicalClassPrefixes(
            ExpTest.class.getPackageName() + "_evil"
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  // ReturnTypeValidatorConfig: allowedCanonicalClassNames() path

  @Test
  public void itRejectsObjectClassInAllowedReturnTypeClassNames() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassNames(Object.class.getCanonicalName())
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsClassClassInAllowedReturnTypeClassNames() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassNames(Class.class.getCanonicalName())
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsObjectMapperInAllowedReturnTypeClassNames() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassNames(JACKSON2_OBJECT_MAPPER_NAME)
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJackson3ObjectMapperInAllowedReturnTypeClassNames() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassNames(
            tools.jackson.databind.ObjectMapper.class.getCanonicalName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJinjavaInterpreterInAllowedReturnTypeClassNames() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassNames(JinjavaInterpreter.class.getCanonicalName())
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  // ReturnTypeValidatorConfig: allowedCanonicalClassPrefixes() path

  @Test
  public void itRejectsReflectPackageInAllowedReturnTypePrefixes() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassPrefixes(Method.class.getPackageName())
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJacksonDatabindPackageInAllowedReturnTypePrefixes() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassPrefixes(JACKSON2_DATABIND_PACKAGE)
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }

  @Test
  public void itRejectsJackson3DatabindPackageInAllowedReturnTypePrefixes() {
    assertThatThrownBy(() ->
        ReturnTypeValidatorConfig
          .builder()
          .addAllowedCanonicalClassPrefixes(
            tools.jackson.databind.ObjectMapper.class.getPackageName()
          )
          .build()
      )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Banned classes or prefixes");
  }
}
