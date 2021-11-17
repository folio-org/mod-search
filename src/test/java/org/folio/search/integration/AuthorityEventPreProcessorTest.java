package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.standardField;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityIdentifiers;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.metadata.AuthorityPlainFieldDescription;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AuthorityEventPreProcessorTest {

  private static final String AUTHORITY_ID = randomId();
  private static final List<String> CORPORATE_NAME_FIELDS = fieldsWithPrefixes("corporateName", "sft", "saft");
  private static final List<String> PERSONAL_NAME_FIELDS = fieldsWithPrefixes("personalName", "sft", "saft");
  private static final List<String> UNIFORM_TITLE_FIELDS = fieldsWithPrefixes("uniformTitle", "sft", "saft");

  @InjectMocks private AuthorityEventPreProcessor eventPreProcessor;
  @Mock private ResourceDescriptionService resourceDescriptionService;

  @BeforeEach
  void setUp() {
    when(resourceDescriptionService.get(AUTHORITY_RESOURCE)).thenReturn(authorityResourceDescription());
    eventPreProcessor.init();
  }

  @Test
  void process_positive() {
    var body = toMap(fullAuthorityRecord());

    var actual = eventPreProcessor.process(resourceEvent(AUTHORITY_RESOURCE, body));

    assertThat(getGeneratedEventIds(actual)).hasSize(3);
    assertThat(withClearGeneratedEventIds(actual)).isEqualTo(List.of(
      expectedResourceEvent(eventWithoutFields(body, flatList(CORPORATE_NAME_FIELDS, UNIFORM_TITLE_FIELDS))),
      expectedResourceEvent(eventWithoutFields(body, flatList(PERSONAL_NAME_FIELDS, UNIFORM_TITLE_FIELDS))),
      expectedResourceEvent(eventWithoutFields(body, flatList(PERSONAL_NAME_FIELDS, CORPORATE_NAME_FIELDS)))));
  }

  @Test
  void process_positive_onlyPersonalIsPopulated() {
    var body = toMap(new Authority().id(AUTHORITY_ID).personalName("a personal name"));
    var actual = eventPreProcessor.process(resourceEvent(AUTHORITY_RESOURCE, body));
    assertThat(withClearGeneratedEventIds(actual)).isEqualTo(List.of(expectedResourceEvent(body)));
  }

  @Test
  void process_positive_onlyCommonFieldsArePopulated() {
    var body = toMap(new Authority().id(AUTHORITY_ID)
      .subjectHeadings("a subject headings")
      .identifiers(List.of(new AuthorityIdentifiers().value("an authority identifier"))));
    var resourceEvent = resourceEvent(AUTHORITY_RESOURCE, body);
    var actual = eventPreProcessor.process(resourceEvent);

    assertThat(withClearGeneratedEventIds(actual)).isEqualTo(List.of(expectedResourceEvent(body)));
  }

  private static ResourceEvent expectedResourceEvent(Map<String, Object> expectedAuthority) {
    return resourceEvent(null, AUTHORITY_RESOURCE, expectedAuthority);
  }

  private static List<String> getGeneratedEventIds(List<ResourceEvent> actual) {
    return actual.stream().map(ResourceEvent::getId).filter(Objects::nonNull).collect(toList());
  }

  private static List<ResourceEvent> withClearGeneratedEventIds(List<ResourceEvent> actual) {
    return actual.stream().map(event -> event.id(null)).collect(toList());
  }

  private static Authority fullAuthorityRecord() {
    return new Authority()
      .id(AUTHORITY_ID)
      .personalName("a personal name")
      .sftPersonalName(List.of("a sft personal name"))
      .saftPersonalName(List.of("a saft personal name"))
      .corporateName("a corporate name")
      .sftCorporateName(List.of("a sft corporate name"))
      .saftCorporateName(List.of("a saft corporate name"))
      .uniformTitle("an uniform title")
      .sftUniformTitle(List.of("a sft uniform title"))
      .saftUniformTitle(List.of("a saft uniform title"))
      .subjectHeadings("a subject heading")
      .identifiers(List.of(new AuthorityIdentifiers()
        .value("an identifier value")
        .identifierTypeId("an identifier type id")));
  }

  private static Map<String, Object> eventWithoutFields(Map<String, Object> body, List<String> fields) {
    var resultEventBody = new LinkedHashMap<>(body);
    for (var field : fields) {
      resultEventBody.remove(field);
    }
    return resultEventBody;
  }

  private static ResourceDescription authorityResourceDescription() {
    var resourceDescription = TestUtils.resourceDescription(AUTHORITY_RESOURCE);
    resourceDescription.setFields(mapOf(
      "id", keywordField(),
      "subjectHeadings", standardField(),
      "identifiers", objectField(mapOf("identifierTypeId", keywordField(), "value", keywordField()))));
    var fields = resourceDescription.getFields();
    PERSONAL_NAME_FIELDS.forEach(field -> fields.put(field, distinctiveField(PERSONAL_NAME_FIELDS.get(0))));
    CORPORATE_NAME_FIELDS.forEach(field -> fields.put(field, distinctiveField(CORPORATE_NAME_FIELDS.get(0))));
    UNIFORM_TITLE_FIELDS.forEach(field -> fields.put(field, distinctiveField(UNIFORM_TITLE_FIELDS.get(0))));
    return resourceDescription;
  }

  private static FieldDescription distinctiveField(String distinctType) {
    var fieldDescription = new AuthorityPlainFieldDescription();
    fieldDescription.setDistinctType(distinctType);
    fieldDescription.setIndex(STANDARD_FIELD_TYPE);
    return fieldDescription;
  }

  @SafeVarargs
  private static <T> List<T> flatList(List<T>... lists) {
    return Arrays.stream(lists)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private static List<String> fieldsWithPrefixes(String fieldName, String... prefixes) {
    var fields = new ArrayList<String>();
    fields.add(fieldName);
    for (var prefix : prefixes) {
      fields.add(prefix + capitalize(fieldName));
    }
    return fields;
  }
}
