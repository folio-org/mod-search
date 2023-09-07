package org.folio.search.service.consortium;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.test.extension.EnablePostgres;
import org.folio.spring.test.type.IntegrationTest;
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
class ConsortiumInstanceRepositoryIT {

  private static final String[] TENANTS = new String[] {"tenant1", "tenant2", "tenant3"};
  private static final String TABLE_NAME = "consortium_instance";

  private @Autowired ObjectMapper mapper;
  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;

  private ConsortiumInstanceRepository repository;

  @BeforeEach
  void setUp() {
    repository = new ConsortiumInstanceRepository(jdbcTemplate, context);
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
    when(context.getTenantId()).thenReturn("consortium");
  }

  @Test
  @SneakyThrows
  void testSave_positive_instancesSaved() {
    var instanceId = randomId();
    String instanceJson = instance(instanceId);
    var consortiumInstance1 = consortiumInstance(0, instanceId, instanceJson);
    var consortiumInstance2 = consortiumInstance(1, instanceId, instanceJson);
    var startTimestamp = new Timestamp(System.currentTimeMillis());

    repository.save(List.of(consortiumInstance1, consortiumInstance2));

    assertThat(getDbRecords())
      .hasSize(2)
      .allSatisfy(dbRecord -> assertThat(dbRecord.tenantId()).isIn(TENANTS[0], TENANTS[1]))
      .allSatisfy(dbRecord -> assertThat(dbRecord.instanceId()).isEqualTo(instanceId))
      .allSatisfy(dbRecord -> assertThat(dbRecord.json()).isEqualTo(instanceJson))
      .allSatisfy(dbRecord -> assertThat(dbRecord.created()).isAfter(startTimestamp))
      .allSatisfy(dbRecord -> assertThat(dbRecord.updated()).isAfter(startTimestamp));
  }

  @Test
  @SneakyThrows
  void testSave_positive_instanceUpdatedIfItHasSameIdAndTenant() {
    var instanceId = randomId();
    String instanceJsonOld = instance(instanceId);
    String instanceJsonNew = instance(instanceId);
    var consortiumInstanceOld = consortiumInstance(0, instanceId, instanceJsonOld);
    var consortiumInstanceNew = consortiumInstance(0, instanceId, instanceJsonNew);

    var startTimestamp = new Timestamp(System.currentTimeMillis());
    repository.save(List.of(consortiumInstanceOld));

    var updateTimestamp = new Timestamp(System.currentTimeMillis());
    repository.save(List.of(consortiumInstanceNew));

    assertThat(getDbRecords())
      .hasSize(1)
      .allSatisfy(dbRecord -> assertThat(dbRecord.tenantId()).isIn(TENANTS[0], TENANTS[1]))
      .allSatisfy(dbRecord -> assertThat(dbRecord.instanceId()).isEqualTo(instanceId))
      .allSatisfy(dbRecord -> assertThat(dbRecord.json()).isEqualTo(instanceJsonNew))
      .allSatisfy(dbRecord -> assertThat(dbRecord.created()).isAfter(startTimestamp))
      .allSatisfy(dbRecord -> assertThat(dbRecord.updated()).isAfter(updateTimestamp));
  }

  @Test
  @SneakyThrows
  void testDelete_positive_instanceDeletedByIdAndTenant() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var consortiumInstance1 = consortiumInstance(0, instanceId1, instance(instanceId1));
    var consortiumInstance2 = consortiumInstance(0, instanceId2, instance(instanceId2));
    var consortiumInstance3 = consortiumInstance(1, instanceId1, instance(instanceId2));

    repository.save(List.of(consortiumInstance1, consortiumInstance2, consortiumInstance3));
    assertThat(getDbRecords()).hasSize(3);

    repository.delete(Set.of(new ConsortiumInstanceId(TENANTS[0], instanceId1)));

    assertThat(getDbRecords())
      .hasSize(2)
      .extracting(DbRecord::instanceId, DbRecord::tenantId)
      .containsExactly(tuple(instanceId2, TENANTS[0]), tuple(instanceId1, TENANTS[1]));
  }

  @Test
  @SneakyThrows
  void testFetch_positive_instancesFetched() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var instanceId3 = randomId();
    var consortiumInstance1 = consortiumInstance(0, instanceId1, instance(instanceId1));
    var consortiumInstance2 = consortiumInstance(1, instanceId2, instance(instanceId2));
    var consortiumInstance3 = consortiumInstance(2, instanceId3, instance(instanceId3));

    repository.save(List.of(consortiumInstance1, consortiumInstance2, consortiumInstance3));

    var actual = repository.fetch(List.of(instanceId1, instanceId3));

    assertThat(actual)
      .hasSize(2)
      .allSatisfy(instance -> assertThat(instance.id().tenantId()).isIn(TENANTS[0], TENANTS[2]))
      .allSatisfy(instance -> assertThat(instance.id().instanceId()).isIn(instanceId1, instanceId3))
      .allSatisfy(instance -> assertThat(instance.instance()).isNotBlank());
  }

  private ConsortiumInstance consortiumInstance(int x, String instanceId, String instanceJsonOld) {
    return new ConsortiumInstance(new ConsortiumInstanceId(TENANTS[x], instanceId), instanceJsonOld);
  }

  private String instance(String instanceId) throws JsonProcessingException {
    var instance = new Instance().id(instanceId).title(randomAlphabetic(10))
      .holdings(List.of(new Holding().id(randomId()).callNumber(randomNumeric(10))))
      .items(List.of(new Item().id(randomId()).barcode(randomNumeric(10))));
    return mapper.writeValueAsString(instance);
  }

  private List<DbRecord> getDbRecords() {
    return jdbcTemplate.query("SELECT * FROM " + TABLE_NAME, (rs, rowNum) -> {
      var tenantId = rs.getString("tenant_id");
      var id = rs.getString("instance_id");
      var json = rs.getString("json");
      var createdDate = rs.getTimestamp("created_date");
      var updatedDate = rs.getTimestamp("updated_date");
      return new DbRecord(tenantId, id, json, createdDate, updatedDate);
    });
  }

  private record DbRecord(String tenantId,
                          String instanceId,
                          String json,
                          Timestamp created,
                          Timestamp updated) { }

}
