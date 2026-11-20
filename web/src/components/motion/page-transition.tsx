"use client";

import type { ReactNode } from "react";

import { motion, useReducedMotion } from "motion/react";
import { usePathname } from "next/navigation";

import { pageVariants } from "@/lib/motion";
import { cn } from "@/lib/utils";

type PageTransitionProps = {
  className?: string;
  children: ReactNode;
};

export function PageTransition({ className, children }: PageTransitionProps) {
  const pathname = usePathname();
  const reduceMotion = useReducedMotion();

  return (
    <motion.div
      key={pathname}
      initial={reduceMotion ? false : "initial"}
      animate="animate"
      variants={pageVariants}
      className={cn("flex min-h-0 flex-1 flex-col", className)}
    >
      {children}
    </motion.div>
  );
}
