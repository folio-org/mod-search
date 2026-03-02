package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.extension.impl.RandomParametersExtension;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@Import(TestNoOpCacheConfig.class)
@ExtendWith(RandomParametersExtension.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CallNumberRepositoryIT {

  private static final String INSTANCE_ID = "9f8febd1-e96c-46c4-a5f4-84a45cc499a2";
  private static final Map<String, List<UUID>> ITEM_IDS =
    Map.of("1", getList(1), "2", getList(2));
  private @MockitoSpyBean JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private CallNumberRepository repository;

  @BeforeEach
  void setUp() {
    var properties = new ReindexConfigurationProperties();
    var jsonConverter = new JsonConverter(new JsonMapper());
    repository = spy(new CallNumberRepository(jdbcTemplate, jsonConverter, context, properties));
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
  @Sql({"/sql/populate-instances.sql", "/sql/populate-call-numbers.sql"})
  void fetchBy_returnListOfMaps() {
    // act
    var ranges = repository.fetchByIdRange("cn7", "cna");

    // assert
    assertThat(ranges)
      .hasSize(3)
      .allMatch(map -> map.keySet().containsAll(List.of("id", "fullCallNumber", "instances")))
      .extracting("id", "fullCallNumber")
      .containsExactlyInAnyOrder(
        tuple("cn7", "CN-007"),
        tuple("cn8", "CN-008 Suf8"),
        tuple("cna", "CN-010")
      );
  }

  @Test
  @Sql("/sql/populate-instances.sql")
  void saveAll() {
    var entities = Set.of(callNumberEntity("1"), callNumberEntity("2"));
    var entityRelations = List.of(
      callNumberRelation("1"),
      callNumberRelation("2"),
      callNumberRelation("2"));

    repository.saveAll(new ChildResourceEntityBatch(entities, entityRelations));

    // assert
    var ranges = repository.fetchByIdRange("0", "z");
    assertThat(ranges)
      .hasSize(2)
      .extracting("callNumber", "instances")
      .contains(
        tuple("number1",
          List.of(mapOf("count", null, "instanceContributors", null,
            "instanceId", List.of("9f8febd1-e96c-46c4-a5f4-84a45cc499a2"), "instanceTitle", null,
            "locationId", null, "resourceId", null, "shared", false, "tenantId", TENANT_ID, "typeId", null))),
        tuple("number2",
          List.of(mapOf("count", null, "instanceContributors", null,
            "instanceId", List.of("9f8febd1-e96c-46c4-a5f4-84a45cc499a2"), "instanceTitle", null,
            "locationId", null, "resourceId", null, "shared", false, "tenantId", TENANT_ID, "typeId", null))));
  }

  @Test
  @Sql("/sql/populate-instances.sql")
  void updateTenantIdForCentralInstances_updatesCallNumberRelationsAndCallNumber() {
    var callNumberId = "cn-test";

    //manually set last_updated_date to past to verify it's updated after tenantId update
    var pastTimestamp = Timestamp.valueOf("2000-01-01 00:00:00");
    jdbcTemplate.update(
      "INSERT INTO call_number (id, call_number, last_updated_date) VALUES (?, ?, ?)",
      callNumberId, "TEST-001", pastTimestamp);
    var relation = Map.<String, Object>of(
      "instanceId", INSTANCE_ID,
      "itemId", UUID.randomUUID().toString(),
      "callNumberId", callNumberId,
      "tenantId", MEMBER_TENANT_ID,
      "shared", false
    );
    repository.saveAll(new ChildResourceEntityBatch(Set.of(), List.of(relation)));

    // verify member tenant is set before update
    var before = repository.fetchByIdRange(callNumberId, callNumberId);
    assertCallNumberInstanceTenantId(before, MEMBER_TENANT_ID);

    // act
    repository.updateTenantIdForCentralInstances(List.of(INSTANCE_ID), CENTRAL_TENANT_ID);

    // assert tenant is updated to central
    var after = repository.fetchByIdRange(callNumberId, callNumberId);
    assertCallNumberInstanceTenantId(after, CENTRAL_TENANT_ID);

    // assert last_updated_date is updated
    var lastUpdatedAfter = jdbcTemplate.queryForObject(
      "SELECT last_updated_date FROM call_number WHERE id = ?", Timestamp.class, callNumberId);
    assertThat(lastUpdatedAfter).isNotNull().isAfter(pastTimestamp);
  }

  private void assertCallNumberInstanceTenantId(List<Map<String, Object>> callNumbers, String expectedTenantId) {
    assertThat(callNumbers).hasSize(1)
      .flatExtracting(m -> (List<?>) m.get("instances"))
      .extracting(i -> (String) ((Map<?, ?>) i).get("tenantId"))
      .containsExactly(expectedTenantId);
  }

  private static List<UUID> getList(int size) {
    var list = new ArrayList<UUID>();
    for (int i = 0; i < size; i++) {
      list.add(UUID.randomUUID());
    }
    return list;
  }

  private static Map<String, Object> callNumberEntity(String id) {
    return Map.of(
      "id", id,
      "callNumber", "number" + id,
      "volume", "vol"
    );
  }

  private static Map<String, Object> callNumberRelation(String callNumberId) {
    List<UUID> itemIdList = ITEM_IDS.get(callNumberId);
    return Map.of(
      "instanceId", INSTANCE_ID,
      "itemId", itemIdList.removeFirst().toString(),
      "callNumberId", callNumberId,
      "tenantId", TENANT_ID,
      "shared", false
    );
  }
}
