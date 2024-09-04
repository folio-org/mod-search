package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Condition;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MergeRangeRepositoriesIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;
  private @MockBean ConsortiumTenantProvider tenantProvider;
  private @MockBean ReindexConfigurationProperties reindexConfig;
  private HoldingRepository holdingRepository;
  private ItemRepository itemRepository;
  private MergeInstanceRepository instanceRepository;
  private UploadInstanceRepository uploadInstanceRepository;

  @BeforeEach
  void setUp() {
    var jsonConverter = new JsonConverter(new ObjectMapper());
    holdingRepository = new HoldingRepository(jdbcTemplate, jsonConverter, context);
    itemRepository = new ItemRepository(jdbcTemplate, jsonConverter, context);
    instanceRepository = new MergeInstanceRepository(jdbcTemplate, jsonConverter, context, tenantProvider);
    uploadInstanceRepository = new UploadInstanceRepository(jdbcTemplate, jsonConverter, context, reindexConfig);
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
  void getMergeRanges_returnEmptyList_whenNoMergeRanges() {
    // act
    var rangesHolding = holdingRepository.getMergeRanges();
    var rangesItem = itemRepository.getMergeRanges();
    var rangesInstance = instanceRepository.getMergeRanges();

    // assert
    assertThat(List.of(rangesHolding, rangesItem, rangesInstance))
      .are(new Condition<>(List::isEmpty, "empty ranges"));
  }

  @Test
  @Sql("/sql/populate-merge-ranges.sql")
  void getMergeRanges_returnRangesList_whenMergeRangesExist() {
    // act
    var rangesHolding = holdingRepository.getMergeRanges();
    var rangesItem = itemRepository.getMergeRanges();
    var rangesInstance = instanceRepository.getMergeRanges();

    // assert
    assertThat(rangesHolding)
      .hasSize(2)
      .are(new Condition<>(range -> range.getEntityType() == ReindexEntityType.HOLDINGS, "holding range"))
      .extracting(MergeRangeEntity::getId, MergeRangeEntity::getTenantId)
      .containsExactly(tuple(UUID.fromString("b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6"), "consortium"),
        tuple(UUID.fromString("dfb20d52-7f1f-4b5b-a492-2e47d2c0ac59"), "member_tenant"));

    assertThat(rangesItem)
      .hasSize(1)
      .are(new Condition<>(range -> range.getEntityType() == ReindexEntityType.ITEM, "item range"))
      .extracting(MergeRangeEntity::getId, MergeRangeEntity::getTenantId)
      .containsExactly(tuple(UUID.fromString("2f23b9fa-9e1a-44ff-a30f-61ec5f3adcc8"), "member_tenant"));

    assertThat(rangesInstance)
      .hasSize(1)
      .are(new Condition<>(range -> range.getEntityType() == ReindexEntityType.INSTANCE, "instance range"))
      .extracting(MergeRangeEntity::getId, MergeRangeEntity::getTenantId)
      .containsExactly(tuple(UUID.fromString("9f8febd1-e96c-46c4-a5f4-84a45cc499a2"), "consortium"));
  }

  @Disabled
  @Test
  @Sql("/sql/populate-merge-ranges.sql")
  void truncateMergeRanges_truncatesAndThenReturnEmptyList_whenMergeRangesExist() {
    // act

    var rangesHolding = holdingRepository.getMergeRanges();
    var rangesItem = itemRepository.getMergeRanges();
    var rangesInstance = instanceRepository.getMergeRanges();

    // assert
    assertThat(List.of(rangesHolding, rangesItem, rangesInstance))
      .are(new Condition<>(List::isEmpty, "empty ranges"));
  }

  @Test
  void saveMergeRanges_savesRanges_whenProvidedListOfMergeRangeEntities() {
    // given
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var instanceRanges = List.of(
      new MergeRangeEntity(id1, ReindexEntityType.INSTANCE, "member", id1, id1, Timestamp.from(Instant.now())),
      new MergeRangeEntity(id2, ReindexEntityType.INSTANCE, "member", id2, id2, Timestamp.from(Instant.now()))
    );

    // act
    instanceRepository.saveMergeRanges(instanceRanges);

    // assert
    var ranges = instanceRepository.getMergeRanges();

    assertThat(ranges)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdAt")
      .isEqualTo(instanceRanges);
  }

  @Test
  @SuppressWarnings("unchecked")
  void saveEntities() {
    var mainInstanceId = UUID.randomUUID();
    var holdingId1 = UUID.randomUUID();
    var holdingId2 = UUID.randomUUID();
    // given
    when(tenantProvider.isCentralTenant(TENANT_ID)).thenReturn(true);
    var instances = List.of(Map.<String, Object>of("id", mainInstanceId, "isBoundWith", true),
      Map.<String, Object>of("id", UUID.randomUUID(), "isBoundWith", false));
    var holdings = List.of(Map.<String, Object>of("id", holdingId1, "instanceId", mainInstanceId),
      Map.<String, Object>of("id", holdingId2, "instanceId", mainInstanceId));
    var items = List.of(Map.<String, Object>of("id", UUID.randomUUID(), "instanceId", mainInstanceId,
        "holdingsRecordId", holdingId1),
      Map.<String, Object>of("id", UUID.randomUUID(), "instanceId", mainInstanceId, "holdingsRecordId", holdingId2));

    // act
    instanceRepository.saveEntities(TENANT_ID, instances);
    holdingRepository.saveEntities(TENANT_ID, holdings);
    itemRepository.saveEntities(TENANT_ID, items);

    // assert
    var instanceCount = instanceRepository.countEntities();
    var holdingCount = holdingRepository.countEntities();
    var itemCount = itemRepository.countEntities();

    assertThat(List.of(instanceCount, holdingCount, itemCount)).allMatch(count -> count == 2);

    var actual = uploadInstanceRepository.fetchBy(5, 0);
    assertThat(actual)
      .hasSize(2);
    var mainInstance = actual.stream().filter(map -> map.get("id").equals(mainInstanceId.toString())).findFirst().get();
    var instanceItems = (List<Map<String, Object>>) mainInstance.get("items");
    assertThat(instanceItems)
      .hasSize(2);
    assertThat(extractMapValues(instanceItems)).contains(holdingId1.toString(), holdingId2.toString());
    var instanceHoldings = (List<Map<String, Object>>) mainInstance.get("holdings");
    assertThat(instanceHoldings)
      .hasSize(2);
    assertThat(extractMapValues(instanceHoldings))
      .contains(mainInstanceId.toString(), holdingId1.toString(), holdingId2.toString());
  }

  private List<String> extractMapValues(List<Map<String, Object>> maps) {
    return maps.stream().map(Map::values).flatMap(Collection::stream).map(String::valueOf).toList();
  }
}
