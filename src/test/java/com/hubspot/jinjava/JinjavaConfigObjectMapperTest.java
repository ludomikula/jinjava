package com.hubspot.jinjava;

import static org.assertj.core.api.Assertions.assertThat;

import com.hubspot.jinjava.testobjects.GoldenTestObjects.AcronymBean;
import com.hubspot.jinjava.testobjects.GoldenTestObjects.CamelBean;
import org.junit.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JinjavaConfigObjectMapperTest {

  private static final String CAMEL_BEAN_CAMEL_CASE_JSON =
    "{\"lastName\":\"Doe\",\"firstName\":\"Jane\",\"httpStatusCode\":200}";
  private static final String CAMEL_BEAN_SNAKE_CASE_JSON =
    "{\"last_name\":\"Doe\",\"first_name\":\"Jane\",\"http_status_code\":200}";

  private static JinjavaConfig config(boolean snakeCase) {
    return JinjavaConfig
      .newBuilder()
      .withLegacyOverrides(
        LegacyOverrides.newBuilder().withUseSnakeCasePropertyNaming(snakeCase).build()
      )
      .build();
  }

  @Test
  public void itDefaultsToAJackson3Mapper() {
    ObjectMapper mapper = config(false).getObjectMapper();
    assertThat(mapper).isInstanceOf(JsonMapper.class);
    assertThat(mapper.getClass().getName()).startsWith("tools.jackson.databind.");
  }

  @Test
  public void itKeepsOneDefaultMapperPerConfig() {
    JinjavaConfig config = config(false);
    assertThat(config.getObjectMapper()).isSameAs(config.getObjectMapper());
  }

  @Test
  public void itKeepsPropertyNamesByDefault() {
    assertThat(config(false).getObjectMapper().writeValueAsString(new CamelBean()))
      .isEqualTo(CAMEL_BEAN_CAMEL_CASE_JSON);
  }

  @Test
  public void itUsesSnakeCaseNamingWhenTheLegacyOverrideAsksForIt() {
    assertThat(config(true).getObjectMapper().writeValueAsString(new CamelBean()))
      .isEqualTo(CAMEL_BEAN_SNAKE_CASE_JSON);
  }

  @Test
  public void itAppliesJackson2AccessorNamingToTheDefaultMapper() {
    assertThat(config(false).getObjectMapper().writeValueAsString(new AcronymBean()))
      .contains("\"url\"")
      .contains("\"xvalue\"");
  }

  @Test
  public void itUsesTheMapperPassedToTheBuilder() {
    ObjectMapper custom = JsonMapper.builder().build();
    assertThat(
      JinjavaConfig.newBuilder().withObjectMapper(custom).build().getObjectMapper()
    )
      .isSameAs(custom);
  }
}
