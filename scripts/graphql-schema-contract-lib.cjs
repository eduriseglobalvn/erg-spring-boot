const crypto = require("node:crypto");

function canonicalSchema(source) {
  if (typeof source !== "string" || source.trim() === "") {
    throw new Error("GraphQL schema must be a non-empty string");
  }
  return source.replace(/^\uFEFF/, "").replace(/\r\n/g, "\n");
}

function sha256(source) {
  return crypto.createHash("sha256").update(canonicalSchema(source), "utf8").digest("hex");
}

function checksumFile(source) {
  return `${sha256(source)}\n`;
}

module.exports = { canonicalSchema, sha256, checksumFile };
