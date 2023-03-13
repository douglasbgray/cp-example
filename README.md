# Classpath Example

### Overview

This project is used to demonstrate an issue found in the Swagger parser library in 
regards to reference resolution.

The specific issue is that if a project is configured as follows ...

```text
src/main/resources/
    ├── openapi.yaml
    ├── types/
        ├── local-types.yaml
```

... and a model in `openapi.yaml` has a reference to a model in `local-types.yaml`, and in turn,
that model is an alias to a model found in a classpath resource, then the reference is not resolved. 

### Example

**openapi.yaml** (root contract)
```yaml
LocalModel:
  type: object
  properties:
    sharedModelField:
      $ref: 'types/local-types.yaml#/components/schemas/TypesModel'
```

**local-types.yaml** (exists in `types` folder sibling to root contract)
```yaml
TypesModel:
  $ref: '/shared-types.yaml#/components/schemas/SharedModel'
```

**shared-types.yaml** (this is in shared-types.yaml resource in the classpath, not in file system)
```yaml
SharedModel:
  type: object
  properties:
    name:
      type: string
```

The parser attempts to find resolve the reference to `/shared-types.yaml` by prepending
the path of the local types directory, so it looks for `types/shared-types.yaml` and 
does not find it. This leave the reference unresolved.

Since the parser is unable to resolve the references, this affects downstream code 
generation, which creates invalid types for some properties.

### Test Case to Reproduce

This project contains code that demonstrates the issue. There are 2 modules:

1. TypeLibrary is used to generate a jar file with a shared types contract that is 
   referred to in the example module. For the purposes of a self-contained example,
   the TypeLibrary is part of this project. Typically, there is a stand-alone
   project containing the shared types and the consumer has access only via a jar file.
2. Example is the module with the main contract and local types file. It also
    contains a unit test exemplifying the issue. 

To reproduce the failure:

1. Run `mvn clean install` and the test will fail.
2. Update the pom and change property `swagger.parser.version` to `2.0.23` and rerun the command, tests now pass. 

### When and Where

The issue is in the [ExternalRefProcessor][RefProcessor] class and the issue was introduced in version
`2.0.24`, where logic was added to add the parent to the path when resolving a reference.

This is the code, as found in version `2.0.24` of the `processRefToExternalSchema` method. It has changed
considerably in later versions, but the crux of the issue still exists in the
most recent version, `2.1.12`

```
String parent = file.substring(0, file.lastIndexOf(File.separatorChar));
if (!parent.isEmpty()) {
     schemaFullRef = Paths.get(parent, schemaFullRef).normalize().toString();
}
```

### Proposed Fix

The easiest fix is to not prepend the path when the reference starts with a leading slash.
This is indicative of an absolute path reference, not a relative path reference, so the
current directory should not be prepended. Then the reference is found in the classpath
resource and properly resolved.

In the fix below, this logic is added as a second clause to the conditional.

```
String parent = file.substring(0, file.lastIndexOf(File.separatorChar));
if (!parent.isEmpty() && !schemaFullRef.startsWith("/")) {
     schemaFullRef = Paths.get(parent, schemaFullRef).normalize().toString();
}
```

<!-- Links -->
[RefProcessor]: https://github.com/swagger-api/swagger-parser/blob/v2.0.24/modules/swagger-parser-v3/src/main/java/io/swagger/v3/parser/processors/ExternalRefProcessor.java#L113-L116
