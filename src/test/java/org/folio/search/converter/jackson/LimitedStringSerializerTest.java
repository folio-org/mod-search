package org.folio.search.converter.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.converter.jackson.LimitedStringSerializer.MAX_BYTES;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LimitedStringSerializerTest {

  private final LimitedStringSerializer serializer = new LimitedStringSerializer();

  @Mock
  private JsonGenerator jsonGenerator;

  @Mock
  private SerializerProvider provider;

  @Test
  void serialize_positive_nullValue() throws IOException {
    serializer.serialize(null, jsonGenerator, provider);
    verify(jsonGenerator).writeNull();
  }

  @Test
  void serialize_positive_emptyString() throws IOException {
    serializer.serialize("", jsonGenerator, provider);
    verify(jsonGenerator).writeString("");
  }

  @Test
  void serialize_positive_shortString() throws IOException {
    String input = "Hello World";
    serializer.serialize(input, jsonGenerator, provider);
    verify(jsonGenerator).writeString(input);
  }

  @Test
  void serialize_positive_stringAtSafeLimit() throws IOException {
    String input = StringUtils.repeat("a", 8000); // Safe character limit is MAX_BYTES/4 = 8000
    serializer.serialize(input, jsonGenerator, provider);
    verify(jsonGenerator).writeString(input);
  }

  @Test
  void serialize_positive_asciiStringAtByteLimit() throws IOException {
    String input = StringUtils.repeat("a", MAX_BYTES); // Each ASCII char is 1 byte
    serializer.serialize(input, jsonGenerator, provider);
    verify(jsonGenerator).writeString(input);
  }

  @Test
  void serialize_positive_unicodeStringExceedingByteLimit() throws IOException {
    // Using a Unicode character that takes 3 bytes in UTF-8
    String unicodeChar = "€"; // Euro symbol
    int charCount = 15000; // Will exceed MAX_BYTES bytes (15000 * 3 = 45000 bytes)
    String input = StringUtils.repeat(unicodeChar, charCount);

    serializer.serialize(input, jsonGenerator, provider);

    // Verify the string was truncated to fit within MAX_BYTES bytes
    verify(jsonGenerator).writeString(input.substring(0, 10666)); // ~MAX_BYTES/3 characters
  }

  @Test
  void serialize_positive_mixedContentExceedingByteLimit() throws IOException {
    // Mix of ASCII and multi-byte Unicode characters
    String repeatingPattern = "Hello€世界"; // Mix of 1-byte, 3-byte, and 3-byte chars
    int patternByteLength = repeatingPattern.getBytes(StandardCharsets.UTF_8).length;
    int repetitions = MAX_BYTES / patternByteLength + 1; // Ensure we exceed the byte limit
    String input = StringUtils.repeat(repeatingPattern, repetitions);

    serializer.serialize(input, jsonGenerator, provider);

    // Verify the truncated string's byte length is within limits
    var captor = ArgumentCaptor.forClass(String.class);
    verify(jsonGenerator).writeString(captor.capture());
    assertThat(captor.getValue().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(MAX_BYTES);
  }
}
