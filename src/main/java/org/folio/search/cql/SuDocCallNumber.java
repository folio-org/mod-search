package org.folio.search.cql;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.marc4j.callnum.AbstractCallNumber;
import org.marc4j.callnum.Utils;

/**
 * Represents a call number in the Superintendent of Documents (SuDoc) Classification System,
 * used by the U.S. Government Publishing Office (GPO) to organize federal government publications.
 *
 * <p>A SuDoc call number consists of two parts separated by a colon:
 * <pre>
 *   AGENCY_SYMBOL SUBORDINATE_OFFICE.SERIES_DESIGNATOR : BOOK_NUMBER
 * </pre>
 *
 * <ul>
 *   <li><b>Agency symbol</b> — one or more letters identifying the issuing agency,
 *       e.g. {@code A} = Department of Agriculture, {@code GP} = Government Publishing Office,
 *       {@code Y} = Congress.</li>
 *   <li><b>Subordinate office number</b> — integer further scoping the issuing bureau or office
 *       within the agency, e.g. {@code 13} in {@code A 13} = Forest Service.</li>
 *   <li><b>Series designator</b> — alphanumeric code (period-prefixed in the raw number)
 *       identifying the type or series of publication, e.g. {@code .28} = technical reports,
 *       {@code .2} = general publications.</li>
 *   <li><b>Book number</b> — individual item identifier after the colon; typically a year
 *       (e.g. {@code 997} for 1997), a sequential number, or a free-text string.</li>
 * </ul>
 *
 * <p>Example: {@code A 13.28:997} — Department of Agriculture ({@code A}),
 * Forest Service ({@code 13}), technical-reports series ({@code .28}), item year 1997 ({@code 997}).
 *
 * <h3>Shelf key</h3>
 *
 * <p>The shelf key is produced by tokenizing the raw call number and encoding each token with a
 * type prefix. Whitespace is ignored entirely, so {@code "A 13.28:W 63"} and
 * {@code "A13.28:W63"} yield the same key. Encoded tokens are joined with a single space.
 *
 * <p>Token encoding:
 * <pre>
 *   letter run                          → "1" + uppercased (e.g. "GP" → "1GP")
 *   digit run (general)                 → "3" + numerically padded
 *   digit run after ':' of 3–4 digits   → "2" + numerically padded (treated as year)
 *   '.'                                 → "01"
 *   ':'                                 → "02"
 *   '-'                                 → "03"
 *   '/'                                 → "04"
 * </pre>
 *
 * <p>Ordering consequences:
 * <ul>
 *   <li>Separators ({@code 01}–{@code 04}) sort before letter ({@code 1}) and numeric
 *       ({@code 2}, {@code 3}) tokens, reflecting SuDoc structural boundaries.</li>
 *   <li>Letter tokens sort before numeric tokens ({@code "1…"} &lt; {@code "2…"} &lt;
 *       {@code "3…"}).</li>
 *   <li>All digit runs compare numerically, not lexicographically (via zero-padded encoding).</li>
 *   <li>Within the book number, 3–4 digit values are treated as years and sort before
 *       longer numeric identifiers.</li>
 *   <li>Separator precedence ({@code .} &lt; {@code :} &lt; {@code -} &lt; {@code /})
 *       matches the SuDoc hierarchy: series designator ({@code .}) sorts before
 *       book-number separator ({@code :}), and dash before slash within a section.</li>
 * </ul>
 */
public class SuDocCallNumber extends AbstractCallNumber {
  private static final Pattern STEM_PATTERN = Pattern.compile("^([A-Za-z]++)\\s*+(\\d++)?+(.*)$");
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z]+|\\d+|[:/.-]");
  private static final String LETTER_TOKEN_PREFIX = "1";
  private static final String DATE_TOKEN_PREFIX = "2";
  private static final String NUMBER_TOKEN_PREFIX = "3";

  protected String agencySymbol;
  protected String subordinateOffice;
  protected String series;
  protected String subSeries;

  protected String bookNumber;
  protected String shelfKey;

  public SuDocCallNumber(String callNumber) {
    this.parse(callNumber);
  }

  protected void init() {
    this.rawCallNum = null;
    this.agencySymbol = null;
    this.subordinateOffice = null;
    this.series = null;
    this.subSeries = null;
    this.bookNumber = null;
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
    var parts = rawCallNum.split(":", 2);
    var stem = parts[0].trim();
    bookNumber = parts.length > 1 ? StringUtils.trimToNull(parts[1]) : null;

    var stemMatcher = STEM_PATTERN.matcher(stem);
    if (!stemMatcher.matches()) {
      bookNumber = rawCallNum;
      return;
    }

    agencySymbol = StringUtils.trimToNull(stemMatcher.group(1));
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
    return agencySymbol != null;
  }

  private void buildShelfKey() {
    var key = new StringBuilder();
    appendTokens(key, rawCallNum);
    shelfKey = key.toString();
  }

  private void appendTokens(StringBuilder key, String cnPart) {
    if (StringUtils.isBlank(cnPart)) {
      return;
    }

    var tokenMatcher = TOKEN_PATTERN.matcher(cnPart);
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
