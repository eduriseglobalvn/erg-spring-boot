const { describe, test } = require("node:test");
const assert = require("node:assert/strict");

const { normalizeOpenApi, stableJson, sha256 } = require("./openapi-contract-lib.cjs");

describe("OpenAPI contract normalization", () => {
  test("removes environment servers and sorts object keys recursively", () => {
    const normalized = normalizeOpenApi({
      servers: [{ url: "http://localhost:1606" }],
      paths: { "/z": { get: { responses: {} } }, "/a": { get: { responses: {} } } },
      openapi: "3.1.0",
      info: { version: "v1", title: "ERG" },
    });

    assert.deepEqual(Object.keys(normalized), ["info", "openapi", "paths", "servers"]);
    assert.deepEqual(normalized.servers, [{ description: "Contract-relative server", url: "/" }]);
    assert.deepEqual(Object.keys(normalized.paths), ["/a", "/z"]);
  });

  test("produces identical JSON and checksum for semantically identical key order", () => {
    const left = stableJson({ b: 2, a: { d: 4, c: 3 } });
    const right = stableJson({ a: { c: 3, d: 4 }, b: 2 });

    assert.equal(left, right);
    assert.equal(sha256(left), sha256(right));
  });
});
