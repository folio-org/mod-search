package org.folio.search.service.metadata;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class LocalFileProvider {

  private static final ClassLoader CLASSLOADER = LocalFileProvider.class.getClassLoader();
  private final JsonConverter jsonConverter;

  /**
   * Reads file from resources folder as {@link String} object.
   *
   * @param path path to file as {@link String} object
   * @return file content as {@link String} object
   */
  public String read(String path) {
    return readString(path);
  }

  /**
   * Reads file from resources folder as {@link JsonNode} object.
   *
   * @param path path to file as {@link String} object
   * @return file content as {@link JsonNode} object
   */
  @SuppressWarnings("unused")
  public JsonNode readAsObject(String path) {
    return jsonConverter.asJsonTree(CLASSLOADER.getResourceAsStream(path));
  }

  /**
   * Reads file from resources folder as {@link JsonNode} object.
   *
   * @param path path to file as {@link String} object
   * @param type target class for conversion value from json
   * @param <T>  generic type for response object
   * @return file content as {@link JsonNode} object
   */
  public <T> T readAsObject(String path, Class<T> type) {
    return jsonConverter.readJson(CLASSLOADER.getResourceAsStream(path), type);
  }

  /**
   * Reads file from resources folder as {@link JsonNode} object.
   *
   * @param path          path to file as {@link String} object
   * @param typeReference type reference for conversion value from json
   * @param <T>           generic type for response object
   * @return file content as {@link JsonNode} object
   */
  public <T> T readAsObject(String path, TypeReference<T> typeReference) {
    return jsonConverter.readJson(CLASSLOADER.getResourceAsStream(path), typeReference);
  }

  private static String readString(String filename) {
    var url = CLASSLOADER.getResource(filename);
    if (url == null) {
      return null;
    }
    try {
      return IOUtils.toString(url, UTF_8);
    } catch (IOException e) {
      log.warn("Failed to read resource file '{}'", filename, e);
      return null;
    }
  }
}
