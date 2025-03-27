package org.folio.support.sample;

import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.support.utils.JsonTestUtils.readJsonFromFile;
import static org.folio.support.utils.JsonTestUtils.toObject;

import java.util.Map;
import lombok.experimental.UtilityClass;
import org.folio.search.domain.dto.InstanceSearchResult;

@UtilityClass
public class SampleInstancesResponse {

  public static InstanceSearchResult getInstanceBasicResponseSample() {
    return convertMap(readSampleInstanceResponse("instance-basic-response"));
  }

  public static InstanceSearchResult getInstanceFullResponseSample() {
    return convertMap(readSampleInstanceResponse("instance-full-response"));
  }

  private static InstanceSearchResult convertMap(Map<String, Object> instanceFullResponseObject) {
    return toObject(instanceFullResponseObject, InstanceSearchResult.class);
  }

  private static Map<String, Object> readSampleInstanceResponse(String sampleName) {
    var path = "/samples/instance-response-sample/";
    return readJsonFromFile(path + sampleName + ".json", MAP_TYPE_REFERENCE);
  }
}
