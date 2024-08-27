package org.folio.search.repository.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstanceClassificationRepositoryIT {

  private final EasyRandom easyRandom = new EasyRandom();

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;
  private InstanceClassificationJdbcRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InstanceClassificationJdbcRepository(context, jdbcTemplate, new ObjectMapper());
    when(context.getFolioModuleMetadata()).thenReturn(new FolioModuleMetadata() {
      @Override
      public String getModuleName() {
        return null;
      }

      @Override
      public String getDBSchemaName(String tenantId) {
        return "public";
      }
    });
    when(context.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void testSaveAll_positive_recordsSaved() {
    // Arrange
    var entityList = List.of(randomEntity(), randomEntity(), randomEntity());

    // Act
    repository.saveAll(entityList);

    // Assert
    var savedEntities = repository.findAll();
    assertEquals(entityList, savedEntities);
  }

  @Test
  void testSaveAll_positive_recordsSavedWithNullType() {
    // Arrange
    var entityList = List.of(randomEntity(), randomEntity(), randomEntityWithNullType());

    // Act
    repository.saveAll(entityList);

    // Assert
    var savedEntities = repository.findAll();
    assertEquals(entityList, savedEntities);
  }

  @Test
  void testSaveAll_positive_uniqueRecordsSavedWhenDuplicated() {
    // Arrange
    var entity = randomEntity();
    var entityList = List.of(entity, entity);

    // Act
    repository.saveAll(entityList);

    // Assert
    var savedEntities = repository.findAll();
    assertEquals(List.of(entity), savedEntities);
  }

  @Test
  void testDeleteAll_positive_recordsDeleted() {
    // Arrange
    var e1 = randomEntity();
    var e2 = randomEntity();
    var e3 = randomEntityWithNullType();
    var entityList = List.of(e1, e2, e3);
    repository.saveAll(entityList);
    var entityListToDelete = List.of(e1, e1, e3);

    // Act
    repository.deleteAll(entityListToDelete);

    // Assert
    var savedEntities = repository.findAll();
    assertEquals(List.of(e2), savedEntities);
  }

  @Test
  void testFindAllByInstanceIds_positive() {
    // Arrange
    var e1 = entity("type1", "number1", "instanceId1", "tenant1", true);
    var e2 = entity("type2", "number2", "instanceId1", "tenant2", false);
    var e3 = entity("type1", "number1", "instanceId2", "tenant2", false);
    var entityList = List.of(e1, e2, e3);
    repository.saveAll(entityList);

    // Act
    var allByInstanceIds = repository.fetchAggregatedByClassifications(List.of(e1, e2));

    // Assert
    assertThat(allByInstanceIds).hasSize(2)
      .extracting(InstanceClassificationEntityAgg::typeId, InstanceClassificationEntityAgg::number,
        entityAgg -> entityAgg.instances()
          .stream()
          .map(i -> i.getTenantId() + "|" + i.getInstanceId() + "|" + i.getShared())
          .sorted()
          .collect(Collectors.joining(";")))
      .containsExactlyInAnyOrder(
        tuple("type1", "number1", "tenant1|instanceId1|true;tenant2|instanceId2|false"),
        tuple("type2", "number2", "tenant2|instanceId1|false"));
  }

  private InstanceClassificationEntity randomEntity() {
    return new InstanceClassificationEntity(InstanceClassificationEntity.Id.builder()
      .number(easyRandom.nextObject(String.class))
      .typeId(easyRandom.nextObject(String.class))
      .tenantId(easyRandom.nextObject(String.class))
      .instanceId(easyRandom.nextObject(String.class))
      .build(), easyRandom.nextObject(Boolean.class));
  }

  private InstanceClassificationEntity randomEntityWithNullType() {
    return new InstanceClassificationEntity(InstanceClassificationEntity.Id.builder()
      .number(easyRandom.nextObject(String.class))
      .typeId(null)
      .tenantId(easyRandom.nextObject(String.class))
      .instanceId(easyRandom.nextObject(String.class))
      .build(), easyRandom.nextObject(Boolean.class));
  }

  private InstanceClassificationEntity entity(String type, String number, String instanceId,
                                              String tenant, boolean shared) {
    return new InstanceClassificationEntity(
      new InstanceClassificationEntity.Id(type, number, instanceId, tenant),
      shared);
  }
}
