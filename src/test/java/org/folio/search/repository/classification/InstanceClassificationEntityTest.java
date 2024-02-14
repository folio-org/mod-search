package org.folio.search.repository.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InstanceClassificationEntityTest {

  @Test
  void constructor_AllParametersProvided_SuccessfullyCreated() {
    // Arrange
    InstanceClassificationEntity.Id id =
      new InstanceClassificationEntity.Id("type", "number", "instanceId", "tenantId");
    boolean shared = true;

    // Act
    InstanceClassificationEntity entity = new InstanceClassificationEntity(id, shared);

    // Assert
    assertEquals(id, entity.id());
    assertEquals(shared, entity.shared());
  }

  @Test
  void constructor_NullId_ThrowsNullPointerException() {
    // Act & Assert
    assertThrows(NullPointerException.class, () -> new InstanceClassificationEntity(null, true));
  }

  @Test
  void builder_AllParametersProvided_SuccessfullyCreated() {
    // Arrange
    String type = "type";
    String number = "number";
    String instanceId = "instanceId";
    String tenantId = "tenantId";
    boolean shared = true;

    // Act
    InstanceClassificationEntity.Id id = InstanceClassificationEntity.Id.builder()
      .typeId(type)
      .number(number)
      .instanceId(instanceId)
      .tenantId(tenantId)
      .build();

    InstanceClassificationEntity entity = new InstanceClassificationEntity(id, shared);

    // Assert
    assertEquals(id, entity.id());
    assertEquals(shared, entity.shared());
  }

  @Test
  void idBuilder_NullNumber_ThrowsNullPointerException() {
    // Act & Assert
    var builder = InstanceClassificationEntity.Id.builder().number(null);
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void idBuilder_NullInstanceId_ThrowsNullPointerException() {
    // Act & Assert
    var builder = InstanceClassificationEntity.Id.builder().instanceId(null);
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  void idBuilder_NullTenantId_ThrowsNullPointerException() {
    // Act & Assert
    var builder = InstanceClassificationEntity.Id.builder().tenantId(null);
    assertThrows(NullPointerException.class, builder::build);
  }
}
