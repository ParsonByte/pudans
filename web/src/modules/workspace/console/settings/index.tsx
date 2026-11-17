import type { ComponentType } from "react";

import {
  ClipboardList,
  Globe2,
  KeyRound,
  type LucideIcon,
  Mail,
  Server,
  ShieldCheck,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { ConsolePage, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { PARAMETER_GROUPS, type ParameterGroupKey } from "./lib/parameter-fields";
import { AuditSettingsPage } from "./pages/audit-settings";
import { CaptchaSettingsPage } from "./pages/captcha-settings";
import { MailSettingsPage } from "./pages/mail-settings";
import { ServerSettingsPage } from "./pages/server-settings";
import { SessionSettingsPage } from "./pages/session-settings";
import { SiteSettingsPage } from "./pages/site-settings";

const GROUP_ICONS: Record<ParameterGroupKey, LucideIcon> = {
  site: Globe2,
  session: KeyRound,
  captcha: ShieldCheck,
  mail: Mail,
  audit: ClipboardList,
  server: Server,
};

const SETTINGS_PAGES: Record<ParameterGroupKey, ComponentType> = {
  site: SiteSettingsPage,
  session: SessionSettingsPage,
  captcha: CaptchaSettingsPage,
  mail: MailSettingsPage,
  audit: AuditSettingsPage,
  server: ServerSettingsPage,
};

type ConsoleSettingsViewProps = {
  activeTab: ParameterGroupKey;
  onTabChange: (tab: ParameterGroupKey) => void;
};

export function ConsoleSettingsView({
  activeTab: activeKey,
  onTabChange,
}: ConsoleSettingsViewProps) {
  const activeGroup = PARAMETER_GROUPS.find((group) => group.key === activeKey);
  const ActiveSettingsPage = SETTINGS_PAGES[activeKey];

  return (
    <ConsolePage title="参数配置">
      <div className="flex flex-col gap-6 md:flex-row">
        <aside className="md:w-48 md:shrink-0">
          <div className="space-y-3 md:sticky md:top-24">
            <div className="md:hidden">
              <Select
                value={activeKey}
                onValueChange={(value) => onTabChange(value as ParameterGroupKey)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PARAMETER_GROUPS.map((group) => {
                    const Icon = GROUP_ICONS[group.key];

                    return (
                      <SelectItem key={group.key} value={group.key}>
                        <Icon className="size-4" />
                        {group.label}
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
            </div>

            <nav className="hidden md:flex md:flex-col md:gap-1" aria-label="参数设置分组">
              {PARAMETER_GROUPS.map((group) => {
                const Icon = GROUP_ICONS[group.key];
                const isActive = group.key === activeKey;

                return (
                  <Button
                    key={group.key}
                    type="button"
                    variant="ghost"
                    className={cn(
                      "h-9 w-full justify-start gap-2",
                      isActive && "bg-muted text-foreground",
                    )}
                    aria-current={isActive ? "page" : undefined}
                    onClick={() => onTabChange(group.key)}
                  >
                    <Icon className="size-4" />
                    {group.label}
                  </Button>
                );
              })}
            </nav>
          </div>
        </aside>

        <div className="min-w-0 flex-1">
          {activeGroup ? (
            <ConsoleSection>
              <div className="border-b px-5 py-4">
                <h2 className="text-xl font-semibold tracking-tight">{activeGroup.label}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{activeGroup.description}</p>
              </div>
              <div className="p-5">
                <ActiveSettingsPage key={activeGroup.key} />
              </div>
            </ConsoleSection>
          ) : null}
        </div>
      </div>
    </ConsolePage>
  );
}
