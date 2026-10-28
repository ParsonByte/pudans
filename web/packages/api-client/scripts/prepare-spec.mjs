import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = resolve(packageRoot, "../../..");
const input = resolve(repoRoot, "api-docs/openapi/v3_0/aggregated.json");
const output = resolve(packageRoot, ".openapi/aggregated.json");

const spec = JSON.parse(readFileSync(input, "utf8"));

for (const schema of Object.values(spec.components?.schemas ?? {})) {
  if (schema && typeof schema === "object" && schema.properties && !schema.type) {
    schema.type = "object";
  }
}

// The CreateUser operation uses a oneOf request body whose first member is the
// CreateUserRequest schema. The generator would otherwise create two models
// with the same name and produce a circular type alias.
const createUserSchema = spec.components?.schemas?.CreateUserRequest;
if (createUserSchema) {
  spec.components.schemas.CreateUserRequestPayload = createUserSchema;
  delete spec.components.schemas.CreateUserRequest;
  const renameRefs = (value) => {
    if (Array.isArray(value)) {
      value.forEach(renameRefs);
      return;
    }
    if (!value || typeof value !== "object") return;
    for (const [key, child] of Object.entries(value)) {
      if (key === "$ref" && child === "#/components/schemas/CreateUserRequest") {
        value[key] = "#/components/schemas/CreateUserRequestPayload";
      } else {
        renameRefs(child);
      }
    }
  };
  renameRefs(spec);
}

for (const [path, item] of Object.entries(spec.paths ?? {})) {
  const placeholders = [...path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]);
  for (const [method, operation] of Object.entries(item)) {
    if (!["get", "post", "put", "patch", "delete", "head", "options"].includes(method)) {
      continue;
    }
    const parameters = Array.isArray(operation.parameters) ? operation.parameters : [];
    const seen = new Set();
    const deduped = parameters.filter((parameter) => {
      const key = `${parameter.in}:${parameter.name}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
    for (const name of placeholders) {
      const key = `path:${name}`;
      if (!seen.has(key)) {
        seen.add(key);
        deduped.push({
          name,
          in: "path",
          required: true,
          schema: { type: "string" },
        });
      }
    }
    operation.parameters = deduped;
  }
}

mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, JSON.stringify(spec, null, 2) + "\n");
