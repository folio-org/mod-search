package org.folio.search.service;

import static java.util.stream.Collectors.toList;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.NONE_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchDocumentConverter {

  private final ObjectMapper objectMapper;
  private final ParseContext parseContext;
  private final JsonConverter jsonConverter;
  private final ResourceDescriptionService descriptionService;

  /**
   * Converts list of {@link ResourceEventBody} object to the list of {@link SearchDocumentBody}
   * objects.
   *
   * @param resourceEvents list with resource events for conversion to elasticsearch document
   * @return list with elasticsearch documents.
   */
  public List<SearchDocumentBody> convert(List<ResourceEventBody> resourceEvents) {
    return resourceEvents.stream()
      .map(this::convert)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(toList());
  }

  /**
   * Converts single {@link ResourceEventBody} object to the {@link SearchDocumentBody} object with
   * all required data for elasticsearch index operation.
   *
   * @param event resource event body for conversion
   * @return elasticsearch document
   */
  public Optional<SearchDocumentBody> convert(ResourceEventBody event) {
    var newData = event.getNew();
    if (newData == null) {
      return Optional.empty();
    }
    var newDataJson = jsonConverter.toJsonTree(newData);
    var document = parseContext.parse(newDataJson.toString());
    var resourceName = event.getResourceName();
    var resourceDescription = descriptionService.get(event.getResourceName());
    var fields = resourceDescription.getFields();

    var conversionContext = ConversionContext.of(getResourceLanguages(resourceName, document));
    ObjectNode objectNode = convertDocument(document, fields, conversionContext);

    return Optional.of(SearchDocumentBody.builder()
      .id(newDataJson.path("id").textValue())
      .index(getElasticsearchIndexName(event.getResourceName(), event.getTenant()))
      .routing(event.getTenant())
      .rawJson(jsonConverter.toJson(objectNode))
      .build());
  }

  private static boolean isMultilangField(PlainFieldDescription desc) {
    return MULTILANG_FIELD_TYPE.equals(desc.getIndex());
  }

  private static boolean isNotIndexedField(PlainFieldDescription desc) {
    return NONE_FIELD_TYPE.equals(desc.getIndex());
  }

  private static JsonNode getJsonNodeByPath(DocumentContext doc, String sourcePath) {
    try {
      return doc.read(sourcePath, JsonNode.class);
    } catch (PathNotFoundException e) {
      if (log.isDebugEnabled()) {
        log.debug("Path not found [jsonPath: {}, doc: {}]", sourcePath, doc, e);
      }
      return null;
    }
  }

  private static Stream<String> getStreamFromJson(JsonNode jsonNode) {
    if (jsonNode == null) {
      return Stream.empty();
    }
    if (jsonNode.isArray()) {
      var builder = Stream.<String>builder();
      for (JsonNode node : jsonNode) {
        if (isTextNode(node)) {
          builder.add(node.asText());
        }
      }
      return builder.build();
    }
    if (isTextNode(jsonNode)) {
      return Stream.of(jsonNode.asText());
    }
    return Stream.empty();
  }

  private static boolean isTextNode(JsonNode jsonNode) {
    return jsonNode.isTextual() && jsonNode.asText() != null;
  }

  private List<String> getResourceLanguages(String resourceName, DocumentContext doc) {
    var languageSourcePaths = descriptionService.getLanguageSourcePaths(resourceName);
    return languageSourcePaths.stream()
      .map(sourcePath -> getJsonNodeByPath(doc, sourcePath))
      .flatMap(SearchDocumentConverter::getStreamFromJson)
      .distinct()
      .filter(descriptionService::isSupportedLanguage)
      .collect(toList());
  }

  private ObjectNode convertDocument(
    DocumentContext doc, Map<String, FieldDescription> fields, ConversionContext ctx) {
    var objectNode = objectMapper.createObjectNode();
    for (var fieldEntry : fields.entrySet()) {
      var jsonNode = getFieldValueAsJsonNode(doc, fieldEntry.getValue(), ctx);
      if (jsonNode != null) {
        objectNode.set(fieldEntry.getKey(), jsonNode);
      }
    }
    return objectNode.isEmpty() ? null : objectNode;
  }

  private JsonNode getFieldValueAsJsonNode(
    DocumentContext doc, FieldDescription desc, ConversionContext ctx) {
    if (desc instanceof PlainFieldDescription) {
      return getPlainFieldValue(doc, (PlainFieldDescription) desc, ctx);
    }

    var objectFieldDescription = (ObjectFieldDescription) desc;
    return convertDocument(doc, objectFieldDescription.getProperties(), ctx);
  }

  private JsonNode getPlainFieldValue(
    DocumentContext doc, PlainFieldDescription desc, ConversionContext ctx) {
    if (isNotIndexedField(desc)) {
      return null;
    }
    JsonNode jsonNodes = getJsonNodeByPath(doc, desc.getSourcePath());
    return isMultilangField(desc) ? getMultilangValueAsJsonNode(jsonNodes, ctx) : jsonNodes;
  }

  private ObjectNode getMultilangValueAsJsonNode(JsonNode jsonNodes, ConversionContext ctx) {
    if (jsonNodes != null) {
      var objectNode = objectMapper.createObjectNode();
      for (var language : ctx.getLanguages()) {
        objectNode.set(language, jsonNodes);
      }

      objectNode.set("src", jsonNodes);
      return objectNode;
    }

    return null;
  }

  /**
   * The conversion context object.
   */
  @Getter
  @RequiredArgsConstructor(staticName = "of")
  private static class ConversionContext {

    /**
     * List of supported language for resource.
     */
    private final List<String> languages;
  }
}
