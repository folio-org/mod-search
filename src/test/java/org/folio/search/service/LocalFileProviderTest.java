package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LocalFileProviderTest {

  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  @InjectMocks private LocalFileProvider resourceService;

  @Test
  void read_positive() {
    var actual = resourceService.read("test-resources/test-file.txt");
    assertThat(actual).isEqualTo("test-data");
  }

  @Test
  void read_negative_fileNotFound() {
    var actual = resourceService.read("test-resources/unknown-file.txt");
    assertThat(actual).isNull();
  }

  @Test
  void readAsObject_positive() {
    var actual = resourceService.readAsObject("test-resources/test-json.json");
    var expected = jsonObject("key", "value");
    assertThat(actual).isEqualTo(expected);
    verify(jsonConverter).asJsonTree(any(InputStream.class));
  }

  @Test
  void readAsObjectForClass_positive() {
    var actual = resourceService.readAsObject("test-resources/test-json.json", TestType.class);
    assertThat(actual).isEqualTo(TestType.of("value"));
    verify(jsonConverter).readJson(any(InputStream.class), eq(TestType.class));
  }


  @Test
  void readAsObjectForType_positive() {
    var mapType = new TypeReference<Map<String, String>>() {};
    var actual = resourceService.readAsObject("test-resources/test-json.json", mapType);
    assertThat(actual).isEqualTo(Map.of("key", "value"));
    verify(jsonConverter).readJson(any(InputStream.class), eq(mapType));
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(staticName = "of")
  private static class TestType {

    private String key;
  }
}
