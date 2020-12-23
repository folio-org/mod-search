package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.folio.search.configuration.properties.FolioKafkaProperties;
import org.folio.search.configuration.properties.FolioKafkaProperties.KafkaListenerProperties;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class FolioKafkaPropertiesTest {

  @Test
  void constructorTest() {
    var kafkaListenerProperties = new KafkaListenerProperties();
    kafkaListenerProperties.setConcurrency("1");
    kafkaListenerProperties.setTopics("test-topic");
    kafkaListenerProperties.setGroupId("test-group");

    var folioKafkaProperties = new FolioKafkaProperties();
    folioKafkaProperties.setListener(Map.of("events", kafkaListenerProperties));

    assertThat(folioKafkaProperties).isNotNull();
  }
}