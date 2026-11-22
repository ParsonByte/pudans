import { readFileSync, writeFileSync } from "node:fs";

const [input, output] = process.argv.slice(2);

if (!input || !output) {
    throw new Error("Usage: normalize-openapi.mjs <input> <output>");
}

const document = JSON.parse(readFileSync(input, "utf8"));
const methods = new Set(["delete", "get", "head", "options", "patch", "post", "put"]);

for (const [path, pathItem] of Object.entries(document.paths ?? {})) {
    const pathParameters = pathItem.parameters ?? [];

    for (const [method, operation] of Object.entries(pathItem)) {
        if (!methods.has(method)) {
            continue;
        }

        const parameters = [];
        const seen = new Set();

        for (const parameter of [...pathParameters, ...(operation.parameters ?? [])]) {
            const key = `${parameter.in}:${parameter.name}`;

            if (seen.has(key)) {
                continue;
            }

            seen.add(key);
            parameters.push(parameter);
        }

        for (const match of path.matchAll(/\{([^}]+)\}/g)) {
            const name = match[1];

            if (!seen.has(`path:${name}`)) {
                parameters.push({ in: "path", name, required: true, schema: { type: "string" } });
                seen.add(`path:${name}`);
            }
        }

        if (parameters.length > 0) {
            operation.parameters = parameters;
        } else {
            delete operation.parameters;
        }

        for (const media of Object.values(operation.requestBody?.content ?? {})) {
            const schema = media?.schema;

            if (Array.isArray(schema?.oneOf) && schema.oneOf.every((item) => typeof item?.$ref === "string")) {
                media.schema = { $ref: schema.oneOf[0].$ref };
            }
        }
    }

    if (pathParameters.length > 0) {
        delete pathItem.parameters;
    }
}

writeFileSync(output, JSON.stringify(document));
