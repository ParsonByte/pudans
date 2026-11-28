import { useEffect, useRef, useState } from "react";

import { CloseOutlined } from "@ant-design/icons";
import { Button, Input, type InputRef } from "antd";
import gsap from "gsap";
import { Search } from "lucide-react";

import { LoginGate } from "./header-login-gate";

type SearchBoxProps = {
  discoveries?: string[];
  history?: string[];
  locked?: boolean;
  loginHref?: string;
  onExpandedChange?: (expanded: boolean) => void;
  onSearch?: (keyword: string) => void;
  placeholder?: string;
};

export function SearchBox({
  discoveries = [],
  history = [],
  locked = false,
  loginHref,
  onExpandedChange,
  onSearch,
  placeholder = "搜索",
}: SearchBoxProps) {
  const [value, setValue] = useState("");
  const [expanded, setExpanded] = useState(false);
  const [panelVisible, setPanelVisible] = useState(false);
  const inputRef = useRef<InputRef>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const closedWidthRef = useRef<number | null>(null);

  useEffect(() => {
    const root = rootRef.current;

    return () => {
      if (root) {
        gsap.killTweensOf(root);
      }
    };
  }, []);

  const expand = () => {
    const root = rootRef.current;

    if (!root || expanded) {
      return;
    }

    if (closedWidthRef.current === null) {
      closedWidthRef.current = root.getBoundingClientRect().width;
    }

    const rootRect = root.getBoundingClientRect();
    const brandRect = root
      .closest(".pudans-app-header")
      ?.querySelector(".pudans-app-header__brand")
      ?.getBoundingClientRect();
    const targetWidth = brandRect
      ? Math.max(360, rootRect.right - brandRect.right - 16)
      : Math.max(360, Math.min(1000, window.innerWidth - 470));

    setExpanded(true);
    setPanelVisible(false);
    onExpandedChange?.(true);
    gsap.to(root, {
      width: targetWidth,
      duration: 0.48,
      ease: "expo.out",
      onComplete: () => {
        setPanelVisible(true);
        if (!locked) {
          inputRef.current?.focus();
        }
      },
    });
  };

  const collapse = () => {
    const root = rootRef.current;

    if (!root || !expanded) {
      return;
    }

    setPanelVisible(false);
    inputRef.current?.blur();

    gsap.to(root, {
      width: closedWidthRef.current ?? 220,
      duration: 0.36,
      ease: "expo.inOut",
      onComplete: () => {
        setExpanded(false);
        onExpandedChange?.(false);
      },
    });
  };

  useEffect(() => {
    if (!expanded) {
      return;
    }

    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        collapse();
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
    };
  }, [expanded]);

  const submit = () => {
    onSearch?.(value.trim());
  };

  const selectKeyword = (keyword: string) => {
    setValue(keyword);
    inputRef.current?.focus();
  };

  return (
    <div
      className={[
        "pudans-app-header__search-box",
        expanded ? "pudans-app-header__search-box--expanded" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      ref={rootRef}
    >
      {expanded ? (
        <>
          <Input
            allowClear={!locked}
            aria-label={placeholder}
            className="pudans-app-header__search"
            onChange={(event) => {
              if (!locked) {
                setValue(event.target.value);
              }
            }}
            onKeyDown={(event) => {
              if (event.key === "Escape") {
                event.preventDefault();
                collapse();
              }
            }}
            onPressEnter={locked ? undefined : submit}
            placeholder={locked ? "登录后搜索" : placeholder}
            prefix={<Search size={16} />}
            readOnly={locked}
            ref={inputRef}
            suffix={
              <button
                aria-label="关闭搜索"
                className="pudans-app-header__search-close"
                onClick={collapse}
                type="button"
              >
                <CloseOutlined />
              </button>
            }
            value={locked ? "" : value}
          />
          {panelVisible && (locked || history.length > 0 || discoveries.length > 0) ? (
            <div className="pudans-app-header__search-panel">
              {locked ? (
                <LoginGate
                  compact
                  description="登录后可搜索产品、文档与服务。"
                  items={[]}
                  loginHref={loginHref}
                  title="登录后使用搜索"
                />
              ) : null}
              {!locked && history.length > 0 ? (
                <section className="pudans-app-header__search-section">
                  <h3 className="pudans-app-header__search-section-title">历史搜索</h3>
                  <div className="pudans-app-header__search-tags">
                    {history.map((keyword) => (
                      <Button key={keyword} onClick={() => selectKeyword(keyword)} size="small">
                        {keyword}
                      </Button>
                    ))}
                  </div>
                </section>
              ) : null}
              {!locked && discoveries.length > 0 ? (
                <section className="pudans-app-header__search-section">
                  <h3 className="pudans-app-header__search-section-title">搜索发现</h3>
                  <div className="pudans-app-header__search-tags">
                    {discoveries.map((keyword) => (
                      <Button key={keyword} onClick={() => selectKeyword(keyword)} size="small">
                        {keyword}
                      </Button>
                    ))}
                  </div>
                </section>
              ) : null}
            </div>
          ) : null}
        </>
      ) : (
        <button className="pudans-app-header__search-trigger" onClick={expand} type="button">
          <Search size={16} />
          <span>{placeholder}</span>
        </button>
      )}
    </div>
  );
}
