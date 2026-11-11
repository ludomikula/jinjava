package com.hubspot.jinjava.lib.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.BaseInterpretingTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.Test;

/**
 * Pins the known break of moving {@code tojson} and {@code pprint} to Jackson 3: Jackson 2
 * databind annotations on a user's class are no longer applied. The annotated class is compiled
 * while the test runs, so no Jackson 2 class is referenced when this test is compiled, and the
 * build's dependency analysis does not need Jackson 2 declared for it. The test needs a JDK: it
 * asserts that the system Java compiler is present.
 */
public class Jackson2DatabindAnnotationBreakTest extends BaseInterpretingTest {

  private static final String PACKAGE_NAME = "knownbreak";
  private static final String CLASS_NAME = "Jackson2AnnotatedBean";
  private static final String BEAN_SOURCE =
    "package " +
    PACKAGE_NAME +
    ";\n" +
    "public class " +
    CLASS_NAME +
    " {\n" +
    "  @com.fasterxml.jackson.databind.annotation.JsonSerialize(\n" +
    "    using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)\n" +
    "  public int getValue() { return 42; }\n" +
    "}\n";
  private static final String JACKSON_2_ANNOTATION_CLASS =
    "com.fasterxml.jackson.databind.annotation.JsonSerialize";
  /** One class from each Jackson 2 jar the bean source needs on the compiler class path. */
  private static final List<String> JACKSON_2_CLASSES = Arrays.asList(
    JACKSON_2_ANNOTATION_CLASS,
    "com.fasterxml.jackson.annotation.JsonProperty",
    "com.fasterxml.jackson.core.JsonGenerator"
  );
  private static final String JACKSON_2_MAPPER_CLASS =
    "com.fasterxml.jackson.databind.ObjectMapper";
  private static final String GETTER_NAME = "getValue";
  private static final String TEMP_DIRECTORY_PREFIX = "jinjava-jackson2-annotation";
  private static final String JACKSON_2_OUTPUT = "{\"value\":\"42\"}";
  private static final String JACKSON_3_OUTPUT = "{\"value\":42}";

  @Test
  public void itDoesNotApplyJackson2DatabindAnnotations() throws Exception {
    Path directory = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX);
    try {
      Path classes = compileBean(directory);
      try (URLClassLoader loader = beanLoader(classes)) {
        Class<?> beanClass = loader.loadClass(PACKAGE_NAME + "." + CLASS_NAME);
        Object bean = beanClass.getDeclaredConstructor().newInstance();

        assertJackson2AnnotationIsPresent(beanClass);
        assertThat(writeWithJackson2(bean)).isEqualTo(JACKSON_2_OUTPUT);

        assertThat(new ToJsonFilter().filter(bean, interpreter))
          .isEqualTo(JACKSON_3_OUTPUT);
        assertThat(new PrettyPrintFilter().filter(bean, interpreter).toString())
          .contains("&quot;value&quot; : 42");
      }
    } finally {
      deleteRecursively(directory);
    }
    assertThat(Files.exists(directory)).isFalse();
  }

  private static Path compileBean(Path directory) throws IOException, URISyntaxException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler)
      .describedAs("a JDK is required: no system Java compiler is available")
      .isNotNull();

    Path sourceDirectory = Files.createDirectories(
      directory.resolve("src").resolve(PACKAGE_NAME)
    );
    Path source = sourceDirectory.resolve(CLASS_NAME + ".java");
    Files.write(source, BEAN_SOURCE.getBytes(StandardCharsets.UTF_8));
    Path classes = Files.createDirectories(directory.resolve("classes"));

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(
        diagnostics,
        null,
        StandardCharsets.UTF_8
      )
    ) {
      List<String> options = Arrays.asList(
        "-classpath",
        jackson2ClassPath(),
        "-d",
        classes.toString()
      );
      boolean compiled = compiler
        .getTask(
          null,
          fileManager,
          diagnostics,
          options,
          null,
          fileManager.getJavaFileObjects(source)
        )
        .call();
      assertThat(compiled).describedAs(describe(diagnostics)).isTrue();
    }
    return classes;
  }

  private static String describe(DiagnosticCollector<JavaFileObject> diagnostics) {
    StringBuilder sb = new StringBuilder(
      "compiling the Jackson 2 annotated bean failed:"
    );
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      sb.append('\n').append(diagnostic);
    }
    return sb.toString();
  }

  /** The Jackson 2 jars on this test's class path, found through classes they contain. */
  private static String jackson2ClassPath() throws URISyntaxException {
    List<String> entries = new ArrayList<>();
    for (String className : JACKSON_2_CLASSES) {
      Class<?> type = loadClass(className);
      URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
      String entry = Path.of(location).toString();
      if (!entries.contains(entry)) {
        entries.add(entry);
      }
    }
    return String.join(System.getProperty("path.separator"), entries);
  }

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new AssertionError(
        "Jackson 2 is expected on the test class path: " + className,
        e
      );
    }
  }

  private static URLClassLoader beanLoader(Path classes) throws IOException {
    URL[] urls = new URL[] { classes.toUri().toURL() };
    return new URLClassLoader(
      urls,
      Jackson2DatabindAnnotationBreakTest.class.getClassLoader()
    );
  }

  /** Checks by reflection that the annotation is on the getter, so the test cannot pass vacuously. */
  private static void assertJackson2AnnotationIsPresent(Class<?> beanClass)
    throws NoSuchMethodException {
    Method getter = beanClass.getMethod(GETTER_NAME);
    List<String> annotationTypes = new ArrayList<>();
    for (Annotation annotation : getter.getAnnotations()) {
      annotationTypes.add(annotation.annotationType().getName());
    }
    assertThat(annotationTypes).contains(JACKSON_2_ANNOTATION_CLASS);
  }

  /** Writes the bean with a plain Jackson 2 mapper, to show the annotation has an effect there. */
  private static String writeWithJackson2(Object bean) throws Exception {
    Class<?> mapperClass = loadClass(JACKSON_2_MAPPER_CLASS);
    Object mapper = mapperClass.getDeclaredConstructor().newInstance();
    return (String) mapperClass
      .getMethod("writeValueAsString", Object.class)
      .invoke(mapper, bean);
  }

  private static void deleteRecursively(Path directory) throws IOException {
    try (Stream<Path> paths = Files.walk(directory)) {
      paths
        .sorted(Comparator.reverseOrder())
        .forEach(path -> {
          try {
            Files.delete(path);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
    }
  }
}
