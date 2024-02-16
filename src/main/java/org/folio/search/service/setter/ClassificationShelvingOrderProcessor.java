package org.folio.search.service.setter;

import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.folio.search.cql.SuDocCallNumber;
import org.folio.search.integration.ClassificationTypeHelper;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.types.ClassificationType;
import org.folio.search.utils.CallNumberUtils;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassificationShelvingOrderProcessor implements FieldProcessor<ClassificationResource, String> {

  private static final Map<ClassificationType, Function<String, String>> CN_TYPE_TO_SHELF_KEY_GENERATOR = Map.of(
    ClassificationType.NLM, cn -> getShelfKey(new NlmCallNumber(cn)),
    ClassificationType.LC, cn -> getShelfKey(new LCCallNumber(cn)),
    ClassificationType.DEWEY, cn -> getShelfKey(new DeweyCallNumber(cn)),
    ClassificationType.SUDOC, cn -> getShelfKey(new SuDocCallNumber(cn)),
    ClassificationType.DEFAULT, CallNumberUtils::normalizeEffectiveShelvingOrder
  );

  private final ClassificationTypeHelper classificationTypeHelper;

  @Override
  public String getFieldValue(ClassificationResource eventBody) {
    var number = eventBody.number();
    var typeId = eventBody.typeId();
    var classificationByIdMap = classificationTypeHelper.getClassificationTypeMap();
    var classificationType = classificationByIdMap.getOrDefault(typeId, ClassificationType.DEFAULT);
    return CN_TYPE_TO_SHELF_KEY_GENERATOR.get(classificationType).apply(number);
  }

  private static String getShelfKey(CallNumber value) {
    return value.getShelfKey();
  }

}
