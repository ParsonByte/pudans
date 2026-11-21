"use client";

import { useState } from "react";

import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import { SystemConfigForm } from "../components/system-config-form";
import { DEFAULT_SYSTEM_CONFIG_GROUP, SYSTEM_CONFIG_GROUPS } from "../lib/system-config-groups";

export function SystemConfigSettingsPage() {
  const [group, setGroup] = useState(DEFAULT_SYSTEM_CONFIG_GROUP);
  const activeGroup = SYSTEM_CONFIG_GROUPS.find((item) => item.value === group);

  return (
    <div className="max-w-3xl space-y-5">
      <div className="space-y-2">
        <Label htmlFor="system-config-group">配置分组</Label>
        <Select value={group} onValueChange={setGroup}>
          <SelectTrigger id="system-config-group" className="w-full sm:max-w-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {SYSTEM_CONFIG_GROUPS.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {activeGroup ? (
          <p className="text-xs leading-5 text-muted-foreground">{activeGroup.description}</p>
        ) : null}
      </div>

      <SystemConfigForm key={group} group={group} />
    </div>
  );
}
