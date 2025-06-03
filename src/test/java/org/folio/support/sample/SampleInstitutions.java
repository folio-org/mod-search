package org.folio.support.sample;

import static org.folio.support.utils.JsonTestUtils.readJsonFromFile;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleInstitutions {

  private static final List<Map<String, Object>> INSTITUTIONS_RECORD_AS_MAP =
    readJsonFromFile("/samples/institution-sample/institutions.json", new TypeReference<>() { });

  public static List<Map<String, Object>> getInstitutionsSampleAsMap() {
    return INSTITUTIONS_RECORD_AS_MAP;
  }
}
