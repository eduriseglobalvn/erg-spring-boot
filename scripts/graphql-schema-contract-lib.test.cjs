const { describe, test } = require("node:test");
const assert = require("node:assert/strict");

const { canonicalSchema, checksumFile, sha256 } = require("./graphql-schema-contract-lib.cjs");

describe("GraphQL schema contract normalization", () => {
  test("normalizes BOM and Windows line endings without changing schema content", () => {
    assert.equal(canonicalSchema("\uFEFFtype Query {\r\n  ping: String\r\n}\r\n"), "type Query {\n  ping: String\n}\n");
  });

  test("writes a stable newline-terminated checksum", () => {
    const schema = "type Query { ping: String }\n";
    assert.equal(checksumFile(schema), `${sha256(schema)}\n`);
  });
});
