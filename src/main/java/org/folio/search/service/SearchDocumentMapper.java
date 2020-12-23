package org.folio.search.service;

import static java.util.stream.Collectors.toList;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.NONE_FIELD_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDocumentMapper {

  private static final TypeRef<List<Object>> STRING_LIST_TYPE_REF = new TypeRef<>() {};

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
   * Converts single {@link ResourceEventBody} object to the {@link SearchDocumentBody} object with all
   * required data for elasticsearch index operation.
   *
   * @param event resource event body for conversion
   * @return elasticsearch document
   */
  public Optional<SearchDocumentBody> convert(ResourceEventBody event) {
    var newData = event.getNewData();
    if (newData == null) {
      return Optional.empty();
    }

    var document = parseContext.parse(newData.toString());
    var resourceName = event.getResourceName();
    var resourceDescription = descriptionService.get(event.getResourceName());
    var fields = resourceDescription.getFields();

    var conversionContext = ConversionContext.of(getResourceLanguages(resourceName, document));
    ObjectNode objectNode = convertDocument(document, fields, conversionContext);

    return Optional.of(SearchDocumentBody.builder()
        .id(newData.path("id").textValue())
        .index(getIndexName(event))
        .routing(event.getTenant())
        .rawJson(jsonConverter.toJson(objectNode))
        .build());
  }

  private List<String> getResourceLanguages(String resourceName, DocumentContext doc) {
    var languageSourcePaths = descriptionService.getLanguageSourcePaths(resourceName);
    return languageSourcePaths.stream()
        .map(sourcePath -> getStringListByJsonPath(doc, sourcePath))
        .flatMap(Collection::stream)
        .filter(value -> value instanceof String)
        .map(value -> (String) value)
        .collect(Collectors.toList());
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
    List<Object> fieldValues = getStringListByJsonPath(doc, desc.getSourcePath());
    var jsonNodes = getFieldValueAsJsonNode(fieldValues);
    return isMultilangField(desc) ? getMultilangValueAsJsonNode(jsonNodes, ctx) : jsonNodes;
  }

  private ObjectNode getMultilangValueAsJsonNode(JsonNode jsonNodes, ConversionContext ctx) {
    if (jsonNodes != null) {
      var objectNode = objectMapper.createObjectNode();
      for (var language : ctx.getLanguages()) {
        if (descriptionService.isSupportedLanguage(language)) {
          objectNode.set(language, jsonNodes);
        }
      }

      objectNode.set("source", jsonNodes);
      return objectNode;
    }

    return null;
  }

  private JsonNode getFieldValueAsJsonNode(List<Object> fieldValues) {
    if (CollectionUtils.isEmpty(fieldValues)) {
      return null;
    }

    if (fieldValues.size() == 1) {
      return objectMapper.valueToTree(fieldValues.get(0));
    }

    var arrayNode = objectMapper.createArrayNode();
    fieldValues.forEach(value -> arrayNode.add(objectMapper.valueToTree(value)));
    return arrayNode;
  }

  private static String getIndexName(ResourceEventBody eventBody) {
    return eventBody.getResourceName() + "_" + eventBody.getTenant();
  }

  private static boolean isMultilangField(PlainFieldDescription desc) {
    return MULTILANG_FIELD_TYPE.equals(desc.getIndex());
  }

  private static boolean isNotIndexedField(PlainFieldDescription desc) {
    return NONE_FIELD_TYPE.equals(desc.getIndex());
  }


  private static List<Object> getStringListByJsonPath(DocumentContext doc, String sourcePath) {
    try {
      return doc.read(sourcePath, STRING_LIST_TYPE_REF);
    } catch (PathNotFoundException e) {
      if (log.isDebugEnabled()) {
        log.debug("Path not found [jsonPath: {}, doc: {}]", sourcePath, doc, e);
      }
      return Collections.emptyList();
    }
  }

  @Getter
  @RequiredArgsConstructor(staticName = "of")
  private static class ConversionContext {

    private final List<String> languages;
  }
}
