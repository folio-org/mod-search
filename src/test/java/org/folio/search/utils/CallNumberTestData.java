package org.folio.search.utils;

import static org.folio.search.utils.CallNumberUtils.calculateFullCallNumber;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.index.CallNumberResource;
import org.junit.jupiter.params.shadow.com.univocity.parsers.common.record.Record;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParser;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParserSettings;

@UtilityClass
public class CallNumberTestData {

  public static List<CallNumberTestDataRecord> callNumbers() {
    var callNumbersCsvPath = "/samples/cn-browse/call-numbers.csv";
    var locations = locations();
    return readCsvEntities(callNumbersCsvPath, callNumberMapper(locations));
  }

  public static Map<Integer, String> locations() {
    var locationsPath = "/samples/cn-browse/locations.csv";
    return readCsvEntities(locationsPath, csvRecord -> Map.entry(
      Integer.parseInt(csvRecord.getString(0)),
      csvRecord.getString(1)
    )).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static List<Instance> instances() {
    var instancesPath = "/samples/cn-browse/instances-with-call-numbers.csv";
    var callNumbers = callNumbers();
    return readCsvEntities(instancesPath, instanceMapper(callNumbers));
  }

  public static Instance instance(String instanceNum, List<CallNumberTestDataRecord> callNumbers) {
    var holdingId = randomId();
    var holding = new Holding().id(holdingId).tenantId(TENANT_ID);

    var items = callNumbers.stream()
      .map(callNumberResource -> {
          var callNumber = callNumberResource.callNumber();
          var locationId = callNumberResource.locationId();
          return new Item()
            .id(randomId())
            .tenantId(TENANT_ID)
            .holdingsRecordId(holdingId)
            .effectiveLocationId(locationId)
            .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents()
              .callNumber(callNumber.callNumber())
              .typeId(callNumber.callNumberTypeId())
              .prefix(callNumber.callNumberPrefix())
              .suffix(callNumber.callNumberSuffix())
            );
        }
      ).toList();

    return new Instance()
      .id(randomId())
      .title("Instance " + instanceNum)
      .tenantId(TENANT_ID)
      .items(items)
      .holdings(List.of(holding));
  }

  public static CallNumberBrowseResult cnBrowseResult(String prev, String next, int total,
                                                      List<CallNumberBrowseItem> items) {
    return new CallNumberBrowseResult().prev(prev).next(next).items(items).totalRecords(total);
  }

  public static CallNumberBrowseItem cnEmptyBrowseItem(String callNumber) {
    return new CallNumberBrowseItem().fullCallNumber(callNumber).isAnchor(true).totalRecords(0);
  }

  public static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int count, String instanceTitle) {
    return cnBrowseItem(resource, count, instanceTitle, null);
  }

  public static CallNumberBrowseItem cnBrowseItem(CallNumberResource resource, int count,
                                                  String instanceTitle, Boolean isAnchor) {
    return new CallNumberBrowseItem()
      .fullCallNumber(resource.fullCallNumber())
      .callNumber(resource.callNumber())
      .callNumberPrefix(resource.callNumberPrefix())
      .callNumberSuffix(resource.callNumberSuffix())
      .callNumberTypeId(resource.callNumberTypeId())
      .instanceTitle(instanceTitle)
      .totalRecords(count)
      .isAnchor(isAnchor);
  }

  private static Function<Record, Instance> instanceMapper(List<CallNumberTestDataRecord> callNumbers) {
    return csvRecord -> {
      var id = csvRecord.getString(InstanceCsvHeader.ID.getHeader());
      var callNumberList = Arrays.stream(csvRecord.getString(InstanceCsvHeader.CALL_NUMBER_NUMS.getHeader()).split(";"))
        .map(callNumberId -> callNumbers.stream()
          .filter(cn -> cn.callNumber().id().equals(callNumberId))
          .findFirst())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
      return instance(id, callNumberList);
    };
  }

  private static Function<Record, CallNumberTestDataRecord> callNumberMapper(Map<Integer, String> locations) {
    return csvRecord -> {
      var id = csvRecord.getString(CallNumberCsvHeader.ID.getHeader());
      var callNumber = csvRecord.getString(CallNumberCsvHeader.CALL_NUMBER.getHeader());
      var typeId = CallNumberTypeId.getIdByName(csvRecord.getString(CallNumberCsvHeader.TYPE.getHeader()));
      var prefix = csvRecord.getString(CallNumberCsvHeader.PREFIX.getHeader());
      var suffix = csvRecord.getString(CallNumberCsvHeader.SUFFIX.getHeader());
      var fullCallNumber = calculateFullCallNumber(callNumber, suffix);
      var callNumberResource = new CallNumberResource(id, fullCallNumber, callNumber, prefix, suffix, typeId, null);
      var locationNum = csvRecord.getString(CallNumberCsvHeader.LOCATION.getHeader());
      var locationId = locations.get(Integer.parseInt(locationNum));
      return new CallNumberTestDataRecord(callNumberResource, locationId);
    };
  }

  @SneakyThrows
  private static <T> List<T> readCsvEntities(String filePath, Function<Record, T> mapper) {
    var settings = new CsvParserSettings();
    settings.setHeaderExtractionEnabled(true);
    var csvParser = new CsvParser(settings);
    try (InputStream inputStream = CallNumberTestData.class.getResourceAsStream(filePath)) {
      csvParser.beginParsing(inputStream);
      return csvParser.parseAllRecords().stream()
        .map(mapper)
        .toList();
    }
  }

  @Getter
  private enum InstanceCsvHeader {
    ID("Num"),
    CALL_NUMBER_NUMS("Call Number Nums");

    private final String header;

    InstanceCsvHeader(String header) {
      this.header = header;
    }
  }

  @Getter
  private enum CallNumberCsvHeader {
    ID("Num"),
    CALL_NUMBER("Call Number"),
    TYPE("Type"),
    PREFIX("Prefix"),
    SUFFIX("Suffix"),
    VOLUME("Volume"),
    ENUMERATION("Enumeration"),
    CHRONOLOGY("Chronology"),
    COPY_NUMBER("Copy Number"),
    LOCATION("Location Num");

    private final String header;

    CallNumberCsvHeader(String header) {
      this.header = header;
    }
  }

  @Getter
  public enum CallNumberTypeId {
    LC("LC", "cbc422b0-b71a-4d2d-9a2a-9fcc56a57a3e"),
    DEWEY("DEWEY", "0b5d15ad-1738-4e9e-a9c1-e972e95ce71c"),
    SUDOC("SUDOC", "6b368b19-0d18-4689-8d15-cf904e15b3f0"),
    OTHER("OTHER", "cf74a451-41ad-49aa-aa2b-21d2d2f2e235"),
    NLM("NLM", "530b84ea-4965-4cda-9d9e-6a5ef91fd21e");

    private final String name;
    private final String id;

    CallNumberTypeId(String name, String id) {
      this.name = name;
      this.id = id;
    }

    public static String getIdByName(String name) {
      for (var value : values()) {
        if (value.name.equalsIgnoreCase(name)) {
          return value.id;
        }
      }
      return null;
    }
  }

  public record CallNumberTestDataRecord(CallNumberResource callNumber, String locationId) {
  }
}
