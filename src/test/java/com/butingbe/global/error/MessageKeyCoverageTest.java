package com.butingbe.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.ai.TravelPlanValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사용자에게 나가는 메시지 키가 4개 언어에 모두 있는지 본다.
 *
 * <p>키가 빠져 있어도 아무것도 터지지 않는다. {@code messageSource.getMessage(code, null, code, locale)} 가 키 자체를
 * 기본값으로 돌려주기 때문에, 사용자 화면에 {@code error.travel.not_found} 같은 문자열이 그대로 찍힌다. 그래서 테스트로 막는다.
 */
class MessageKeyCoverageTest {

  private static final List<String> BUNDLES =
      List.of(
          "messages.properties",
          "messages_en.properties",
          "messages_ja.properties",
          "messages_zh.properties");

  private static final Pattern MESSAGE_KEY = Pattern.compile("\"(error\\.[a-z0-9_.]+)\"");

  @Test
  @DisplayName("4개 언어 파일의 키 집합이 정확히 같다")
  void allBundlesDeclareTheSameKeys() {
    Set<String> base = keysOf(BUNDLES.getFirst());

    for (String bundle : BUNDLES) {
      Set<String> keys = keysOf(bundle);
      assertThat(new TreeSet<>(base).stream().filter(key -> !keys.contains(key)).toList())
          .describedAs("%s 에 빠진 키", bundle)
          .isEmpty();
      assertThat(new TreeSet<>(keys).stream().filter(key -> !base.contains(key)).toList())
          .describedAs("%s 에만 있는 키", bundle)
          .isEmpty();
    }
  }

  @Test
  @DisplayName("코드가 쓰는 메시지 키는 모두 정의되어 있다")
  void everyKeyUsedInCodeIsDefined() throws IOException {
    Set<String> defined = keysOf(BUNDLES.getFirst());
    List<String> missing = new ArrayList<>();

    for (String key : keysUsedInMainSource()) {
      if (!defined.contains(key)) {
        missing.add(key);
      }
    }

    assertThat(missing)
        .describedAs("코드에서 던지지만 messages.properties 에 없는 키. 빠지면 사용자에게 키 문자열이 그대로 보인다.")
        .isEmpty();
  }

  @Test
  @DisplayName("AI 일정 검증 사유는 이름으로 키를 만들므로 사유마다 키가 있어야 한다")
  void everyAiValidationReasonHasAKey() {
    Set<String> defined = keysOf(BUNDLES.getFirst());

    List<String> missing =
        Arrays.stream(TravelPlanValidationException.Reason.values())
            .map(reason -> "error.travel.ai." + reason.name().toLowerCase(Locale.ROOT))
            .filter(key -> !defined.contains(key))
            .toList();

    assertThat(missing).describedAs("사유 enum 에 대응하는 키가 없다").isEmpty();
  }

  /** 문자열 이어 붙이기로 만드는 접두사(끝이 '.')는 여기서 거르고 사유별 테스트가 따로 본다. */
  private Set<String> keysUsedInMainSource() throws IOException {
    Set<String> keys = new LinkedHashSet<>();
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        Matcher matcher = MESSAGE_KEY.matcher(Files.readString(path));
        while (matcher.find()) {
          String key = matcher.group(1);
          if (!key.endsWith(".")) {
            keys.add(key);
          }
        }
      }
    }
    assertThat(keys).describedAs("메시지 키를 하나도 못 읽었다면 이 테스트는 아무것도 검사하지 않는다").hasSizeGreaterThan(100);
    return keys;
  }

  private Set<String> keysOf(String bundle) {
    Properties properties = new Properties();
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(bundle)) {
      assertThat(stream).describedAs("%s 를 찾을 수 없다", bundle).isNotNull();
      properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(bundle + " 를 읽지 못했다.", e);
    }
    return properties.stringPropertyNames().stream()
        .filter(key -> key.startsWith("error."))
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }
}
