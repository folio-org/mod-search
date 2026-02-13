package org.folio.support.utils;

import static java.util.Objects.requireNonNull;
import static org.folio.search.utils.CallNumberUtils.calculateFullCallNumber;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.index.CallNumberResource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.shadow.de.siegmar.fastcsv.reader.CsvReader;
import org.junit.jupiter.params.shadow.de.siegmar.fastcsv.reader.NamedCsvRecord;

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
      Integer.parseInt(csvRecord.getField(0)),
      csvRecord.getField(1)
    )).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static List<Instance> instances() {
    var instancesPath = "/samples/cn-browse/instances-with-call-numbers.csv";
    var callNumbers = callNumbers();
    return readCsvEntities(instancesPath, instanceMapper(callNumbers));
  }

  public static Instance instance(String instanceNum, List<CallNumberTestDataRecord> callNumbers) {
    var holdingId = randomId();
    var holding = new Holding().id(holdingId);

    var items = callNumbers.stream()
      .map(callNumberResource -> {
          var callNumber = callNumberResource.callNumber();
          var locationId = callNumberResource.locationId();
          return new Item()
            .id(randomId())
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

  private static Function<NamedCsvRecord, Instance> instanceMapper(List<CallNumberTestDataRecord> callNumbers) {
    return csvRecord -> {
      var id = getField(csvRecord, InstanceCsvHeader.ID.getHeader());
      var callNumberList = Arrays.stream(getField(csvRecord, InstanceCsvHeader.CALL_NUMBER_NUMS.getHeader()).split(";"))
        .map(callNumberId -> callNumbers.stream()
          .filter(cn -> cn.callNumber().id().equals(callNumberId))
          .findFirst())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
      return instance(id, callNumberList);
    };
  }

  private static Function<NamedCsvRecord, CallNumberTestDataRecord> callNumberMapper(Map<Integer, String> locations) {
    return csvRecord -> {
      var id = getField(csvRecord, CallNumberCsvHeader.ID.getHeader());
      var callNumber = getField(csvRecord, CallNumberCsvHeader.CALL_NUMBER.getHeader());
      var typeId = CallNumberTypeId.getIdByName(getField(csvRecord, CallNumberCsvHeader.TYPE.getHeader()));
      var prefix = getField(csvRecord, CallNumberCsvHeader.PREFIX.getHeader());
      var suffix = getField(csvRecord, CallNumberCsvHeader.SUFFIX.getHeader());
      var fullCallNumber = calculateFullCallNumber(callNumber, suffix);
      var callNumberResource = new CallNumberResource(id, fullCallNumber, callNumber, prefix, suffix, typeId, null);
      var locationNum = getField(csvRecord, CallNumberCsvHeader.LOCATION.getHeader());
      var locationId = locations.get(Integer.parseInt(requireNonNull(locationNum)));
      return new CallNumberTestDataRecord(callNumberResource, locationId);
    };
  }

  private static @Nullable String getField(NamedCsvRecord csvRecord, String header) {
    var value = csvRecord.getField(header);
    return StringUtils.isBlank(value) ? null : value;
  }

  @SneakyThrows
  private static <T> List<T> readCsvEntities(String filePath, Function<NamedCsvRecord, T> mapper) {
    try (var inputStream = CallNumberTestData.class.getResourceAsStream(filePath)) {
      return CsvReader.builder()
        .ofNamedCsvRecord(requireNonNull(inputStream)).stream().map(mapper).toList();
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
