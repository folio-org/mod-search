package org.folio.search.service.setter;

public abstract class AbstractSharedFieldProcessor<T> implements FieldProcessor<T, Boolean> {

  public static final String CONSORTIUM_PREFIX = "CONSORTIUM-";

  @Override
  public Boolean getFieldValue(T eventBody) {
    String source = getSource(eventBody);
    return source != null && source.toUpperCase().startsWith(CONSORTIUM_PREFIX);
  }

  protected abstract String getSource(T eventBody);
}
