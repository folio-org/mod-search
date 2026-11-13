package org.folio.support.testdata;

import static java.util.Collections.emptyList;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.utils.CallNumberTestData.CallNumberTestDataRecord;
import static org.folio.support.utils.CallNumberTestData.callNumbers;
import static org.folio.support.utils.CallNumberTestData.instance;
import static org.folio.support.utils.CallNumberTestData.instances;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.folio.search.domain.dto.Classification;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.model.Pair;
import org.folio.support.testdata.SharedTestDataManager.LockManager;

/**
 * Shared ECS (consortium) inventory data set used by the consortium suite. The data is defined here, not in the
 * test classes, so that every consortium test class reads the same fixtures.
 *
 * <p>Data set by tenant:
 * <ul>
 *   <li>central: 30 call number instances, 3 contributor instances (shared), 5 classification instances
 *       and 5 subject instances;</li>
 *   <li>member: the remaining 33 call number instances, 4 contributor instances, 5 classification instances,
 *       10 subject instances, the shared call number instance 0 as a local copy and the semantic-web instance
 *       (with its holdings and items);</li>
 *   <li>member2: one call number instance with {@link #MEMBER2_LOCATION} and a copy of the shared call number
 *       instance 0.</li>
 * </ul>
 */
public final class ConsortiumTestDataManager {

  // call number data
  public static final String MEMBER2_LOCATION = UUID.randomUUID().toString();
  // classification data
  public static final String LC_TYPE_ID = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  public static final String LC2_TYPE_ID = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  public static final String DEWEY_TYPE_ID = "50524585-046b-49a1-8ca7-8d46f2a8dc19";
  // contributor data
  public static final String[] CONTRIBUTOR_NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985", "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e");
  public static final String[] CONTRIBUTOR_TYPE_IDS =
    array("2a165833-1673-493f-934b-f3d3c8fcb299", "3ae36e29-e38f-457c-8fcf-1974a6cb63d3",
      "653ffe66-aa3f-4f1c-a090-c42c4011ef40");
  public static final String[] CONTRIBUTOR_AUTHORITY_IDS =
    array("0a4c6d10-2161-4f64-aace-9e919489b6c9", "7ff32633-cc49-4332-870a-b05e329d2a2d");
  // subject data
  public static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  public static final String MUSIC_AUTHORITY_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  public static final String MUSIC_SOURCE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91b";
  public static final String MUSIC_SOURCE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bd";
  public static final String MUSIC_TYPE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91c";
  public static final String MUSIC_TYPE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21be";

  // expected sub-resource document counts in the central tenant index for the whole data set
  public static final int CALL_NUMBER_COUNT = 103;
  public static final int CONTRIBUTOR_COUNT = 18;
  public static final int CLASSIFICATION_COUNT = 19;
  public static final int SUBJECT_COUNT = 30;

  private static final List<Instance> CALL_NUMBER_INSTANCES = instances();
  private static final List<Instance> CLASSIFICATION_INSTANCES_CENTRAL = classificationInstances(0, 5);
  private static final List<Instance> CLASSIFICATION_INSTANCES_MEMBER = classificationInstances(5, 10);
  private static final List<Instance> CONTRIBUTOR_INSTANCES_CENTRAL = contributorInstances(0, 3);
  private static final List<Instance> CONTRIBUTOR_INSTANCES_MEMBER = contributorInstances(3, 7);
  private static final List<Instance> SUBJECT_INSTANCES_CENTRAL = subjectInstances(0, 5);
  private static final List<Instance> SUBJECT_INSTANCES_MEMBER = subjectInstances(5, 15);

  private ConsortiumTestDataManager() { }

  /**
   * Returns the call number instances in creation order: the first 30 are central, the rest are member ones.
   */
  public static List<Instance> callNumberInstances() {
    return CALL_NUMBER_INSTANCES;
  }

  /**
   * Creates the whole ECS inventory data set while holding all sub-resource locks, so that scheduled
   * sub-resource processing does not race with the data load.
   */
  public static void loadInventory(LockManager lockManager, InventoryWriter writer) {
    lockManager.lockAll();
    try {
      saveCallNumberData(writer);
      writer.createInstances(CENTRAL_TENANT_ID, CLASSIFICATION_INSTANCES_CENTRAL);
      writer.createInstances(MEMBER_TENANT_ID, CLASSIFICATION_INSTANCES_MEMBER);
      saveContributorData(writer);
      writer.createInstances(CENTRAL_TENANT_ID, SUBJECT_INSTANCES_CENTRAL);
      writer.createInstances(MEMBER_TENANT_ID, SUBJECT_INSTANCES_MEMBER);
      writer.createInstances(MEMBER_TENANT_ID, List.of(getSemanticWeb()));
    } finally {
      lockManager.unlockAll();
    }
  }

  private static void saveCallNumberData(InventoryWriter writer) {
    var centralInstances = CALL_NUMBER_INSTANCES.subList(0, 30);
    writer.createInstances(CENTRAL_TENANT_ID, centralInstances);

    var memberInstances = CALL_NUMBER_INSTANCES.subList(30, CALL_NUMBER_INSTANCES.size());
    writer.createInstances(MEMBER_TENANT_ID, memberInstances);

    var instance1 = centralInstances.getFirst();
    instance1.setSource(SOURCE_CONSORTIUM_PREFIX + "FOLIO");
    instance1.getItems().forEach(item -> item.setId(UUID.randomUUID().toString()));
    writer.createInstance(MEMBER_TENANT_ID, instance1);

    var dataRecord = new CallNumberTestDataRecord(callNumbers().getLast().callNumber(), MEMBER2_LOCATION);
    var member2Instance = instance("51", List.of(dataRecord));
    instance1.getItems().forEach(item -> item.setId(UUID.randomUUID().toString()));
    writer.createInstances(MEMBER2_TENANT_ID, List.of(member2Instance, instance1));
  }

  private static void saveContributorData(InventoryWriter writer) {
    writer.createInstances(CENTRAL_TENANT_ID, CONTRIBUTOR_INSTANCES_CENTRAL);
    writer.createInstances(MEMBER_TENANT_ID, CONTRIBUTOR_INSTANCES_MEMBER);

    // this is needed to test deleting contributors when all instances are unlinked from a contributor
    var instanceToUpdate = CONTRIBUTOR_INSTANCES_CENTRAL.getFirst();
    instanceToUpdate.setContributors(emptyList());
    writer.updateInstance(CENTRAL_TENANT_ID, instanceToUpdate);
  }

  private static List<Instance> classificationInstances(int from, int to) {
    return classificationBrowseInstanceData().subList(from, to).stream()
      .map(ConsortiumTestDataManager::classificationInstance)
      .toList();
  }

  @SuppressWarnings("unchecked")
  private static Instance classificationInstance(List<Object> data) {
    var pairs = (List<Pair<String, String>>) data.get(1);
    var instance = new Instance()
      .id(randomId())
      .title((String) data.get(0))
      .classifications(pairs.stream()
        .map(pair -> new Classification()
          .classificationNumber(String.valueOf(pair.getFirst()))
          .classificationTypeId(String.valueOf(pair.getSecond())))
        .toList())
      .staffSuppress(false)
      .discoverySuppress(false)
      .holdings(emptyList());

    if (data.size() > 2) {
      var contributors = (List<String>) data.get(2);
      instance.setContributors(contributors.stream()
        .map(name -> new Contributor().name(name))
        .toList());
    }

    return instance;
  }

  private static List<List<Object>> classificationBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of(pair("BJ1453 .I49 1983", LC_TYPE_ID), pair("HD1691 .I5 1967", LC_TYPE_ID))),
      List.of("instance #02", List.of(pair("BJ1453 .I49 1983", LC2_TYPE_ID))),
      List.of("instance #03", List.of(pair("HQ536 .A565 2018", LC2_TYPE_ID), pair("N6679.R64 G88 2010", LC_TYPE_ID))),
      List.of("instance #04", List.of(pair("QD33 .O87", LC_TYPE_ID)), List.of("Contributor 1", "Contributor 2")),
      List.of("instance #05", List.of(pair("QD453 .M8 1961", LC_TYPE_ID), pair("146.4", DEWEY_TYPE_ID))),
      List.of("instance #06", List.of(pair("SF433 .D47 2004", LC_TYPE_ID), pair("TX545 .M45", LC_TYPE_ID))),
      List.of("instance #07", List.of(pair("221.609", DEWEY_TYPE_ID), pair("SF991 .M94", LC2_TYPE_ID))),
      List.of("instance #08", List.of(pair("TN800 .F4613", LC_TYPE_ID))),
      List.of("instance #09", List.of(pair("292.07", DEWEY_TYPE_ID), pair("333.91", DEWEY_TYPE_ID),
        pair("372.4", DEWEY_TYPE_ID))),
      List.of("instance #10", List.of(pair("146.4", DEWEY_TYPE_ID), pair("QD33 .O87", LC_TYPE_ID),
        pair("SF991 .M94", null)), List.of("Contributor #3", "Contributor #4"))
    );
  }

  private static List<Instance> contributorInstances(int from, int to) {
    return contributorBrowseInstanceData().subList(from, to).stream()
      .map(ConsortiumTestDataManager::contributorInstance)
      .toList();
  }

  @SuppressWarnings("unchecked")
  private static Instance contributorInstance(List<Object> data) {
    return new Instance().id(randomId()).title((String) data.get(0)).contributors((List<Contributor>) data.get(1))
      .staffSuppress(false).discoverySuppress(false).holdings(emptyList());
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static List<List<Object>> contributorBrowseInstanceData() {
    var nameTypes = CONTRIBUTOR_NAME_TYPE_IDS;
    var authorities = CONTRIBUTOR_AUTHORITY_IDS;
    var types = CONTRIBUTOR_TYPE_IDS;
    return List.of(
      List.of("instance #00", List.of(
        contributor("Darth Vader", nameTypes[0], authorities[0], types[0])
      )),
      List.of("instance #01", List.of(
        contributor("Bon Jovi", nameTypes[0], authorities[0], types[0]),
        contributor("Klaus Meine", nameTypes[0], authorities[1], types[0]),
        contributor("Anthony Kiedis", nameTypes[0], authorities[1], types[0])
      )),
      List.of("instance #02", List.of(
        contributor("Bon Jovi", nameTypes[1], authorities[1], types[0]),
        contributor("Klaus Meine", nameTypes[0], authorities[1], types[1]),
        contributor("Anthony Kiedis", nameTypes[1], authorities[1], types[2])
      )),
      List.of("instance #03", List.of(
        contributor("Bon Jovi", nameTypes[0], authorities[0], types[1]),
        contributor("Bon Jovi", nameTypes[0], authorities[0], types[2]),
        contributor("Klaus Meine", nameTypes[1], authorities[0], null)
      )),
      List.of("instance #04", List.of(
        contributor("John Lennon", nameTypes[2], authorities[1], types[0]),
        contributor("Paul McCartney", nameTypes[0], authorities[0], types[0]),
        contributor("George Harrison", nameTypes[1], authorities[0], types[2]),
        contributor("Ringo Starr", nameTypes[1], authorities[1], types[0])
      )),
      List.of("instance #05", List.of(
        contributor("John Lennon", nameTypes[2], authorities[1], types[0]),
        contributor("Paul McCartney", nameTypes[0], authorities[0], types[1]),
        contributor("George Harrison", nameTypes[1], authorities[0], types[2]),
        contributor("Ringo Starr", nameTypes[1], authorities[1], types[1])
      )),
      List.of("instance #06", List.of(
        contributor("Paul McCartney", nameTypes[0], authorities[1], types[2]),
        contributor("John Lennon", nameTypes[2], null, types[0])
      )));
  }

  private static Contributor contributor(String name, String nameTypeId, String authorityId, String typeId) {
    return new Contributor()
      .name(name)
      .contributorNameTypeId(nameTypeId)
      .contributorTypeId(typeId)
      .authorityId(authorityId);
  }

  private static List<Instance> subjectInstances(int from, int to) {
    return subjectBrowseInstanceData().subList(from, to).stream()
      .map(ConsortiumTestDataManager::subjectInstance)
      .toList();
  }

  @SuppressWarnings("unchecked")
  private static Instance subjectInstance(List<Object> data) {
    return new Instance()
      .id(randomId())
      .title((String) data.get(0))
      .subjects(((List<Object>) data.get(1)).stream()
        .map(val -> {
          if (val instanceof List<?> list) {
            var subject = new Subject().value(String.valueOf(list.get(0)));
            if (list.size() == 4) {
              subject.setAuthorityId(Objects.toString(list.get(1), null));
              subject.setSourceId(Objects.toString(list.get(2), null));
              subject.setTypeId(Objects.toString(list.get(3), null));
            }
            return subject;
          } else {
            return new Subject().value(String.valueOf(val));
          }
        }).toList())
      .staffSuppress(false)
      .discoverySuppress(false)
      .holdings(emptyList());
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static List<List<Object>> subjectBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of("History",
        List.of("Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1),
        List.of("Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2), "Biography")),
      List.of("instance #02", List.of("Fantasy",
        List.of("Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2))),
      List.of("instance #03", List.of(Arrays.asList("United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2),
        "History", "Rules")),
      List.of("instance #04", List.of("Europe",
        Arrays.asList("Music", null, null, MUSIC_TYPE_ID_1))),
      List.of("instance #05", List.of("Book", "Text", "Biography")),
      List.of("instance #06", List.of("Religion", "History", "Philosophy")),
      List.of("instance #07", List.of("Science", "Science--Methodology", "Science--Philosophy")),
      List.of("instance #08", List.of("Water", "Water-supply")),
      List.of("instance #09", List.of("Water--Analysis", "Water--Purification", "Water--Microbiology")),
      List.of("instance #10", List.of("Database design", "Database management", "Textbooks")),
      List.of("instance #11", List.of("History",
        Arrays.asList("Music", null, null, MUSIC_TYPE_ID_1), "Biography")),
      List.of("instance #12", List.of("History",
        Arrays.asList("Music", MUSIC_AUTHORITY_ID_1, null, MUSIC_TYPE_ID_2), "Biography")),
      List.of("instance #13", List.of("History",
        Arrays.asList("Music", null, MUSIC_SOURCE_ID_1, null), "Biography")),
      List.of("instance #14", List.of("History",
        Arrays.asList("Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_1, null), "Biography")),
      List.of("instance #15", List.of("History",
        Arrays.asList("Music", null, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_1), "Biography"))
    );
  }

  /**
   * Writes inventory records to a tenant; creation waits until the records are visible for that tenant.
   */
  public interface InventoryWriter {

    void createInstances(String tenantId, List<Instance> instances);

    void createInstance(String tenantId, Instance instance);

    void updateInstance(String tenantId, Instance instance);
  }
}
