const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const modulesRoot = path.join(root, "src", "main", "resources", "graphql", "modules");
const schemaPath = path.join(root, "src", "main", "resources", "graphql", "schema.graphqls");

// Keep the order explicit: GraphQL SDL is assembled as one canonical contract for Spring GraphQL.
const MODULES = [
  ["00-shell.graphqls", "platform shell and namespace roots"],
  ["10-school-quiz.graphqls", "school and quiz read models"],
  ["20-elearning.graphqls", "learner dashboard and resource library"],
  ["30-lms.graphqls", "LMS/LCMS workspaces and shared DTOs"],
  ["35-learning-outcomes.graphqls", "learning paths, competencies and certificates"],
  ["40-mutation-social.graphqls", "mutation and learner collaboration inputs"],
  ["45-school-operations-mutations.graphqls", "school operations command mutations"],
  ["50-calendar.graphqls", "calendar bounded context"],
];

function readModules() {
  return MODULES.map(([file]) => {
    const fullPath = path.join(modulesRoot, file);
    if (!fs.existsSync(fullPath)) throw new Error(`GraphQL module is missing: ${file}`);
    return fs.readFileSync(fullPath, "utf8").replace(/^\uFEFF/, "").replace(/\r\n/g, "\n");
  });
}

function assemble() {
  return `${readModules().join("\n").replace(/\n+$/, "")}\n`;
}

function verify() {
  const expected = assemble();
  const actual = fs.readFileSync(schemaPath, "utf8").replace(/^\uFEFF/, "").replace(/\r\n/g, "\n");
  if (actual !== expected) {
    throw new Error("Canonical GraphQL schema differs from the ordered module sources; run node scripts/assemble-graphql-schema.cjs --write");
  }
  return expected;
}

if (require.main === module) {
  try {
    const write = process.argv.includes("--write");
    if (write) fs.writeFileSync(schemaPath, assemble(), "utf8");
    else verify();
    console.log(`GraphQL module assembly is synchronized (${MODULES.length} modules).`);
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}

module.exports = { MODULES, assemble, verify };
