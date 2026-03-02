package org.folio.search.exception;

import lombok.Getter;
import org.folio.search.model.types.ErrorCode;
import org.folio.spring.integration.XOkapiHeaders;

@Getter
public class RequestValidationException extends BaseSearchException {

  public static final String REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG =
    "The request not allowed for member tenant of consortium environment";
  public static final String REQUEST_NOT_ALLOWED_WITH_TARGET_TENANT_MSG =
    "Target tenant ID is allowed only from consortium central tenant with a value of consortium member tenant";

  private final String key;
  private final String value;

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param key     - validation key as field or parameter name
   * @param value   - invalid parameter value
   */
  public RequestValidationException(String message, String key, String value) {
    super(message, ErrorCode.VALIDATION_ERROR);

    this.key = key;
    this.value = value;
  }

  public static RequestValidationException memberTenantNotAllowedException(String tenantId) {
    return new RequestValidationException(REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG,
      XOkapiHeaders.TENANT, tenantId);
  }

  public static RequestValidationException targetTenantNotAllowedException(String targetTenantId) {
    return new RequestValidationException(REQUEST_NOT_ALLOWED_WITH_TARGET_TENANT_MSG, "targetTenantId", targetTenantId);
  }
}
