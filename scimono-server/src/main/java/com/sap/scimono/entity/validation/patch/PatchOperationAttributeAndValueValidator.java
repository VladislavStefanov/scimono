
package com.sap.scimono.entity.validation.patch;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sap.scimono.callback.schemas.SchemasCallback;
import com.sap.scimono.entity.patch.PatchOperation;
import com.sap.scimono.entity.schema.Attribute;
import com.sap.scimono.entity.schema.Schema;
import com.sap.scimono.entity.validation.AttributeAndValueValidator;
import com.sap.scimono.entity.validation.Validator;
import com.sap.scimono.exception.SCIMException;
import com.sap.scimono.helper.Strings;

public class PatchOperationAttributeAndValueValidator implements Validator<PatchOperation> {

  private final SchemasCallback schemaAPI;
  private final String coreSchemaId;
  private final Map<String, Schema> permittedSchemas;

  public PatchOperationAttributeAndValueValidator(final SchemasCallback schemaAPI, final String coreSchemaId,
      final Map<String, Schema> permittedSchemas) {
    this.schemaAPI = schemaAPI;
    this.coreSchemaId = coreSchemaId;
    this.permittedSchemas = permittedSchemas;
  }

  @Override
  public void validate(final PatchOperation operation) {
    String path = operation.getPath();
    JsonNode value = operation.getValue();

    AttributeAndValueValidator attributeAndValueValidator;
    if (Strings.isNullOrEmpty(path)) {
      Attribute coreSchemaAttribute = schemaAPI.getSchema(coreSchemaId).toAttribute();
      validateSchemaAttributes(coreSchemaAttribute, operation);
      attributeAndValueValidator = new AttributeAndValueValidator(coreSchemaAttribute, permittedSchemas);
    } else if (schemaAPI.getSchema(path) != null) {
      Attribute schemaAttribute = schemaAPI.getSchema(path).toAttribute();
      validateSchemaAttributes(schemaAttribute, operation);
      attributeAndValueValidator = new AttributeAndValueValidator(schemaAttribute, permittedSchemas);
    } else {
      String pathWithoutFilter = schemaAPI.removeValueFilterFromAttributeNotation(path);
      Attribute targetAttribute = schemaAPI.getAttribute(pathWithoutFilter);
      validatePathAttribute(targetAttribute, operation);
      attributeAndValueValidator = new AttributeAndValueValidator(targetAttribute, permittedSchemas);
    }

    attributeAndValueValidator.validate(value);
  }

  private void validatePathAttribute(final Attribute attribute, final PatchOperation operation) {
    JsonNode value = operation.getValue();

    Validator<Attribute> mutabilityValidator = new PatchAttributeMutabilityValidator(false);
    if (!value.isArray()) {
      mutabilityValidator.validate(attribute);
    }
  }

  private void validateSchemaAttributes(final Attribute schemaAttribute, final PatchOperation operation) {
    JsonNode value = operation.getValue();

    Validator<Attribute> mutabilityValidator = new PatchAttributeMutabilityValidator(false);
    Iterator<Map.Entry<String, JsonNode>> fieldsIterator = value.fields();

    while (fieldsIterator.hasNext()) {
      String subAttrName = fieldsIterator.next().getKey();

      // @formatter:off
      Attribute subAttribute = schemaAttribute.getSubAttributes().stream()
          .filter(attr -> subAttrName.equalsIgnoreCase(attr.getName()))
          .findAny()
          .orElseThrow(() -> new PatchValidationException(SCIMException.Type.INVALID_PATH, String.format("Value attribute with name %s does not exist", subAttrName)));
      // @formatter:on

      mutabilityValidator.validate(subAttribute);
    }
  }

}