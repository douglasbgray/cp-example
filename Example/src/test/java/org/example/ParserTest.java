package org.example;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

/**
 * This test class demonstrates an issue with the Swagger parser's external reference
 * resolver, in which it tries to prepend the path to a reference that should be resolved
 * via the classpath.
 */
public class ParserTest {

  private final static String contractPath = "src/main/resources/api/openapi.yaml";

  /**
   * The structure of the contract is
   * - src/main/resources/api/openapi.yaml has model LocalModel
   * - LocalModel has a property with a $ref to TypesModel from a subdirectory "types/local-types.yaml"
   * - TypesModel is an alias (just a $ref) to a SharedModel from a classpath file "/shared-types.yaml"
   *
   * The expectation is that all references should be resolved to local references within the contract.
   *
   * This test passes using swagger.parser.version <= 2.0.23, fails with later versions.
   */
  @Test
  public void testParser_unexpectedPathPrepended() {

    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    options.setFlatten(true);

    OpenAPIParser parser = new OpenAPIParser();
    SwaggerParseResult result = parser.readLocation(contractPath, new ArrayList<>(), options);

    // There shouldn't be any messages.
    System.out.println("--- Messages: ");
    result.getMessages().forEach(System.out::println);
    Assert.assertTrue(result.getMessages().isEmpty());

    // The $ref should ALL be resolved to local references in the contract and 3 models created.
    OpenAPI openApi = result.getOpenAPI();
    Schema<?> localModel = openApi.getComponents().getSchemas().get("LocalModel");
    Schema<?> typesModel = openApi.getComponents().getSchemas().get("TypesModel");
    Schema<?> sharedModel = openApi.getComponents().getSchemas().get("SharedModel");

    Assert.assertEquals("#/components/schemas/TypesModel", localModel.getProperties().get("sharedModelField").get$ref());
    Assert.assertEquals("#/components/schemas/SharedModel", typesModel.get$ref());
    Assert.assertNotNull(sharedModel);
  }

}
