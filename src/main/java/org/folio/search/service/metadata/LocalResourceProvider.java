package org.folio.search.service.metadata;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.service.LocalFileProvider;
import org.folio.search.utils.JsonConverter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class LocalResourceProvider implements MetadataResourceProvider {

  private final JsonConverter jsonConverter;
  private final LocalFileProvider localFileProvider;
  private final ResourcePatternResolver patternResolver;

  @Override
  public List<ResourceDescription> getResourceDescriptions() {
    var pattern = "classpath*:/model/*.json";
    try {
      return Stream.of(patternResolver.getResources(pattern))
        .filter(Resource::isReadable)
        .map(this::getResourceDescription)
        .filter(Objects::nonNull)
        .collect(toList());
    } catch (IOException e) {
      log.error("Failed to read models [pattern: {}]", pattern);
      throw new ResourceDescriptionException(String.format(
        "Failed to read local files [pattern: %s]", pattern), e);
    }
  }

  @Override
  public Map<String, SearchFieldType> getSearchFieldTypes() {
    var path = "elasticsearch/index-field-types.json";
    Map<String, SearchFieldType> fieldTypes =
      localFileProvider.readAsObject(path, new TypeReference<>() {});
    if (fieldTypes == null) {
      throw new ResourceDescriptionException(String.format(
        "Failed to load search field types [path: %s]", path));
    }
    return fieldTypes;
  }

  private ResourceDescription getResourceDescription(Resource resource) {
    try (var is = resource.getInputStream()) {
      return jsonConverter.readJson(is, ResourceDescription.class);
    } catch (IOException e) {
      var filename = resource.getFilename();
      log.error("Failed to read resource [file: {}]", filename, e);
      throw new ResourceDescriptionException(String.format(
        "Failed to read resource [file: %s]", filename), e);
    }
  }
}
