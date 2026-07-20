const { test } = require("node:test");
const assert = require("node:assert/strict");

const { buildOasdiffInvocation, parseArgs } = require("./check-openapi-breaking.cjs");

test("requires explicit base and revision contracts", () => {
  assert.throws(() => parseArgs([]), /--base and --revision are required/);
});

test("builds a pinned Docker oasdiff breaking command", () => {
  const invocation = buildOasdiffInvocation(
    "C:/repo/base.json",
    "C:/repo/revision.json",
  );

  assert.equal(invocation.command, "docker");
  assert.ok(invocation.args.includes("tufin/oasdiff:v1.15.1"));
  assert.ok(invocation.args.includes("breaking"));
  assert.ok(invocation.args.includes("--fail-on"));
  assert.ok(invocation.args.includes("WARN"));
  assert.ok(invocation.args.includes("/work/base.json"));
  assert.ok(invocation.args.includes("/work/revision.json"));
});
