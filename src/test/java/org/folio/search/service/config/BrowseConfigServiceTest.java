package org.folio.search.service.config;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CLASSIFICATION_TYPES;
import static org.folio.search.domain.dto.BrowseOptionType.ALL;
import static org.folio.search.domain.dto.BrowseOptionType.LC;
import static org.folio.search.domain.dto.BrowseType.INSTANCE_CLASSIFICATION;
import static org.folio.search.model.client.CqlQueryParam.ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType;
import org.folio.search.converter.BrowseConfigMapper;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.config.BrowseConfigEntity;
import org.folio.search.model.config.BrowseConfigId;
import org.folio.search.repository.BrowseConfigEntityRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class BrowseConfigServiceTest {

  @Mock
  private BrowseConfigEntityRepository repository;
  @Mock
  private BrowseConfigMapper mapper;

  @Mock
  private ReferenceDataService referenceDataService;

  @InjectMocks
  private BrowseConfigService service;

  private BrowseOptionType configId;
  private BrowseConfig config;
  private List<String> typeIds;

  @BeforeEach
  void setUp() {
    configId = LC;
    typeIds = List.of(randomId(), randomId());
    config = new BrowseConfig()
      .id(LC)
      .shelvingAlgorithm(ShelvingOrderAlgorithmType.LC)
      .typeIds(typeIds.stream().map(UUID::fromString).toList());
  }

  @EnumSource(BrowseType.class)
  @ParameterizedTest
  void shouldGetConfigs(BrowseType type) {
    var entities = List.of(getEntity(type));
    var configs = new BrowseConfigCollection().addConfigsItem(config);
    given(repository.findByConfigId_BrowseType(type.getValue())).willReturn(entities);
    given(mapper.convert(entities)).willReturn(configs);

    var result = service.getConfigs(type);

    assertEquals(configs, result);
    verify(repository).findByConfigId_BrowseType(type.getValue());
  }

  @EnumSource(BrowseType.class)
  @ParameterizedTest
  void shouldGetConfig(BrowseType type) {
    var browseConfigId = new BrowseConfigId(type.getValue(), "lc");
    var configEntity = getEntity(type);
    given(repository.findById(browseConfigId)).willReturn(Optional.of(configEntity));
    given(mapper.convert(configEntity)).willReturn(config);

    var result = service.getConfig(type, configId);

    assertEquals(config, result);
    verify(repository).findById(browseConfigId);
  }

  @EnumSource(BrowseType.class)
  @ParameterizedTest
  void shouldThrowExceptionIfConfigNotExists(BrowseType type) {
    given(repository.findById(any())).willReturn(Optional.empty());

    var exception = assertThrows(IllegalStateException.class, () -> service.getConfig(type, configId));

    String expectedMessage = String.format("Config for %s type %s must be present in database", type.getValue(),
      configId.getValue());
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @EnumSource(BrowseType.class)
  @ParameterizedTest
  void shouldUpsertConfigWhenFitAllValidations(BrowseType type) {
    var entity = getEntity(type);
    given(mapper.convert(type, config)).willReturn(entity);
    given(referenceDataService.fetchReferenceData(referenceDataType(type), ID, new HashSet<>(typeIds)))
      .willReturn(new HashSet<>(typeIds));

    assertDoesNotThrow(() -> service.upsertConfig(type, configId, config));
    verify(repository).save(entity);
  }

  @Test
  void shouldThrowExceptionWhenConfigIdNotMatches() {
    config.setId(ALL);

    var exception = assertThrows(RequestValidationException.class,
      () -> service.upsertConfig(INSTANCE_CLASSIFICATION, configId, config));

    assertThat(exception)
      .hasMessage("Body doesn't match path parameter: %s", configId.getValue());
    verifyNoInteractions(repository);
  }

  @EnumSource(BrowseType.class)
  @ParameterizedTest
  void shouldThrowExceptionWhenIdIsNotInReferenceData(BrowseType type) {
    given(referenceDataService.fetchReferenceData(referenceDataType(type), ID, new HashSet<>(typeIds)))
      .willReturn(Set.of(typeIds.get(0)));

    var exception = assertThrows(RequestValidationException.class, () -> service.upsertConfig(type, configId, config));

    assertThat(exception)
      .hasMessage(type.getValue() + " type IDs don't exist");

    verifyNoInteractions(repository);
  }

  @EnumSource(BrowseType.class)
  @ParameterizedTest
  void shouldDeleteTypeIdsFromConfigs(BrowseType type) {
    var entities = List.of(getEntity(type), getEntity(type));
    given(repository.findByConfigId_BrowseType(type.getValue())).willReturn(entities);
    ArgumentCaptor<List<BrowseConfigEntity>> captor = ArgumentCaptor.captor();
    given(repository.saveAll(captor.capture())).willReturn(emptyList());

    service.deleteTypeIdsFromConfigs(type, List.of("e1", "e2"));

    var captorValues = captor.getValue();
    for (BrowseConfigEntity captorValue : captorValues) {
      assertThat(captorValue.getTypeIds()).isNullOrEmpty();
    }
  }

  private static BrowseConfigEntity getEntity(BrowseType type) {
    var configEntity = new BrowseConfigEntity();
    configEntity.setConfigId(new BrowseConfigId(type.getValue(), LC.getValue()));
    configEntity.setShelvingAlgorithm(ShelvingOrderAlgorithmType.LC.getValue());
    configEntity.setTypeIds(List.of("e1", "e2"));
    return configEntity;
  }

  private ReferenceDataType referenceDataType(BrowseType browseType) {
    return INSTANCE_CLASSIFICATION.equals(browseType) ? CLASSIFICATION_TYPES : CALL_NUMBER_TYPES;
  }
}
