package org.folio.search.cql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.marc4j.callnum.AbstractCallNumber;
import org.marc4j.callnum.Utils;

public class SuDocCallNumber extends AbstractCallNumber {
  private static final Pattern STEM_PATTERN = Pattern.compile("^([A-Za-z]+)\\s*(\\d+)?(.*)$");
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z]+|\\d+|[:/.-]");
  private static final String LETTER_TOKEN_PREFIX = "1";
  private static final String DATE_TOKEN_PREFIX = "2";
  private static final String NUMBER_TOKEN_PREFIX = "3";

  protected String authorSymbol;
  protected String subordinateOffice;
  protected String series;
  protected String subSeries;

  protected String suffix;
  protected String shelfKey;

  public SuDocCallNumber(String callNumber) {
    this.parse(callNumber);
  }

  protected void init() {
    this.rawCallNum = null;
    this.authorSymbol = null;
    this.subordinateOffice = null;
    this.series = null;
    this.subSeries = null;
    this.suffix = null;
    this.shelfKey = null;
  }

  @Override
  public void parse(String s) {
    this.init();
    if (s == null) {
      this.rawCallNum = null;
    } else {
      this.rawCallNum = s.trim();
    }

    this.parse();
  }

  protected void parse() {
    if (this.rawCallNum != null) {
      this.parseCallNumber();
    }
  }

  protected void parseCallNumber() {
    var stemAndSuffix = rawCallNum.split(":", 2);
    var stem = stemAndSuffix[0].trim();
    suffix = stemAndSuffix.length > 1 ? StringUtils.trimToNull(stemAndSuffix[1]) : null;

    var stemMatcher = STEM_PATTERN.matcher(stem);
    if (!stemMatcher.matches()) {
      suffix = rawCallNum;
      return;
    }

    authorSymbol = StringUtils.trimToNull(stemMatcher.group(1));
    subordinateOffice = StringUtils.trimToNull(stemMatcher.group(2));
    series = StringUtils.trimToNull(stemMatcher.group(3));
    subSeries = null;
  }

  @Override
  public String getShelfKey() {
    if (shelfKey == null) {
      buildShelfKey();
    }
    return shelfKey;
  }

  @Override
  public boolean isValid() {
    return authorSymbol != null;
  }

  private void buildShelfKey() {
    StringBuilder key = new StringBuilder();
    appendTokens(key, rawCallNum);
    shelfKey = key.toString();
  }

  private void appendTokens(StringBuilder key, String cnPart) {
    if (StringUtils.isBlank(cnPart)) {
      return;
    }

    Matcher tokenMatcher = TOKEN_PATTERN.matcher(cnPart);
    var afterColon = false;
    while (tokenMatcher.find()) {
      var part = tokenMatcher.group();
      if (!key.isEmpty()) {
        key.append(' ');
      }

      if (!appendSeparator(key, part)) {
        appendSortableToken(key, part, afterColon);
      }
      afterColon = afterColon || ":".equals(part);
    }
  }

  private void appendSortableToken(StringBuilder key, String token, boolean afterColon) {
    if (Character.isLetter(token.charAt(0))) {
      key.append(LETTER_TOKEN_PREFIX);
      key.append(token.toUpperCase());
      return;
    }

    key.append(isDateToken(token, afterColon) ? DATE_TOKEN_PREFIX : NUMBER_TOKEN_PREFIX);
    Utils.appendNumericallySortable(key, token);
  }

  private boolean isDateToken(String token, boolean afterColon) {
    return afterColon && token.length() >= 3 && token.length() <= 4;
  }

  private boolean appendSeparator(StringBuilder key, String token) {
    if (token.length() != 1) {
      return false;
    }

    return switch (token.charAt(0)) {
      case '.' -> {
        key.append("01");
        yield true;
      }
      case ':' -> {
        key.append("02");
        yield true;
      }
      case '-' -> {
        key.append("03");
        yield true;
      }
      case '/' -> {
        key.append("04");
        yield true;
      }
      default -> false;
    };
  }
}
