import { House } from "lucide-react";

type WorkspaceButtonProps = {
  href?: string;
};

export function WorkspaceButton({ href = "/console" }: WorkspaceButtonProps) {
  return (
    <a className="pudans-app-header__workspace" href={href}>
      <House size={15} />
      <span>工作台</span>
    </a>
  );
}