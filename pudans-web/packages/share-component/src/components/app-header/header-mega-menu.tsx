"use client";

import { useLayoutEffect, useRef } from "react";

import gsap from "gsap";

import { LoginGate } from "./header-login-gate";
import type { AppHeaderMenu } from "./header-types";

type MegaMenuProps = {
  activeKey: string;
  locked?: boolean;
  loginHref?: string;
  menus: AppHeaderMenu[];
};

export function MegaMenu({
  activeKey,
  locked = false,
  loginHref,
  menus,
}: MegaMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const itemsRef = useRef<HTMLDivElement>(null);
  const panelRefs = useRef(new Map<string, HTMLDivElement>());
  const previousKeyRef = useRef<string | null>(null);
  const openedRef = useRef(false);
  const gated = locked && activeKey !== "about";

  useLayoutEffect(() => {
    const items = itemsRef.current;
    const menu = menuRef.current;
    const activePanel = panelRefs.current.get(activeKey);
    if (!items || !menu || !activePanel) {
      return;
    }
    const targetHeight = gated ? Math.max(activePanel.offsetHeight, 360)
      : activePanel.offsetHeight;
    const previousKey = previousKeyRef.current;
    previousKeyRef.current = activeKey;

    const previousPanel = previousKey ? panelRefs.current.get(previousKey) : null;
    const previousGated = previousKey ? locked && previousKey !== "about" : false;
    gsap.killTweensOf(items);
    gsap.killTweensOf(menu);
    menus.forEach((item) => {
      if (item.key === activeKey || item.key === previousKey) {
        return;
      }
      const element = panelRefs.current.get(item.key);
      if (element) {
        gsap.killTweensOf(element);
        gsap.set(element, { opacity: 0 });
      }
    });

    if (!openedRef.current) {
      openedRef.current = true;
      gsap.fromTo(items, { height: 0 }, {
        height: targetHeight,
        duration: 0.2,
        ease: "power2.inOut",
      });
      gsap.fromTo(menu, { opacity: 0 }, {
        opacity: 1,
        duration: 0.15,
        ease: "power1.out",
      });
    } else {
      gsap.to(items, {
        height: targetHeight,
        duration: 0.2,
        ease: "power2.inOut",
      });
      gsap.set(menu, { opacity: 1 });
      if (previousPanel && previousPanel !== activePanel) {
        gsap.killTweensOf(previousPanel);
        if (previousGated && !gated) {
          gsap.set(previousPanel, { opacity: 0 });
        } else {
          gsap.to(previousPanel, { opacity: 0, duration: 0.16, ease: "power1.inOut" });
        }
      }
    }
    gsap.killTweensOf(activePanel);
    gsap.to(activePanel, { opacity: 1, duration: 0.18, ease: "power1.inOut" });

    return () => {
      gsap.killTweensOf(items);
      gsap.killTweensOf(menu);
    };
  }, [activeKey, gated, menus]);

  return (
    <div
      aria-label="主导航菜单"
      className="pudans-app-header__mega"
      ref={menuRef}
      role="region"
    >
      <div className="pudans-app-header__mega-items" ref={itemsRef}>
        {menus.map((menu) => {
          const active = menu.key === activeKey;
          return (
            <div
              aria-hidden={!active}
              className={[
                "pudans-app-header__mega-panel",
                active ? "pudans-app-header__mega-panel--active" : "",
              ].filter(Boolean).join(" ")}
              inert={!active}
              key={menu.key}
              ref={(element) => {
                if (element) {
                  panelRefs.current.set(menu.key, element);
                } else {
                  panelRefs.current.delete(menu.key);
                }
              }}
            >
              <div className="pudans-app-header__mega-inner">
                <div className="pudans-app-header__mega-aside">
                  <p className="pudans-app-header__mega-eyebrow">PUDANS</p>
                  {menu.href ? (
                    <a className="pudans-app-header__mega-title" href={menu.href}>
                      {menu.label}
                    </a>
                  ) : (
                    <h2 className="pudans-app-header__mega-title">{menu.label}</h2>
                  )}
                  {menu.description ? (
                    <p className="pudans-app-header__mega-description">{menu.description}</p>
                  ) : null}
                </div>
                <div className="pudans-app-header__mega-groups">
                  {menu.groups?.map((group) => (
                    <section className="pudans-app-header__mega-group" key={group.key}>
                      {group.title ? (
                        <h3 className="pudans-app-header__mega-group-title">{group.title}</h3>
                      ) : null}
                      <ul className="pudans-app-header__mega-list">
                        {group.items.map((item) => (
                          <li key={item.key}>
                            <a className="pudans-app-header__mega-item" href={item.href}>
                              {item.icon ? (
                                <span className="pudans-app-header__mega-item-icon">{item.icon}</span>
                              ) : null}
                              <span className="pudans-app-header__mega-item-copy">
                                <span className="pudans-app-header__mega-item-label">
                                  {item.label}
                                </span>
                                {item.description ? (
                                  <span className="pudans-app-header__mega-item-description">
                                    {item.description}
                                  </span>
                                ) : null}
                              </span>
                            </a>
                          </li>
                        ))}
                      </ul>
                    </section>
                  ))}
                </div>
              </div>
            </div>
          );
        })}
        {gated ? <LoginGate loginHref={loginHref} overlay /> : null}
      </div>
    </div>
  );
}