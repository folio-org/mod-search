package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.Authority;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleAuthorities {

  private static final Map<String, Object> AUTHORITY_RECORD_AS_MAP =
    readJsonFromFile("/samples/authority-sample/authority.json", MAP_TYPE_REFERENCE);

  private static final Authority AUTHORITY_RECORD =
    OBJECT_MAPPER.convertValue(AUTHORITY_RECORD_AS_MAP, Authority.class);

  private static final String AUTHORITY_RECORD_ID = AUTHORITY_RECORD.getId();
  private static final String AUTHORITY_SOURCE_FILE_ID = AUTHORITY_RECORD.getSourceFileId();
  private static final String AUTHORITY_NATURAL_ID = AUTHORITY_RECORD.getNaturalId();

  public static Authority getAuthoritySample() {
    return AUTHORITY_RECORD;
  }

  public static Map<String, Object> getAuthoritySampleAsMap() {
    return AUTHORITY_RECORD_AS_MAP;
  }

  public static String getAuthoritySampleId() {
    return AUTHORITY_RECORD_ID;
  }

  public static String getAuthoritySourceFileId() {
    return AUTHORITY_SOURCE_FILE_ID;
  }

  public static String getAuthorityNaturalId() {
    return AUTHORITY_NATURAL_ID;
  }
}
