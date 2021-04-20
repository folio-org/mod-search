package org.folio.search.support.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.folio.search.support.extension.impl.OkapiExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Starts WireMockServer on a random port and sets all required Spring properties.
 * In order to get OKAPI URL or WireMock you can declare a static
 * {@link org.folio.search.support.extension.impl.OkapiConfiguration} field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(OkapiExtension.class)
public @interface EnableOkapi {}
