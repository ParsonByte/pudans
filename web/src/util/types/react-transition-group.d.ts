declare module "react-transition-group" {
  import type { ComponentType, ReactNode, Ref } from "react";

  export interface CSSTransitionClassNames {
    appear?: string;
    appearActive?: string;
    appearDone?: string;
    enter?: string;
    enterActive?: string;
    enterDone?: string;
    exit?: string;
    exitActive?: string;
    exitDone?: string;
  }

  export interface CSSTransitionProps {
    in?: boolean;
    appear?: boolean;
    enter?: boolean;
    exit?: boolean;
    timeout: number | { enter?: number; exit?: number; appear?: number };
    classNames?: string | CSSTransitionClassNames;
    nodeRef?: Ref<HTMLElement>;
    onEnter?: () => void;
    onEntering?: () => void;
    onEntered?: () => void;
    onExit?: () => void;
    onExiting?: () => void;
    onExited?: () => void;
    mountOnEnter?: boolean;
    unmountOnExit?: boolean;
    children?: ReactNode;
  }

  export interface TransitionGroupProps {
    component?: ComponentType<Record<string, unknown>> | string;
    className?: string;
    childFactory?: (child: ReactNode) => ReactNode;
    children?: ReactNode;
  }

  export interface SwitchTransitionProps {
    mode?: "out-in" | "in-out";
    children?: ReactNode;
  }

  export const CSSTransition: ComponentType<CSSTransitionProps>;
  export const SwitchTransition: ComponentType<SwitchTransitionProps>;
  export const Transition: ComponentType<Record<string, unknown>>;
  export const TransitionGroup: ComponentType<TransitionGroupProps>;
}
