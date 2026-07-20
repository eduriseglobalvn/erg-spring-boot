const crypto = require("node:crypto");

function sortValue(value) {
  if (Array.isArray(value)) return value.map(sortValue);
  if (!value || typeof value !== "object") return value;

  return Object.fromEntries(
    Object.keys(value)
      .sort()
      .map((key) => [key, sortValue(value[key])]),
  );
}

function normalizeOpenApi(document) {
  if (!document || typeof document !== "object" || !document.openapi || !document.paths) {
    throw new Error("Input is not an OpenAPI document");
  }

  return sortValue({
    ...document,
    servers: [{ url: "/", description: "Contract-relative server" }],
  });
}

function stableJson(value) {
  return `${JSON.stringify(sortValue(value), null, 2)}\n`;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

module.exports = { normalizeOpenApi, sha256, stableJson };
