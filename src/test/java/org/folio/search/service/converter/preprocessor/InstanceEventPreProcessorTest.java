package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;
import static org.folio.search.utils.SearchUtils.SOURCE_FIELD;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.repository.classification.InstanceClassificationEntity;
import org.folio.search.repository.classification.InstanceClassificationRepository;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.testing.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceEventPreProcessorTest {

  @SuppressWarnings("unchecked")
  private static final Function<InstanceClassificationEntity, Object>[] ENTITY_FIELD_EXTRACTORS = new Function[] {
    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::type,
    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::number,
    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::tenantId,
    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::instanceId,
    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::shared
  };

  private @Mock FeatureConfigService featureConfigService;
  private @Mock ConsortiumTenantService consortiumTenantService;
  private @Mock InstanceClassificationRepository instanceClassificationRepository;
  private @InjectMocks InstanceEventPreProcessor preProcessor;

  private @Captor ArgumentCaptor<List<InstanceClassificationEntity>> createCaptor;
  private @Captor ArgumentCaptor<List<InstanceClassificationEntity>> deleteCaptor;

  @Test
  void preProcess_ShadowInstance_ShouldNotProcessClassifications() {
    // Arrange
    var data = instance(randomId(), SOURCE_CONSORTIUM_PREFIX + "SOURCE", emptyList());
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, data);

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verifyNoInteractions(instanceClassificationRepository);
  }

  @Test
  void preProcess_FeatureIsDisabled_ShouldNotProcessClassifications() {
    // Arrange
    var data = instance(emptyList());
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, data);
    mockClassificationBrowseFeatureEnabled(Boolean.FALSE);

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verifyNoInteractions(instanceClassificationRepository);
  }

  @Test
  void preProcess_NoChangeToClassifications_ShouldNotProcessClassifications() {
    // Arrange
    var newData = instance(List.of(classification("n1", "t1"), classification("n2", "t2")));
    var oldData = instance(List.of(classification("n2", "t2"), classification("n1", "t1")));
    var resourceEvent = resourceEvent(randomId(), INSTANCE_RESOURCE, UPDATE, newData, oldData);
    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verifyNoInteractions(instanceClassificationRepository);
  }

  @Test
  void preProcess_CreateEvent_ShouldProcessClassifications() {
    // Arrange
    var id = randomId();
    var newData = instance(id, List.of(classification("n1", "t1"), classification("n2", "t2")));
    var resourceEvent = resourceEvent(id, INSTANCE_RESOURCE, CREATE, newData, null);
    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verify(instanceClassificationRepository).saveAll(createCaptor.capture());

    assertThat(createCaptor.getValue())
      .extracting(ENTITY_FIELD_EXTRACTORS)
      .containsExactlyInAnyOrder(tuple("t1", "n1", TENANT_ID, id, false), tuple("t2", "n2", TENANT_ID, id, false));

    verify(instanceClassificationRepository).deleteAll(emptyList());
    verifyNoMoreInteractions(instanceClassificationRepository);
  }

  @Test
  void preProcess_DeleteEvent_ShouldProcessClassifications() {
    // Arrange
    var id = randomId();
    var oldData = instance(id, List.of(classification("n1", "t1"), classification("n2", "t2")));
    var resourceEvent = resourceEvent(id, INSTANCE_RESOURCE, DELETE, null, oldData);
    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());

    assertThat(deleteCaptor.getValue())
      .extracting(ENTITY_FIELD_EXTRACTORS)
      .containsExactlyInAnyOrder(tuple("t1", "n1", TENANT_ID, id, false), tuple("t2", "n2", TENANT_ID, id, false));

    verify(instanceClassificationRepository).saveAll(emptyList());
    verifyNoMoreInteractions(instanceClassificationRepository);
  }

  @Test
  void preProcess_UpdateEvent_ShouldProcessClassifications() {
    // Arrange
    var id = randomId();
    var newData = instance(id, List.of(classification("n1", "t1"), classification("n3", "t3")));
    var oldData = instance(id, List.of(classification("n1", "t1"), classification("n4", "t4")));
    var resourceEvent = resourceEvent(id, INSTANCE_RESOURCE, DELETE, newData, oldData);
    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verify(instanceClassificationRepository).saveAll(createCaptor.capture());

    assertThat(createCaptor.getValue())
      .extracting(ENTITY_FIELD_EXTRACTORS)
      .containsExactlyInAnyOrder(tuple("t3", "n3", TENANT_ID, id, false));

    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());
    assertThat(deleteCaptor.getValue())
      .extracting(ENTITY_FIELD_EXTRACTORS)
      .containsExactlyInAnyOrder(tuple("t4", "n4", TENANT_ID, id, false));

    verifyNoMoreInteractions(instanceClassificationRepository);
  }

  @ParameterizedTest
  @EnumSource(value = ResourceEventType.class, mode = EnumSource.Mode.INCLUDE, names = {"CREATE", "UPDATE", "DELETE"})
  void preProcess_AnyEventInConsortium_ShouldProcessClassificationsAndSetShared(ResourceEventType eventType) {
    // Arrange
    var id = randomId();
    var newData = instance(id, List.of(classification("n1", "t1"), classification("n3", "t3")));
    var oldData = instance(id, List.of(classification("n1", "t1"), classification("n4", "t4")));
    var resourceEvent = resourceEvent(id, INSTANCE_RESOURCE, eventType, newData, oldData);
    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
    when(consortiumTenantService.getCentralTenant(any())).then(invocation -> Optional.of(invocation.getArgument(0)));

    // Act
    var resourceEvents = preProcessor.preProcess(resourceEvent);

    // Assert
    assertThat(resourceEvents)
      .hasSize(1)
      .containsExactly(resourceEvent);

    verify(instanceClassificationRepository).saveAll(createCaptor.capture());

    assertThat(createCaptor.getValue())
      .extracting(ENTITY_FIELD_EXTRACTORS)
      .containsExactlyInAnyOrder(tuple("t3", "n3", TENANT_ID, id, true));

    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());
    assertThat(deleteCaptor.getValue())
      .extracting(ENTITY_FIELD_EXTRACTORS)
      .containsExactlyInAnyOrder(tuple("t4", "n4", TENANT_ID, id, true));

    verifyNoMoreInteractions(instanceClassificationRepository);
  }

  private void mockClassificationBrowseFeatureEnabled(Boolean isEnabled) {
    when(featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)).thenReturn(isEnabled);
  }

  @NotNull
  private static Map<String, String> instance(List<Map<String, String>> classifications) {
    return instance(randomId(), classifications);
  }

  @NotNull
  private static Map<String, String> instance(String id, List<Map<String, String>> classifications) {
    return instance(id, "SOURCE", classifications);
  }

  @NotNull
  private static Map<String, String> instance(String id, String source, List<Map<String, String>> classifications) {
    return mapOf(ID_FIELD, id, SOURCE_FIELD, source, CLASSIFICATIONS_FIELD, classifications);
  }

  @NotNull
  private static Map<String, String> classification(String number, String type) {
    return mapOf(CLASSIFICATION_NUMBER_FIELD, number, CLASSIFICATION_TYPE_FIELD, type);
  }
}
