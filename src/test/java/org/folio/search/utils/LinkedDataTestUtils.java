package org.folio.search.utils;

import static java.lang.String.format;
import static java.lang.String.join;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LinkedDataTestUtils {

  public static String path(String path) {
    return format("['%s']", path);
  }

  public static String arrayPath(String path) {
    return arrayPath(path, 0);
  }

  public static String arrayPath(String path, int number) {
    return format("['%s'][%s]", path, number);
  }

  public static String toRootContent() {
    return join(".", "$", arrayPath("content"));
  }

  public static String toRootContent(int number) {
    return join(".", "$", arrayPath("content", number));
  }

  public static String toId(String base) {
    return join(".", base, path("id"));
  }

  public static String toTitle(String base, int number) {
    return join(".", base, arrayPath("titles", number));
  }

  public static String toTitleValue(String base, int number) {
    return join(".", toTitle(base, number), path("value"));
  }

  public static String toTitleType(String base, int number) {
    return join(".", toTitle(base, number), path("type"));
  }

  public static String toContributor(String base, int number) {
    return join(".", base, arrayPath("contributors", number));
  }

  public static String toContributorName(String base, int number) {
    return join(".", toContributor(base, number), path("name"));
  }

  public static String toContributorType(String base, int number) {
    return join(".", toContributor(base, number), path("type"));
  }

  public static String toContributorIsCreator(String base, int number) {
    return join(".", toContributor(base, number), path("isCreator"));
  }

  public static String toHubAap(String base, int number) {
    return join(".", base, arrayPath("hubAAPs", number));
  }


  public static String toLanguage(String base, int number) {
    return join(".", base, arrayPath("languages", number));
  }

  public static String toClassification(String base, int number) {
    return join(".", base, arrayPath("classifications", number));
  }

  public static String toClassificationType(String base, int number) {
    return join(".", toClassification(base, number), path("type"));
  }

  public static String toClassificationNumber(String base, int number) {
    return join(".", toClassification(base, number), path("number"));
  }

  public static String toClassificationAdditionalNumber(String base, int number) {
    return join(".", toClassification(base, number), path("additionalNumber"));
  }

  public static String toSubject(String base, int number) {
    return join(".", base, arrayPath("subjects", number));
  }

  public static String toInstance() {
    return join(".", toRootContent(), arrayPath("instances"));
  }

  public static String toIdValue(String base, int number) {
    return join(".", base, arrayPath("identifiers", number), path("value"));
  }

  public static String toIdType(String base, int number) {
    return join(".", base, arrayPath("identifiers", number), path("type"));
  }

  public static String toPublicationName(String base, int number) {
    return join(".", base, arrayPath("publications", number), path("name"));
  }

  public static String toPublicationDate(String base, int number) {
    return join(".", base, arrayPath("publications", number), path("date"));
  }

  public static String toEditionStatement(String base, int number) {
    return join(".", base, arrayPath("editionStatements", number));
  }

  public static String toNote(String base, int number) {
    return join(".", base, arrayPath("notes", number));
  }

  public static String toNoteValue(String base, int number) {
    return join(".", toNote(base, number), path("value"));
  }

  public static String toNoteType(String base, int number) {
    return join(".", toNote(base, number), path("type"));
  }

  public static String toFormat(String base) {
    return join(".", base, path("format"));
  }

  public static String toSuppressFromDiscovery(String base) {
    return join(".", base, path("suppress"), path("fromDiscovery"));
  }

  public static String toSuppressStaff(String base) {
    return join(".", base, path("suppress"), path("staff"));
  }

  public static String toTotalRecords() {
    return join(".", "$", path("totalRecords"));
  }

  public static String toParentWork() {
    return join(".", toRootContent(), path("parentWork"));
  }
}
