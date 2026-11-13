import { useMemo, useState, type ReactNode } from "react";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import { cn } from "@pudans/shared";
import styles from "./tree-view.module.css";

export interface TreeNode {
  id: string;
  label: ReactNode;
  icon?: ReactNode;
  children?: readonly TreeNode[];
  disabled?: boolean;
}

export interface TreeViewProps {
  items: readonly TreeNode[];
  selectedId?: string | null;
  defaultSelectedId?: string | null;
  expandedIds?: readonly string[];
  defaultExpandedIds?: readonly string[];
  onSelect?: (node: TreeNode | null) => void;
  onExpandedChange?: (ids: string[]) => void;
  className?: string;
  ariaLabel?: string;
}

function flattenNodes(nodes: readonly TreeNode[]): Map<string, TreeNode> {
  const map = new Map<string, TreeNode>();
  const visit = (items: readonly TreeNode[]): void => {
    for (const item of items) {
      map.set(item.id, item);
      if (item.children) {
        visit(item.children);
      }
    }
  };
  visit(nodes);
  return map;
}

function renderTreeItems(nodes: readonly TreeNode[]): ReactNode {
  return nodes.map((node) => (
    <TreeItem
      key={node.id}
      itemId={node.id}
      disabled={node.disabled}
      label={
        <span className={styles.label}>
          {node.icon ? <span className={styles.icon}>{node.icon}</span> : null}
          <span>{node.label}</span>
        </span>
      }
    >
      {node.children ? renderTreeItems(node.children) : null}
    </TreeItem>
  ));
}

/** MUI X tree view with icon slots and controlled/uncontrolled selection. */
export function TreeView({
  items,
  selectedId,
  defaultSelectedId = null,
  expandedIds,
  defaultExpandedIds = [],
  onSelect,
  onExpandedChange,
  className,
  ariaLabel = "Tree",
}: TreeViewProps) {
  const [internalSelected, setInternalSelected] = useState<string | null>(defaultSelectedId);
  const [internalExpanded, setInternalExpanded] = useState<string[]>([...defaultExpandedIds]);
  const nodeMap = useMemo(() => flattenNodes(items), [items]);
  const activeSelected = selectedId === undefined ? internalSelected : selectedId;
  const activeExpanded = expandedIds === undefined ? internalExpanded : expandedIds;

  return (
    <SimpleTreeView
      className={cn(styles.root, className)}
      aria-label={ariaLabel}
      selectedItems={activeSelected}
      expandedItems={activeExpanded}
      onSelectedItemsChange={(_event, itemId) => {
        const next = itemId ?? null;
        if (selectedId === undefined) {
          setInternalSelected(next);
        }
        onSelect?.(next ? (nodeMap.get(next) ?? null) : null);
      }}
      onExpandedItemsChange={(_event, ids) => {
        if (expandedIds === undefined) {
          setInternalExpanded(ids);
        }
        onExpandedChange?.(ids);
      }}
    >
      {renderTreeItems(items)}
    </SimpleTreeView>
  );
}
