package org.folio.search.service.indexrebuild;

import java.util.List;

/**
 * Tenants covered by a rebuild.
 *
 * @param tenants    tenants covered by the rebuild
 * @param wholeIndex true if the rebuild covers the whole index, false if only documents of a single tenant are reloaded
 */
public record IndexRebuildScope(List<String> tenants, boolean wholeIndex) { }
