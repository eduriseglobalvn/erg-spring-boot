const fs = require("node:fs");
const path = require("node:path");

const { normalizeOpenApi, sha256, stableJson } = require("./openapi-contract-lib.cjs");

const root = path.resolve(__dirname, "..");
const outputDirectory = path.join(root, "contracts", "openapi");
const contractPath = path.join(outputDirectory, "erg-openapi.json");
const checksumPath = path.join(outputDirectory, "erg-openapi.sha256");

function parseArgs(args) {
  const options = { check: false };

  for (let index = 0; index < args.length; index += 1) {
    const name = args[index];
    if (name === "--check") {
      options.check = true;
      continue;
    }
    if (name === "--url" || name === "--file") {
      const value = args[index + 1];
      if (!value || value.startsWith("--")) throw new Error(`${name} requires a value`);
      options[name.slice(2)] = value;
      index += 1;
      continue;
    }
    throw new Error(`Unknown argument: ${name}`);
  }

  if (Boolean(options.url) === Boolean(options.file)) {
    throw new Error("Provide exactly one of --url or --file");
  }
  return options;
}

async function readDocument(options) {
  if (options.file) {
    return JSON.parse(fs.readFileSync(path.resolve(options.file), "utf8").replace(/^\uFEFF/, ""));
  }

  const response = await fetch(options.url, { headers: { accept: "application/json" } });
  if (!response.ok) throw new Error(`Cannot fetch OpenAPI document: HTTP ${response.status}`);
  return response.json();
}

function verifyFile(file, expected, label) {
  if (!fs.existsSync(file)) throw new Error(`${label} is missing: ${path.relative(root, file)}`);
  const current = fs.readFileSync(file, "utf8").replace(/\r\n/g, "\n");
  if (current !== expected) throw new Error(`${label} is stale: ${path.relative(root, file)}`);
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const document = normalizeOpenApi(await readDocument(options));
  const json = stableJson(document);
  const checksum = `${sha256(json)}\n`;

  if (options.check) {
    verifyFile(contractPath, json, "OpenAPI contract");
    verifyFile(checksumPath, checksum, "OpenAPI checksum");
    console.log(`OpenAPI contract is synchronized (${checksum.trim()})`);
    return;
  }

  fs.mkdirSync(outputDirectory, { recursive: true });
  fs.writeFileSync(contractPath, json);
  fs.writeFileSync(checksumPath, checksum);
  console.log(`Wrote ${path.relative(root, contractPath)} (${checksum.trim()})`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
