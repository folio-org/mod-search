package org.folio.search.service.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.arrayNode;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchMappingsHelperTest {

  @InjectMocks private SearchMappingsHelper mappingsHelper;
  @Mock private ResourceDescriptionService resourceDescriptionService;
  @Mock private SearchFieldProvider searchFieldProvider;
  @Spy private final ObjectMapper objectMapper = new ObjectMapper();
  @Spy private final JsonConverter jsonConverter = new JsonConverter(objectMapper);

  @Test
  void getMappings_positive() {
    var keywordType = fieldType(jsonObject("type", "keyword"));
    var dateType = fieldType(jsonObject("type", "date", "format", "epoch_millis"));
    var multilangType = fieldType(jsonObject(
      "properties", jsonObject(
        "eng", jsonObject("type", "text", "analyzer", "english"),
        "spa", jsonObject("type", "text", "analyzer", "spanish"))));

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription());
    doReturn(keywordType).when(searchFieldProvider).getSearchFieldType("keyword");
    doReturn(multilangType).when(searchFieldProvider).getSearchFieldType("multilang");
    doReturn(dateType).when(searchFieldProvider).getSearchFieldType("date");

    var actual = mappingsHelper.getMappings(RESOURCE_NAME);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "_routing", jsonObject("required", true),
      "properties", jsonObject(
        "id", keywordType.getMapping(),
        "title", multilangType.getMapping(),
        "subtitle", jsonObject("type", "text"),
        "isbn", jsonObject("type", "keyword", "normalizer", "lowercase_normalizer"),
        "metadata", jsonObject("properties", jsonObject("createdDate", dateType.getMapping())),
      "identifiers", keywordType.getMapping()
    ))));
  }

  @Test
  void getMappings_positive_resourceWithIndexMappings() {
    var keywordType = fieldType(jsonObject("type", "keyword"));
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField("keyword", jsonObject("copy_to", arrayNode("id_copy")))));
    var idCopyMappings = jsonObject("type", "keyword", "normalizer", "lowercase");
    resourceDescription.setIndexMappings(mapOf("id_copy", idCopyMappings));

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription);
    when(searchFieldProvider.getSearchFieldType("keyword")).thenReturn(keywordType);

    var actual = mappingsHelper.getMappings(RESOURCE_NAME);
    assertThat(actual).isEqualTo(asJsonString(jsonObject(
      "date_detection", false,
      "numeric_detection", false,
      "_routing", jsonObject("required", true),
      "properties", jsonObject(
        "id", jsonObject("type", "keyword", "copy_to", arrayNode("id_copy")),
        "id_copy", idCopyMappings
      ))));
  }

  private static ResourceDescription resourceDescription() {
    var resourceDescription = TestUtils.resourceDescription(mapOf(
      "id", plainField("keyword"),
      "issn", identifiersGroup(),
      "title", plainField("multilang"),
      "subtitle", plainField(null, jsonObject("type", "text")),
      "not_indexed_field1", plainField("none"),
      "not_indexed_field2", plainField(null),
      "isbn", plainField("keyword", jsonObject("normalizer", "lowercase_normalizer")),
      "metadata", objectField(mapOf(
        "createdDate", plainField("date")
      ))));
    resourceDescription.setGroups(mapOf("identifiers", plainField("keyword")));
    return resourceDescription;
  }

  private static PlainFieldDescription identifiersGroup() {
    var groupField = plainField("keyword");
    groupField.setGroup(List.of("identifiers"));
    return groupField;
  }

  private static SearchFieldType fieldType(ObjectNode mappings) {
    return SearchFieldType.of(mappings);
  }
}
