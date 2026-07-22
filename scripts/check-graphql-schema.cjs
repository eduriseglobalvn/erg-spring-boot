const fs = require("node:fs");
const path = require("node:path");

const { canonicalSchema, checksumFile } = require("./graphql-schema-contract-lib.cjs");

const root = path.resolve(__dirname, "..");
const schemaPath = path.join(root, "src", "main", "resources", "graphql", "schema.graphqls");
const checksumPath = path.join(root, "contracts", "graphql", "schema.graphqls.sha256");

function verify() {
  if (!fs.existsSync(schemaPath)) throw new Error(`GraphQL schema is missing: ${schemaPath}`);
  if (!fs.existsSync(checksumPath)) throw new Error(`GraphQL schema checksum is missing: ${checksumPath}`);
  const schema = canonicalSchema(fs.readFileSync(schemaPath, "utf8"));
  const expected = checksumFile(schema);
  const actual = fs.readFileSync(checksumPath, "utf8").replace(/\r\n/g, "\n");
  if (actual !== expected) throw new Error(`GraphQL schema checksum is stale: ${path.relative(root, checksumPath)}`);
  return expected.trim();
}

if (require.main === module) {
  try {
    console.log(`GraphQL schema is synchronized (${verify()})`);
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}

module.exports = { verify };
