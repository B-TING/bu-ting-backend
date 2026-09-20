package com.butingbe.global.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Swagger UI가 읽는 API 명세가 깨지지 않았는지 지킨다.
 *
 * <p>{@code openapi3.yaml}은 REST Docs가 생성하지 않고 사람이 직접 고치는 파일이다. 머지 충돌을 잘못 풀면 키가 중복되거나 구조가 어긋나는데,
 * 그대로 배포되면 Swagger 화면 전체가 파싱 오류로 뜨지 않는다. 실제로 그렇게 깨진 적이 있어 검사를 남긴다.
 */
class OpenApiSpecTest {

  private static final Path SPEC = Path.of("src/main/resources/static/docs/openapi3.yaml");
  private static final Pattern SCHEMA_REF = Pattern.compile("#/components/schemas/([A-Za-z0-9_]+)");

  @Test
  @DisplayName("명세는 중복 키 없이 파싱된다")
  void parsesWithoutDuplicateKeys() throws Exception {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setCodePointLimit(Integer.MAX_VALUE);
    Yaml yaml = new Yaml(new SafeConstructor(options));

    Map<String, Object> spec;
    try (InputStream in = Files.newInputStream(SPEC)) {
      spec = yaml.load(in);
    }

    assertThat(spec).containsKeys("openapi", "paths", "components");
  }

  @Test
  @DisplayName("참조한 스키마가 모두 정의돼 있다")
  void everySchemaReferenceIsDefined() throws Exception {
    String raw = Files.readString(SPEC);
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setCodePointLimit(Integer.MAX_VALUE);

    @SuppressWarnings("unchecked")
    Map<String, Object> spec = new Yaml(new SafeConstructor(options)).load(raw);
    @SuppressWarnings("unchecked")
    Map<String, Object> components = (Map<String, Object>) spec.get("components");
    @SuppressWarnings("unchecked")
    Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

    Matcher matcher = SCHEMA_REF.matcher(raw);
    Set<String> referenced =
        matcher.results().map(result -> result.group(1)).collect(Collectors.toSet());

    assertThat(schemas.keySet()).containsAll(referenced);
  }
}
