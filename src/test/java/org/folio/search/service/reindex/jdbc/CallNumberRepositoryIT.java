package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
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
    var jsonConverter = new JsonConverter(new ObjectMapper());
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
          List.of(mapOf("count", 0, "instanceId", List.of("9f8febd1-e96c-46c4-a5f4-84a45cc499a2"),
            "instanceTitle", null, "locationId", null, "shared", false, "tenantId", TENANT_ID, "typeId", null))),
        tuple("number2",
          List.of(mapOf("count", 0, "instanceId", List.of("9f8febd1-e96c-46c4-a5f4-84a45cc499a2"),
            "instanceTitle", null, "locationId", null, "shared", false, "tenantId", TENANT_ID, "typeId", null))));
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
