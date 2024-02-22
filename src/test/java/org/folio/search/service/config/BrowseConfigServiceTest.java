package org.folio.search.service.config;

import static org.folio.search.domain.dto.BrowseOptionType.ALL;
import static org.folio.search.domain.dto.BrowseOptionType.LC;
import static org.folio.search.domain.dto.BrowseType.INSTANCE_CLASSIFICATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.folio.search.converter.BrowseConfigMapper;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.config.BrowseConfigEntity;
import org.folio.search.model.config.BrowseConfigId;
import org.folio.search.repository.BrowseConfigEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrowseConfigServiceTest {

  @Mock
  private BrowseConfigEntityRepository repository;

  @Mock
  private BrowseConfigMapper mapper;

  @InjectMocks
  private BrowseConfigService service;

  private BrowseType type;
  private BrowseOptionType configId;
  private BrowseConfig config;

  @BeforeEach
  void setUp() {
    type = INSTANCE_CLASSIFICATION;
    configId = LC;
    config = new BrowseConfig().id(LC).shelvingAlgorithm(ShelvingOrderAlgorithmType.LC);
  }

  @Test
  void shouldGetConfigs() {
    List<BrowseConfigEntity> entities = List.of(getEntity());
    BrowseConfigCollection configs = new BrowseConfigCollection().addConfigsItem(config);
    given(repository.findByConfigId_BrowseType(type.getValue())).willReturn(entities);
    given(mapper.convert(entities)).willReturn(configs);

    var result = service.getConfigs(type);

    assertEquals(configs, result);
    verify(repository).findByConfigId_BrowseType(type.getValue());
  }

  @Test
  void shouldUpsertConfigWhenConfigIdMatches() {
    var entity = getEntity();
    given(mapper.convert(type, config)).willReturn(entity);

    assertDoesNotThrow(() -> service.upsertConfig(type, configId, config));
    verify(repository).save(entity);
  }

  @Test
  void shouldThrowExceptionWhenConfigIdNotMatches() {
    config.setId(ALL);

    var exception = assertThrows(RequestValidationException.class, () -> service.upsertConfig(type, configId, config));

    String expectedMessage = String.format("Body doesn't match path parameter: %s", configId.getValue());
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  private static BrowseConfigEntity getEntity() {
    var configEntity = new BrowseConfigEntity();
    configEntity.setConfigId(new BrowseConfigId(INSTANCE_CLASSIFICATION.getValue(), LC.getValue()));
    configEntity.setShelvingAlgorithm(ShelvingOrderAlgorithmType.LC.getValue());
    configEntity.setTypeIds(List.of("e1", "e2"));
    return configEntity;
  }
}
