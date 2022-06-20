package org.folio.search.service.metadata;

import static java.util.stream.Collectors.toUnmodifiableList;

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
import org.folio.search.utils.JsonConverter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class LocalResourceProvider implements MetadataResourceProvider {

  public static final String RESOURCE_DESCRIPTIONS_LOCATION_PATTERN = "classpath*:/model/*.json";
  public static final String INDEX_FIELD_TYPES_LOCATION = "elasticsearch/index-field-types.json";

  private final JsonConverter jsonConverter;
  private final LocalFileProvider localFileProvider;
  private final ResourcePatternResolver patternResolver;
  private List<ResourceDescription> resourceDescriptions;

  @Override
  public List<ResourceDescription> getResourceDescriptions() {
    if (this.resourceDescriptions == null) {
      loadResourceDescriptions();
    }

    return this.resourceDescriptions;
  }

  @Override
  public Map<String, SearchFieldType> getSearchFieldTypes() {
    var typeReference = new TypeReference<Map<String, SearchFieldType>>() { };
    var fieldTypes = localFileProvider.readAsObject(INDEX_FIELD_TYPES_LOCATION, typeReference);
    if (fieldTypes == null) {
      throw new ResourceDescriptionException(String.format(
        "Failed to load search field types [path: %s]", INDEX_FIELD_TYPES_LOCATION));
    }
    return fieldTypes;
  }

  private void loadResourceDescriptions() {
    try {
      this.resourceDescriptions = Stream.of(patternResolver.getResources(RESOURCE_DESCRIPTIONS_LOCATION_PATTERN))
        .filter(Resource::isReadable)
        .map(this::loadResourceDescription)
        .filter(Objects::nonNull)
        .collect(toUnmodifiableList());
    } catch (IOException e) {
      log.error("Failed to read models [pattern: {}]", RESOURCE_DESCRIPTIONS_LOCATION_PATTERN);
      throw new ResourceDescriptionException(String.format(
        "Failed to read local files [pattern: %s]", RESOURCE_DESCRIPTIONS_LOCATION_PATTERN), e);
    }
  }

  private ResourceDescription loadResourceDescription(Resource resource) {
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
