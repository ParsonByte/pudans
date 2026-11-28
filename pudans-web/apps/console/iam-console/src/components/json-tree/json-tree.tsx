"use client";

import { useState } from "react";

import { CaretDownOutlined, CaretRightOutlined, CopyOutlined } from "@ant-design/icons";
import { Button, notification } from "antd";

import "./json-tree.modules.less";

type JsonTreeProps = {
  data: unknown;
};

type JsonNodeProps = {
  depth: number;
  isRoot?: boolean;
  label?: string;
  onCopy?: () => void;
  value: unknown;
};

function typeLabel(value: unknown) {
  if (typeof value === "number") {
    return Number.isInteger(value) ? "int" : "number";
  }
  if (typeof value === "boolean") {
    return "bool";
  }
  if (typeof value === "string") {
    return "string";
  }
  return "null";
}

function PrimitiveValue({ value }: { value: unknown }) {
  if (value === null) {
    return <span className="json-tree__null">null</span>;
  }
  if (typeof value === "string") {
    return (
      <>
        <span className="json-tree__type">string</span>
        <span className="json-tree__string">&quot;{value}&quot;</span>
      </>
    );
  }
  if (typeof value === "number") {
    return (
      <>
        <span className="json-tree__type">{typeLabel(value)}</span>
        <span className="json-tree__number">{String(value)}</span>
      </>
    );
  }
  if (typeof value === "boolean") {
    return (
      <>
        <span className="json-tree__type">bool</span>
        <span className="json-tree__boolean">{String(value)}</span>
      </>
    );
  }
  return <span className="json-tree__null">{String(value)}</span>;
}

function JsonNode({ depth, isRoot = false, label, onCopy, value }: JsonNodeProps) {
  const isCollection = value !== null && typeof value === "object";
  const entries = isCollection ? Object.entries(value as Record<string, unknown>) : [];
  const isArray = Array.isArray(value);
  const [expanded, setExpanded] = useState(depth < 2);
  const openBracket = isArray ? "[" : "{";
  const closeBracket = isArray ? "]" : "}";

  if (!isCollection) {
    return (
      <div className="json-tree__row">
        {label ? <span className="json-tree__key">&quot;{label}&quot;</span> : null}
        <span className="json-tree__colon">:</span>
        <PrimitiveValue value={value} />
      </div>
    );
  }

  return (
    <div className="json-tree__node">
      <div className="json-tree__row">
        <button
          aria-label={expanded ? "收起" : "展开"}
          className="json-tree__toggle"
          onClick={() => setExpanded((current) => !current)}
          type="button"
        >
          {expanded ? <CaretDownOutlined /> : <CaretRightOutlined />}
        </button>
        {label ? <span className="json-tree__key">&quot;{label}&quot;</span> : null}
        <span className="json-tree__colon">:</span>
        <span className="json-tree__bracket">{openBracket}</span>
        <span className="json-tree__count">{entries.length} items</span>
        {!expanded ? <span className="json-tree__bracket">{closeBracket}</span> : null}
        {isRoot && onCopy ? (
          <Button
            className="json-tree__copy"
            icon={<CopyOutlined />}
            onClick={onCopy}
            size="small"
            type="text"
          />
        ) : null}
      </div>
      {expanded ? (
        <div className="json-tree__children">
          {entries.map(([entryKey, entryValue]) => (
            <JsonNode
              depth={depth + 1}
              key={entryKey}
              label={isArray ? undefined : entryKey}
              value={entryValue}
            />
          ))}
          <div className="json-tree__row">
            <span className="json-tree__bracket">{closeBracket}</span>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function JsonTree({ data }: JsonTreeProps) {
  const [notificationApi, notificationHolder] = notification.useNotification();

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(JSON.stringify(data, null, 2));
      notificationApi.success({ key: "json-tree-copy", title: "已复制事件数据" });
    } catch {
      notificationApi.error({ key: "json-tree-copy", title: "复制失败" });
    }
  };

  return (
    <div className="json-tree">
      {notificationHolder}
      <JsonNode depth={0} isRoot onCopy={copy} value={data} />
    </div>
  );
}