package org.folio.search.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.readJsonFromFile;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.InstanceSearchResult;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SampleInstancesResponse {

  public static InstanceSearchResult getInstanceBasicResponseSample() {
    return convertMap(readSampleInstanceResponse("instance-basic-response"));
  }

  public static InstanceSearchResult getInstanceFullResponseSample() {
    return convertMap(readSampleInstanceResponse("instance-full-response"));
  }

  private static InstanceSearchResult convertMap(Map<String, Object> instanceFullResponseObject) {
    return OBJECT_MAPPER.convertValue(instanceFullResponseObject, InstanceSearchResult.class);
  }

  private static Map<String, Object> readSampleInstanceResponse(String sampleName) {
    var path = "/samples/instance-response-sample/";
    return readJsonFromFile(path + sampleName + ".json", MAP_TYPE_REFERENCE);
  }
}
