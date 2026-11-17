import { type ComponentProps, type Ref, useState } from "react";

import { Eye, EyeOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

type PasswordInputProps = Omit<ComponentProps<typeof Input>, "type"> & {
  ref?: Ref<HTMLInputElement>;
};

export function PasswordInput({ className, disabled, ref, ...props }: PasswordInputProps) {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <div className={cn("relative", className)}>
      <Input
        type={showPassword ? "text" : "password"}
        className="pe-9"
        ref={ref}
        disabled={disabled}
        {...props}
      />
      <Button
        type="button"
        size="icon-sm"
        variant="ghost"
        disabled={disabled}
        className="absolute inset-e-1 top-1/2 -translate-y-1/2 text-muted-foreground"
        onClick={() => setShowPassword((previous) => !previous)}
      >
        {showPassword ? <Eye /> : <EyeOff />}
        <span className="sr-only">{showPassword ? "隐藏密码" : "显示密码"}</span>
      </Button>
    </div>
  );
}
