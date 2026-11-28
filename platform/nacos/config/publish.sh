#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NACOS_ADDR="${NACOS_ADDR:-http://127.0.0.1:8848}"
NACOS_ADDR="${NACOS_ADDR%/}"
case "${NACOS_ADDR}" in
    http://*|https://*) ;;
    *) NACOS_ADDR="http://${NACOS_ADDR}" ;;
esac
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
CONTEXT_PATH="${NACOS_CONTEXT_PATH:-/nacos}"
CONTEXT_PATH="/${CONTEXT_PATH#/}"
CONTEXT_PATH="${CONTEXT_PATH%/}"

shopt -s nullglob
files=("${SCRIPT_DIR}"/*.yaml "${SCRIPT_DIR}"/*.json)
if [ "${#files[@]}" -eq 0 ]; then
    echo "No Nacos config files found in ${SCRIPT_DIR}" >&2
    exit 1
fi

for file in "${files[@]}"; do
    data_id="$(basename "${file}")"
    case "${file}" in
        *.json) data_type="json" ;;
        *) data_type="yaml" ;;
    esac
    echo "Publishing ${data_id} to ${GROUP}"
    response="$(curl --fail --silent --show-error \
        -X POST "${NACOS_ADDR}${CONTEXT_PATH}/v1/cs/configs" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=${GROUP}" \
        --data-urlencode "type=${data_type}" \
        --data-urlencode "content@${file}")"
    if [ "${response}" != "true" ]; then
        echo "Failed to publish ${data_id}: ${response}" >&2
        exit 1
    fi
done

echo "Published ${#files[@]} Nacos config(s) to ${GROUP} at ${NACOS_ADDR}"