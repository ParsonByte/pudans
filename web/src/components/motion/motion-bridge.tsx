"use client";

import { useEffect } from "react";

import { animate, useReducedMotion } from "motion/react";

import { gsap } from "@/lib/gsap";
import { MOTION_DURATION } from "@/lib/motion";

const ENTRANCE_SELECTOR = '[data-slot], [data-motion="enter"]';

const BUILTIN_MOTION_SELECTOR = [
  '[data-slot="alert-dialog-content"]',
  '[data-slot="dialog-content"]',
  '[data-slot="sheet-content"]',
  '[data-slot="popover-content"]',
  '[data-slot="dropdown-menu-content"]',
  '[data-slot="dropdown-menu-sub-content"]',
  '[data-slot="select-content"]',
  '[data-slot="tooltip-content"]',
  '[data-slot="hover-card-content"]',
  '[data-slot="collapsible-content"]',
  '[data-slot="skeleton"]',
  '[data-slot="chart"]',
].join(",");

const INTERACTIVE_SELECTOR = [
  '[data-slot="button"]',
  '[data-slot$="-button"]',
  '[data-slot$="-trigger"]',
  '[data-slot$="-action"]',
  '[data-slot="checkbox"]',
  '[data-slot="switch"]',
  '[data-slot="radio-group-item"]',
  '[data-slot="toggle"]',
  '[data-slot="toggle-group-item"]',
  '[data-slot="dialog-close"]',
  '[data-slot="sheet-close"]',
  '[data-slot="dropdown-menu-item"]',
  '[data-slot="dropdown-menu-checkbox-item"]',
  '[data-slot="dropdown-menu-radio-item"]',
  '[data-slot="select-item"]',
  '[data-slot="command-item"]',
].join(",");

const HOVER_SCALE = 1.01;
const PRESS_SCALE = 0.97;
const ROW_STAGGER_DELAY = 0.02;
const MAX_STAGGER_ROWS = 8;

function isDisabled(element: HTMLElement): boolean {
  return element.hasAttribute("disabled") || element.getAttribute("aria-disabled") === "true";
}

function hasSlotAncestor(element: Element, boundary: Element | null): boolean {
  let parent = element.parentElement;

  while (parent && parent !== boundary) {
    if (parent.matches(ENTRANCE_SELECTOR)) {
      return true;
    }
    parent = parent.parentElement;
  }

  return false;
}

export function MotionBridge() {
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    if (reduceMotion) {
      return;
    }

    const animated = new Set<HTMLElement>();

    const settling = new Set<HTMLElement>();

    const enter = (element: Element) => {
      if (!(element instanceof HTMLElement) || animated.has(element)) {
        return;
      }

      if (element.closest(BUILTIN_MOTION_SELECTOR)) {
        return;
      }

      animated.add(element);
      settling.add(element);
      const rowIndex = element.matches('[data-slot="table-row"]')
        ? Number(element.getAttribute("data-row-index") ?? 0)
        : 0;

      gsap.fromTo(
        element,
        { autoAlpha: 0, y: 6 },
        {
          autoAlpha: 1,
          y: 0,
          delay: Math.min(rowIndex, MAX_STAGGER_ROWS) * ROW_STAGGER_DELAY,
          duration: MOTION_DURATION.base,
          ease: "power2.out",
          overwrite: "auto",
          onComplete: () => settling.delete(element),
        },
      );
    };

    const enterTree = (root: Element) => {
      if (root.matches(ENTRANCE_SELECTOR)) {
        enter(root);
        return;
      }

      root.querySelectorAll(ENTRANCE_SELECTOR).forEach((element) => {
        if (!hasSlotAncestor(element, root)) {
          enter(element);
        }
      });
    };

    document.querySelectorAll(ENTRANCE_SELECTOR).forEach((element) => {
      if (!hasSlotAncestor(element, null)) {
        enter(element);
      }
    });

    const observer = new MutationObserver((records) => {
      for (const record of records) {
        for (const node of record.addedNodes) {
          if (node instanceof Element) {
            enterTree(node);
          }
        }
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });

    const findTarget = (event: Event): HTMLElement | null => {
      const origin = event.target;

      if (!(origin instanceof Element)) {
        return null;
      }

      const target = origin.closest<HTMLElement>(INTERACTIVE_SELECTOR);

      if (
        !target ||
        isDisabled(target) ||
        settling.has(target) ||
        target.closest('[data-motion="off"]')
      ) {
        return null;
      }

      return target;
    };

    const isEnteringChild = (event: PointerEvent, target: HTMLElement): boolean =>
      event.relatedTarget instanceof Node && target.contains(event.relatedTarget);

    const hover = (event: PointerEvent) => {
      const target = findTarget(event);

      if (!target || isEnteringChild(event, target)) {
        return;
      }

      animate(target, { scale: HOVER_SCALE }, { duration: MOTION_DURATION.fast, ease: "easeOut" });
    };

    const leave = (event: PointerEvent) => {
      const target = findTarget(event);

      if (!target || isEnteringChild(event, target)) {
        return;
      }

      animate(target, { scale: 1 }, { duration: MOTION_DURATION.fast, ease: "easeOut" });
    };

    let pressed: HTMLElement | null = null;

    const press = (event: PointerEvent) => {
      const target = findTarget(event);

      if (!target) {
        return;
      }

      pressed = target;
      animate(target, { scale: PRESS_SCALE }, { duration: MOTION_DURATION.fast, ease: "easeOut" });
    };

    const release = () => {
      if (!pressed) {
        return;
      }

      animate(pressed, { scale: 1 }, { duration: MOTION_DURATION.fast, ease: "easeOut" });
      pressed = null;
    };

    document.addEventListener("pointerover", hover);
    document.addEventListener("pointerout", leave);
    document.addEventListener("pointerdown", press);
    document.addEventListener("pointerup", release);
    document.addEventListener("pointercancel", release);

    return () => {
      observer.disconnect();
      document.removeEventListener("pointerover", hover);
      document.removeEventListener("pointerout", leave);
      document.removeEventListener("pointerdown", press);
      document.removeEventListener("pointerup", release);
      document.removeEventListener("pointercancel", release);
      animated.forEach((element) => gsap.killTweensOf(element));
      settling.clear();
    };
  }, [reduceMotion]);

  return null;
}
