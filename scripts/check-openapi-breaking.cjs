const path = require("node:path");
const { spawnSync } = require("node:child_process");

const OASDIFF_IMAGE = "tufin/oasdiff:v1.15.1";

function parseArgs(args) {
  const options = {};

  for (let index = 0; index < args.length; index += 1) {
    const name = args[index];
    if (name === "--base" || name === "--revision") {
      const value = args[index + 1];
      if (!value || value.startsWith("--")) {
        throw new Error(`${name} requires a file path`);
      }
      options[name.slice(2)] = value;
      index += 1;
      continue;
    }
    throw new Error(`Unknown argument: ${name}`);
  }

  if (!options.base || !options.revision) {
    throw new Error("--base and --revision are required");
  }

  return options;
}

function commonDirectory(left, right) {
  const leftDirectory = path.dirname(path.resolve(left));
  const rightDirectory = path.dirname(path.resolve(right));
  const leftRoot = path.parse(leftDirectory).root;
  const rightRoot = path.parse(rightDirectory).root;

  if (leftRoot.toLowerCase() !== rightRoot.toLowerCase()) {
    throw new Error("Base and revision contracts must be on the same filesystem root");
  }

  const leftParts = leftDirectory.slice(leftRoot.length).split(path.sep).filter(Boolean);
  const rightParts = rightDirectory.slice(rightRoot.length).split(path.sep).filter(Boolean);
  const shared = [];

  for (let index = 0; index < Math.min(leftParts.length, rightParts.length); index += 1) {
    if (leftParts[index].toLowerCase() !== rightParts[index].toLowerCase()) break;
    shared.push(leftParts[index]);
  }

  return path.join(leftRoot, ...shared);
}

function dockerPath(root, file) {
  return `/work/${path.relative(root, path.resolve(file)).split(path.sep).join("/")}`;
}

function buildOasdiffInvocation(base, revision) {
  const mountRoot = commonDirectory(base, revision);
  return {
    command: "docker",
    args: [
      "run",
      "--rm",
      "-v",
      `${mountRoot}:/work:ro`,
      OASDIFF_IMAGE,
      "breaking",
      "--fail-on",
      "WARN",
      "--format",
      "text",
      dockerPath(mountRoot, base),
      dockerPath(mountRoot, revision),
    ],
  };
}

function main() {
  const { base, revision } = parseArgs(process.argv.slice(2));
  const invocation = buildOasdiffInvocation(base, revision);
  const result = spawnSync(invocation.command, invocation.args, { stdio: "inherit" });

  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
}

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}

module.exports = { buildOasdiffInvocation, parseArgs };
