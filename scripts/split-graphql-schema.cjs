const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const schemaPath = path.join(root, "src", "main", "resources", "graphql", "schema.graphqls");
const modulesRoot = path.join(root, "src", "main", "resources", "graphql", "modules");
const files = [
  ["00-shell.graphqls", 0, 43],
  ["10-school-quiz.graphqls", 43, 217],
  ["20-elearning.graphqls", 217, 308],
  ["30-lms.graphqls", 308, 892],
  ["40-mutation-social.graphqls", 892, 934],
  ["50-calendar.graphqls", 934, null],
];

const lines = fs.readFileSync(schemaPath, "utf8").replace(/^\uFEFF/, "").replace(/\r\n/g, "\n").split("\n");
fs.mkdirSync(modulesRoot, { recursive: true });
for (const [file, start, end] of files) {
  fs.writeFileSync(path.join(modulesRoot, file), lines.slice(start, end ?? lines.length).join("\n"), "utf8");
}
console.log(`Split ${files.length} GraphQL SDL modules.`);
