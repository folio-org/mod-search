package org.folio.search.converter.jackson;

import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * A custom serializer for {@link String} that enforces a byte size limit
 * on the serialized string to ensure it does not exceed a predefined maximum.
 *
 * <p>
 * This serializer truncates strings as needed to prevent serialized strings
 * from exceeding 32,000 bytes when encoded in UTF-8. The truncation is based
 * on estimated safe character limits and actual byte calculations.
 *
 * <p>
 * If the input string's length exceeds a safe character limit, its UTF-8 encoded
 * byte size is analyzed. If the encoded size exceeds the maximum allowed byte size,
 * a warning will be logged and the string will be truncated accordingly.
 *
 * <p>
 * This class is designed to be used in Jackson serialization processes, typically
 * registered with a `SimpleModule` (e.g., {@link StringLimitModule}).
 */
@Log4j2
public class LimitedStringSerializer extends StdSerializer<String> {

  static final int MAX_BYTES = 32000;
  private static final int MAX_UTF8_BYTES_PER_CHAR = 4;
  private static final int SAFE_CHAR_LIMIT = MAX_BYTES / MAX_UTF8_BYTES_PER_CHAR;

  public LimitedStringSerializer() {
    super(String.class);
  }

  @Override
  public void serialize(String value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
    if (value == null) {
      gen.writeNull();
      return;
    }

    gen.writeString(truncateIfNeeded(value));
  }

  private String truncateIfNeeded(String value) {
    if (value.length() <= SAFE_CHAR_LIMIT) {
      return value;
    }

    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= MAX_BYTES) {
      return value;
    }

    log.warn("String value is too long, it will be truncated to fit within the byte limit.");
    int low = 0;
    int high = value.length();
    int result = 0;
    while (low <= high) {
      int mid = (low + high) / 2;
      int byteLen = value.substring(0, mid).getBytes(StandardCharsets.UTF_8).length;
      if (byteLen <= MAX_BYTES) {
        result = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return value.substring(0, result);
  }
}


