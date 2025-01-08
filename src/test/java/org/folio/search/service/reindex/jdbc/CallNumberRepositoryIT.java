package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
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

  private @MockitoSpyBean JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private @MockitoBean ConsortiumTenantProvider tenantProvider;
  private CallNumberRepository repository;
  private ReindexConfigurationProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ReindexConfigurationProperties();
    var jsonConverter = new JsonConverter(new ObjectMapper());
    repository = spy(new CallNumberRepository(jdbcTemplate, jsonConverter, context, properties, tenantProvider));
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
  @Sql("/sql/populate-call-numbers.sql")
  void fetchBy_returnListOfMaps() {
    // act
    var ranges = repository.fetchByIdRange("cn7", "cna");

    // assert
    assertThat(ranges)
      .hasSize(3)
      .allMatch(map -> map.keySet().containsAll(List.of("id", "fullCallNumber", "instances")))
      .extracting("id", "fullCallNumber")
      .containsExactlyInAnyOrder(
        tuple("cn7", "CN-007 Vol7 Enum7 Copy7"),
        tuple("cn8", "CN-008 Chron8 Copy8 Suf8"),
        tuple("cna", "CN-010 Vol10 Chron10 Copy10")
      );
  }

  @Test
  void saveAll() {
    var entities = Set.of(callNumberEntity("1"), callNumberEntity("2"));
    var entityRelations = List.of(
      callNumberRelation("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "1"),
      callNumberRelation("b3bae8a9-cfb1-4afe-83d5-2cdae4580e07", "2"),
      callNumberRelation("9ec55e4f-6a76-427c-b47b-197046f44a54", "2"));

    repository.saveAll(new ChildResourceEntityBatch(entities, entityRelations));

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(2)
      .extracting("callNumber", "instances")
      .contains(
        tuple("number1",
          List.of(mapOf("count", 1, "locationId", null, "shared", null, "tenantId", TENANT_ID, "typeId", null))),
        tuple("number2",
          List.of(mapOf("count", 2, "locationId", null, "shared", null, "tenantId", TENANT_ID, "typeId", null))));
  }

  private Map<String, Object> callNumberEntity(String id) {
    return Map.of(
      "id", id,
      "callNumber", "number" + id,
      "volume", "vol"
    );
  }

  private Map<String, Object> callNumberRelation(String instanceId, String callNumberId) {
    return Map.of(
      "instanceId", instanceId,
      "itemId", "b3e1db88-67e7-4e62-8824-8bd8fe71af9a",
      "callNumberId", callNumberId,
      "tenantId", TENANT_ID,
      "shared", false
    );
  }

}
