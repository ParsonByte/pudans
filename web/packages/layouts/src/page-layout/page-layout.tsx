import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { Box } from "@mui/material";
import { PageHeaderRow, PageSurface } from "../shared/page-surface";

export interface PageLayoutProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  "title" | "children"
> {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  header?: ReactNode;
  footer?: ReactNode;
  /** Disables the legacy refresh IconButton while a request is in flight. */
  loading?: boolean;
  onRefresh?: () => void;
  refreshLabel?: string;
  children: ReactNode;
}

/**
 * Legacy Pages/PageHeader + Pages/PageContainer composition: a page surface
 * card with the PageHeader row (h4/600, mb 32px, optional refresh button and
 * right-aligned actions) followed by the page content.
 *
 * The legacy app composed these pieces per page inside a Container; this
 * layout keeps the same visual contract (card py 32px, header mb 32px,
 * content horizontal padding 24px) behind a single component.
 */
export const PageLayout = forwardRef<HTMLDivElement, PageLayoutProps>(function PageLayout(
  {
    title,
    description,
    actions,
    header,
    footer,
    loading,
    onRefresh,
    refreshLabel,
    children,
    className,
    ...props
  },
  ref,
) {
  return (
    <Box
      ref={ref}
      className={className}
      sx={{ flexGrow: 1, minHeight: 0, display: "flex", flexDirection: "column" }}
      {...props}
    >
      <PageSurface>
        {header ?? (
          <PageHeaderRow
            title={title}
            description={description}
            actions={actions}
            loading={loading}
            onRefresh={onRefresh}
            refreshLabel={refreshLabel}
          />
        )}
        <Box sx={{ px: 3 }}>{children}</Box>
        {footer ? <Box sx={{ px: 3, pt: 2 }}>{footer}</Box> : null}
      </PageSurface>
    </Box>
  );
});

PageLayout.displayName = "PageLayout";
