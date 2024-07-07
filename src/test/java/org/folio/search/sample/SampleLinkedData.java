package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleLinkedData {

  private static final Map<String, Object> WORK_AS_MAP =
    readJsonFromFile("/samples/linked-data/work.json", MAP_TYPE_REFERENCE);

  private static final Map<String, Object> WORK_2_AS_MAP =
    readJsonFromFile("/samples/linked-data/work2.json", MAP_TYPE_REFERENCE);

  private static final Map<String, Object> AUTHORITY_CONCEPT_AS_MAP =
    readJsonFromFile("/samples/linked-data/authority_concept.json", MAP_TYPE_REFERENCE);

  private static final Map<String, Object> AUTHORITY_PERSON_AS_MAP =
    readJsonFromFile("/samples/linked-data/authority_person.json", MAP_TYPE_REFERENCE);

  public static Map<String, Object> getWorkSampleAsMap() {
    return WORK_AS_MAP;
  }

  public static Map<String, Object> getWork2SampleAsMap() {
    return WORK_2_AS_MAP;
  }

  public static Map<String, Object> getAuthorityConceptSampleAsMap() {
    return AUTHORITY_CONCEPT_AS_MAP;
  }

  public static Map<String, Object> getAuthorityPersonSampleAsMap() {
    return AUTHORITY_PERSON_AS_MAP;
  }
}
