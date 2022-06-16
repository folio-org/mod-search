package org.folio.search.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.opensearch.common.ParseField;
import org.opensearch.common.xcontent.ConstructingObjectParser;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.search.aggregations.ParsedAggregation;

@Getter
public class ParsedStringStats extends ParsedAggregation {
  private static final ParseField COUNT_FIELD = new ParseField("count");
  private static final ParseField MIN_LENGTH_FIELD = new ParseField("min_length");
  private static final ParseField MAX_LENGTH_FIELD = new ParseField("max_length");
  private static final ParseField AVG_LENGTH_FIELD = new ParseField("avg_length");
  private static final ParseField ENTROPY_FIELD = new ParseField("entropy");
  private static final ParseField DISTRIBUTION_FIELD = new ParseField("distribution");
  private static final Object NULL_DISTRIBUTION_MARKER = new Object();

  public static final ConstructingObjectParser<ParsedStringStats, String> PARSER =
    new ConstructingObjectParser<>("string_stats", true, (args, name) -> {
      long count = (Long) args[0];
      boolean disributionWasExplicitNull = args[5] == NULL_DISTRIBUTION_MARKER;
      if (count == 0L) {
        return new ParsedStringStats(name, count, 0, 0, 0.0, 0.0, disributionWasExplicitNull, null);
      } else {
        int minLength = (Integer) args[1];
        int maxLength = (Integer) args[2];
        double averageLength = (Double) args[3];
        double entropy = (Double) args[4];
        if (disributionWasExplicitNull) {
          return new ParsedStringStats(name, count, minLength, maxLength, averageLength, entropy,
            disributionWasExplicitNull, null);
        } else {
          @SuppressWarnings("unchecked")
          Map<String, Double> distribution = (Map<String, Double>) args[5];
          return new ParsedStringStats(name, count, minLength, maxLength, averageLength, entropy, distribution != null,
            distribution);
        }
      }
    });

  static {
    PARSER.declareLong(ConstructingObjectParser.constructorArg(), COUNT_FIELD);
    PARSER.declareIntOrNull(ConstructingObjectParser.constructorArg(), 0, MIN_LENGTH_FIELD);
    PARSER.declareIntOrNull(ConstructingObjectParser.constructorArg(), 0, MAX_LENGTH_FIELD);
    PARSER.declareDoubleOrNull(ConstructingObjectParser.constructorArg(), 0.0, AVG_LENGTH_FIELD);
    PARSER.declareDoubleOrNull(ConstructingObjectParser.constructorArg(), 0.0, ENTROPY_FIELD);
    PARSER.declareObjectOrNull(ConstructingObjectParser.optionalConstructorArg(),
      (p, c) -> Collections.unmodifiableMap(p.map(HashMap::new, XContentParser::doubleValue)), NULL_DISTRIBUTION_MARKER,
      DISTRIBUTION_FIELD);
    ParsedAggregation.declareAggregationFields(PARSER);
  }

  private final long count;
  private final int minLength;
  private final int maxLength;
  private final double avgLength;
  private final double entropy;
  private final boolean showDistribution;
  private final Map<String, Double> distribution;

  private ParsedStringStats(String name, long count, int minLength, int maxLength, double avgLength, double entropy,
                            boolean showDistribution, Map<String, Double> distribution) {
    this.setName(name);
    this.count = count;
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.avgLength = avgLength;
    this.entropy = entropy;
    this.showDistribution = showDistribution;
    this.distribution = distribution;
  }


  public String getType() {
    return "string_stats";
  }

  protected XContentBuilder doXContentBody(XContentBuilder builder, ToXContent.Params params) throws IOException {
    builder.field(COUNT_FIELD.getPreferredName(), this.count);
    if (this.count == 0L) {
      builder.nullField(MIN_LENGTH_FIELD.getPreferredName());
      builder.nullField(MAX_LENGTH_FIELD.getPreferredName());
      builder.nullField(AVG_LENGTH_FIELD.getPreferredName());
      builder.field(ENTROPY_FIELD.getPreferredName(), 0.0);
    } else {
      builder.field(MIN_LENGTH_FIELD.getPreferredName(), this.minLength);
      builder.field(MAX_LENGTH_FIELD.getPreferredName(), this.maxLength);
      builder.field(AVG_LENGTH_FIELD.getPreferredName(), this.avgLength);
      builder.field(ENTROPY_FIELD.getPreferredName(), this.entropy);
    }

    if (this.showDistribution) {
      builder.field(DISTRIBUTION_FIELD.getPreferredName(), this.distribution);
    }

    return builder;
  }
}

