package org.folio.search.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLTermNode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BrowseUtils {

  public static final String CALL_NUMBER_BROWSING_FIELD = "callNumber";
  public static final String SUBJECT_BROWSING_FIELD = "subject";
  public static final String AUTHORITY_BROWSING_FIELD = "headingRef";

  /**
   * Extracts anchor call number from {@link CQLNode} object.
   *
   * @param node - {@link CQLNode} object to analyze
   * @return anchor call-number as {@link String} value
   */
  public static String getAnchorCallNumber(CQLNode node) {
    if (node instanceof CQLTermNode) {
      var termNode = (CQLTermNode) node;
      if (CALL_NUMBER_BROWSING_FIELD.equals(termNode.getIndex())) {
        return termNode.getTerm();
      }
    }

    if (node instanceof CQLBooleanNode) {
      var boolNode = (CQLBooleanNode) node;
      var rightAnchorCallNumber = getAnchorCallNumber(boolNode.getLeftOperand());
      return rightAnchorCallNumber != null ? rightAnchorCallNumber : getAnchorCallNumber(boolNode.getRightOperand());
    }

    return null;
  }
}
