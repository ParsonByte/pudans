import { type ReactNode } from "react";

import { useNavigate, useRouter } from "@tanstack/react-router";

import { Button } from "@/components/ui/button";

type ErrorPageProps = {
  status: string;
  title: string;
  description: string;
  actions?: ReactNode;
};

export function ErrorPage({ status, title, description, actions }: ErrorPageProps) {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-2 bg-background px-6 py-16 text-center">
      <p className="text-[7rem] leading-none font-bold tracking-tight">{status}</p>
      <h1 className="text-xl font-semibold">{title}</h1>
      <p className="max-w-md text-sm leading-6 text-muted-foreground">{description}</p>
      {actions ? (
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">{actions}</div>
      ) : null}
    </main>
  );
}

export function BackHomeActions() {
  const navigate = useNavigate();
  const { history } = useRouter();

  return (
    <>
      <Button variant="outline" onClick={() => history.go(-1)}>
        返回上一页
      </Button>
      <Button onClick={() => navigate({ to: "/" })}>返回首页</Button>
    </>
  );
}
