//package org.folio.search.service.converter.preprocessor;
//
//import static java.util.Collections.emptyList;
//import static org.assertj.core.api.AssertionsForClassTypes.tuple;
//import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
//import static org.folio.search.domain.dto.ResourceEventType.CREATE;
//import static org.folio.search.domain.dto.ResourceEventType.DELETE;
//import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
//import static org.folio.search.model.types.ResourceType.INSTANCE;
//import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
//import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
//import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
//import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
//import static org.folio.search.utils.SearchUtils.ID_FIELD;
//import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;
//import static org.folio.search.utils.SearchUtils.SOURCE_FIELD;
//import static org.folio.search.utils.TestConstants.TENANT_ID;
//import static org.folio.search.utils.TestUtils.mapOf;
//import static org.folio.search.utils.TestUtils.randomId;
//import static org.folio.search.utils.TestUtils.resourceEvent;
//import static org.mockito.ArgumentMatchers.anyList;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoInteractions;
//import static org.mockito.Mockito.verifyNoMoreInteractions;
//import static org.mockito.Mockito.when;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.function.Function;
//import org.apache.commons.collections4.SetUtils;
//import org.folio.search.domain.dto.ResourceEvent;
//import org.folio.search.domain.dto.ResourceEventType;
//import org.folio.search.domain.dto.TenantConfiguredFeature;
//import org.folio.search.model.index.InstanceSubResource;
//import org.folio.search.model.entity.InstanceClassificationEntity;
//import org.folio.search.model.entity.InstanceClassificationEntityAgg;
//import org.folio.search.repository.classification.InstanceClassificationRepository;
//import org.folio.search.service.FeatureConfigService;
//import org.folio.search.service.consortium.ConsortiumTenantProvider;
//import org.folio.search.utils.JsonConverter;
//import org.folio.spring.testing.type.UnitTest;
//import org.jetbrains.annotations.NotNull;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.EnumSource;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Captor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//@UnitTest
//@ExtendWith(MockitoExtension.class)
//class InstanceEventPreProcessorTest {
//
//  @SuppressWarnings("unchecked")
//  private static final Function<InstanceClassificationEntity, Object>[] ENTITY_FIELD_EXTRACTORS = new Function[] {
//    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::typeId,
//    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::number,
//    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::tenantId,
//    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::instanceId,
//    (Function<InstanceClassificationEntity, Object>) InstanceClassificationEntity::shared
//  };
//
//  private @Spy JsonConverter jsonConverter = new JsonConverter(new ObjectMapper());
//  private @Mock FeatureConfigService featureConfigService;
//  private @Mock ConsortiumTenantProvider consortiumTenantProvider;
//  private @Mock InstanceClassificationRepository instanceClassificationRepository;
//  private @InjectMocks InstanceEventPreProcessor preProcessor;
//
//  private @Captor ArgumentCaptor<List<InstanceClassificationEntity>> createCaptor;
//  private @Captor ArgumentCaptor<List<InstanceClassificationEntity>> deleteCaptor;
//
//  @Test
//  void preProcess_ShadowInstance_ShouldNotProcessClassifications() {
//    // Arrange
//    var data = instance(randomId(), SOURCE_CONSORTIUM_PREFIX + "SOURCE", emptyList());
//    var resourceEvent = resourceEvent(INSTANCE, data);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .hasSize(1)
//      .containsExactly(resourceEvent);
//
//    verifyNoInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_FeatureIsDisabled_ShouldNotProcessClassifications() {
//    // Arrange
//    var data = instance(emptyList());
//    var resourceEvent = resourceEvent(INSTANCE, data);
//    mockClassificationBrowseFeatureEnabled(Boolean.FALSE);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .isEmpty();
//
//    verifyNoInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_NoChangeToClassifications_ShouldNotProcessClassifications() {
//    // Arrange
//    var newData = instance(List.of(classification("n1", "t1"), classification("n2", "t2")));
//    var oldData = instance(List.of(classification("n2", "t2"), classification("n1", "t1")));
//    var resourceEvent = resourceEvent(randomId(), INSTANCE, UPDATE, newData, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .isEmpty();
//
//    verifyNoInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_CreateEvent_ShouldProcessClassifications() {
//    // Arrange
//    var id = randomId();
//    var newData = instance(id, List.of(classification("n1", "t1"), classification("n2", "t2")));
//    var resourceEvent = resourceEvent(id, INSTANCE, CREATE, newData, null);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//    when(instanceClassificationRepository.fetchAggregatedByClassifications(anyList()))
//      .thenReturn(List.of(new InstanceClassificationEntityAgg("t1", "n1",
//          Set.of(InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID).shared(false).build())),
//        new InstanceClassificationEntityAgg("t2", "n2",
//          Set.of(InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID).shared(false).build()))));
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .hasSize(2)
//      .allSatisfy(event -> assertThat(event)
//        .extracting(ResourceEvent::getResourceName, ResourceEvent::getTenant, ResourceEvent::getType)
//        .containsExactly(INSTANCE_CLASSIFICATION.getName(), TENANT_ID, CREATE))
//      .extracting(ResourceEvent::getId)
//      .containsExactlyInAnyOrder("n1|t1", "n2|t2");
//
//    verify(instanceClassificationRepository).saveAll(createCaptor.capture());
//
//    assertThat(createCaptor.getValue())
//      .extracting(ENTITY_FIELD_EXTRACTORS)
//      .containsExactlyInAnyOrder(tuple("t1", "n1", TENANT_ID, id, false), tuple("t2", "n2", TENANT_ID, id, false));
//
//    verify(instanceClassificationRepository).deleteAll(emptyList());
//    verifyNoMoreInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_DeleteEvent_ShouldProcessClassifications() {
//    // Arrange
//    var id = randomId();
//    var oldData = instance(id, List.of(classification("n1", "t1"), classification("n2", "t2")));
//    var resourceEvent = resourceEvent(id, INSTANCE, DELETE, null, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//    when(instanceClassificationRepository.fetchAggregatedByClassifications(anyList()))
//      .thenReturn(List.of(new InstanceClassificationEntityAgg("t1", "n1",
//        Set.of(InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID).shared(false).build()))));
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .hasSize(2)
//      .allSatisfy(event -> assertThat(event)
//        .extracting(ResourceEvent::getResourceName, ResourceEvent::getTenant)
//        .containsExactly(INSTANCE_CLASSIFICATION.getName(), TENANT_ID))
//      .extracting(ResourceEvent::getId, ResourceEvent::getType)
//      .containsExactlyInAnyOrder(tuple("n1|t1", CREATE), tuple("n2|t2", DELETE));
//
//    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());
//
//    assertThat(deleteCaptor.getValue())
//      .extracting(ENTITY_FIELD_EXTRACTORS)
//      .containsExactlyInAnyOrder(tuple("t1", "n1", TENANT_ID, id, false), tuple("t2", "n2", TENANT_ID, id, false));
//
//    verify(instanceClassificationRepository).saveAll(emptyList());
//    verifyNoMoreInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_UpdateEvent_ShouldProcessClassifications() {
//    // Arrange
//    var id = randomId();
//    var newData = instance(id, List.of(classification("n1", "t1"), classification("n3", "t3")));
//    var oldData = instance(id, List.of(classification("n1", "t1"), classification("n4", "t4")));
//    var resourceEvent = resourceEvent(id, INSTANCE, DELETE, newData, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//    when(instanceClassificationRepository.fetchAggregatedByClassifications(anyList()))
//      .thenReturn(List.of(new InstanceClassificationEntityAgg("t1", "n1",
//          Set.of(InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID).shared(false).build())),
//        new InstanceClassificationEntityAgg("t3", "n3",
//          Set.of(InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID).shared(false).build()))));
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .hasSize(3)
//      .allSatisfy(event -> assertThat(event)
//        .extracting(ResourceEvent::getResourceName, ResourceEvent::getTenant)
//        .containsExactly(INSTANCE_CLASSIFICATION.getName(), TENANT_ID))
//      .extracting(ResourceEvent::getId, ResourceEvent::getType)
//      .containsExactlyInAnyOrder(tuple("n1|t1", CREATE), tuple("n3|t3", CREATE), tuple("n4|t4", DELETE));
//
//    verify(instanceClassificationRepository).saveAll(createCaptor.capture());
//
//    assertThat(createCaptor.getValue())
//      .extracting(ENTITY_FIELD_EXTRACTORS)
//      .containsExactlyInAnyOrder(tuple("t3", "n3", TENANT_ID, id, false));
//
//    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());
//    assertThat(deleteCaptor.getValue())
//      .extracting(ENTITY_FIELD_EXTRACTORS)
//      .containsExactlyInAnyOrder(tuple("t4", "n4", TENANT_ID, id, false));
//
//    verifyNoMoreInteractions(instanceClassificationRepository);
//  }
//
//  @ParameterizedTest
//  @EnumSource(value = ResourceEventType.class, mode = EnumSource.Mode.INCLUDE, names = {"CREATE", "UPDATE", "DELETE"})
//  void preProcess_AnyEventInConsortium_ShouldProcessClassificationsAndSetShared(ResourceEventType eventType) {
//    // Arrange
//    var id = randomId();
//    var newData = instance(id, List.of(classification("n1", "t1"), classification("n3", "t3")));
//    var oldData = instance(id, List.of(classification("n1", "t1"), classification("n4", "t4")));
//    var resourceEvent = resourceEvent(id, INSTANCE, eventType, newData, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//    when(consortiumTenantProvider.isCentralTenant(anyString())).thenReturn(true);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .hasSize(1);
//
//    verify(instanceClassificationRepository).saveAll(createCaptor.capture());
//
//    assertThat(createCaptor.getValue())
//      .extracting(ENTITY_FIELD_EXTRACTORS)
//      .containsExactlyInAnyOrder(tuple("t3", "n3", TENANT_ID, id, true));
//
//    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());
//    assertThat(deleteCaptor.getValue())
//      .extracting(ENTITY_FIELD_EXTRACTORS)
//      .containsExactlyInAnyOrder(tuple("t4", "n4", TENANT_ID, id, true));
//  }
//
//  @Test
//  void preProcess_featureIsDisabledOnInstanceSharing_shouldNotProcessClassifications() {
//    // Arrange
//    var newData = instance(randomId(), SOURCE_CONSORTIUM_PREFIX + "FOLIO", null);
//    var oldData = instance(randomId(), "FOLIO", null);
//    var resourceEvent = resourceEvent(randomId(), INSTANCE, UPDATE, newData, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.FALSE);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .isEmpty();
//
//    verifyNoInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_differentClassificationsOnInstanceSharing_shouldNotProcessClassifications() {
//    // Arrange
//    var id = randomId();
//    var newData = instance(id, SOURCE_CONSORTIUM_PREFIX + "FOLIO", List.of(classification("n2", "t2")));
//    var oldData = instance(id, "FOLIO", List.of(classification("n1", "t1")));
//    var resourceEvent = resourceEvent(randomId(), INSTANCE, UPDATE, newData, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    assertThat(resourceEvents)
//      .isEmpty();
//
//    verifyNoInteractions(instanceClassificationRepository);
//  }
//
//  @Test
//  void preProcess_DeleteEntityAndUpdateIndexOnInstanceSharing_shouldProcessClassifications() {
//    // Arrange
//    var id = randomId();
//    var typeId = "type";
//    var number = "num";
//    var newData = instance(id, SOURCE_CONSORTIUM_PREFIX + "FOLIO", List.of(classification(number, typeId)));
//    var oldData = instance(id, "FOLIO", List.of(classification(number, typeId)));
//    var resourceEvent = resourceEvent(id, INSTANCE, UPDATE, newData, oldData);
//    mockClassificationBrowseFeatureEnabled(Boolean.TRUE);
//    when(instanceClassificationRepository.fetchAggregatedByClassifications(anyList()))
//      .thenReturn(List.of(
//        new InstanceClassificationEntityAgg(typeId, number,
//          SetUtils.hashSet(
//            InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID).shared(false).build(),
//            InstanceSubResource.builder().instanceId(id).tenantId(TENANT_ID + "_central").shared(true).build()
//          )
//        )
//      ));
//    var classificationId = InstanceClassificationEntity.Id.builder()
//      .number(number)
//      .typeId(typeId)
//      .instanceId(id)
//      .tenantId(TENANT_ID)
//      .build();
//    var expectedDeletedClassificationEntity = new InstanceClassificationEntity(classificationId, false);
//
//    // Act
//    var resourceEvents = preProcessor.preProcess(resourceEvent);
//
//    // Assert
//    verify(instanceClassificationRepository).deleteAll(deleteCaptor.capture());
//    verify(instanceClassificationRepository).fetchAggregatedByClassifications(anyList());
//    var deletedClassifications = deleteCaptor.getValue();
//    assertThat(List.of(expectedDeletedClassificationEntity))
//      .isEqualTo(deletedClassifications);
//
//    assertThat(resourceEvents)
//      .hasSize(1)
//      .allSatisfy(event -> assertThat(event)
//        .extracting(ResourceEvent::getResourceName, ResourceEvent::getTenant, ResourceEvent::getType)
//        .containsExactly(INSTANCE_CLASSIFICATION.getName(), TENANT_ID, UPDATE))
//      .extracting(ResourceEvent::getId)
//      .containsExactlyInAnyOrder("num|type");
//
//    verifyNoMoreInteractions(instanceClassificationRepository);
//  }
//
//  private void mockClassificationBrowseFeatureEnabled(Boolean isEnabled) {
//    when(featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)).thenReturn(isEnabled);
//  }
//
//  @NotNull
//  private static Map<String, String> instance(List<Map<String, String>> classifications) {
//    return instance(randomId(), classifications);
//  }
//
//  @NotNull
//  private static Map<String, String> instance(String id, List<Map<String, String>> classifications) {
//    return instance(id, "SOURCE", classifications);
//  }
//
//  @NotNull
//  private static Map<String, String> instance(String id, String source, List<Map<String, String>> classifications) {
//    return mapOf(ID_FIELD, id, SOURCE_FIELD, source, CLASSIFICATIONS_FIELD, classifications);
//  }
//
//  @NotNull
//  private static Map<String, String> classification(String number, String type) {
//    return mapOf(CLASSIFICATION_NUMBER_FIELD, number, CLASSIFICATION_TYPE_FIELD, type);
//  }
//}
