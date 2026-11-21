"use client";

import {
  type ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";

import { ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { resolveApiError } from "@/lib/api-error";
import { type SudoMethod, confirmSudo, fetchSudoStatus, sendSudoCode } from "@/lib/sudo";

const ERROR_MESSAGES = {
  notFound: "当前账号不可用。",
  conflict: "安全确认失败，请重试。",
};

type SudoContextValue = {
  requireSudo: () => Promise<void>;
};

type PendingSudo = {
  promise: Promise<void>;
  resolve: () => void;
  reject: (error: Error) => void;
};

const SudoContext = createContext<SudoContextValue | null>(null);

export function useSudo(): SudoContextValue {
  const context = useContext(SudoContext);
  if (!context) {
    throw new Error("useSudo must be used within SudoProvider");
  }
  return context;
}

export function SudoProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [methods, setMethods] = useState<SudoMethod[]>([]);
  const [method, setMethod] = useState("");
  const [code, setCode] = useState("");
  const [pending, setPending] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pendingRef = useRef<PendingSudo | null>(null);

  const openSudo = useCallback((nextMethods: SudoMethod[]) => {
    const current = pendingRef.current;
    if (current) {
      return current.promise;
    }
    if (nextMethods.length === 0) {
      return Promise.resolve();
    }
    let resolve: () => void = () => undefined;
    let reject: (error: Error) => void = () => undefined;
    const promise = new Promise<void>((resolvePromise, rejectPromise) => {
      resolve = resolvePromise;
      reject = rejectPromise;
    });
    pendingRef.current = { promise, resolve, reject };
    setMethods(nextMethods);
    setMethod(nextMethods[0]?.name ?? "");
    setCode("");
    setError(null);
    setOpen(true);
    return promise;
  }, []);

  const finish = useCallback((approved: boolean) => {
    const current = pendingRef.current;
    pendingRef.current = null;
    setOpen(false);
    setPending(false);
    setSending(false);
    setCode("");
    setError(null);
    if (!current) {
      return;
    }
    if (approved) {
      current.resolve();
    } else {
      current.reject(new Error("已取消安全确认。"));
    }
  }, []);

  const requireSudo = useCallback(async () => {
    const status = await fetchSudoStatus();
    if (status.active || status.methods.length === 0) {
      return;
    }
    await openSudo(status.methods);
  }, [openSudo]);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<{ methods?: string[] }>).detail;
      const names = detail?.methods ?? [];
      const nextMethods =
        names.length > 0
          ? names.map((name) => ({ name, canSendCode: false }))
          : [{ name: "TOTP", canSendCode: false }];
      void openSudo(nextMethods).catch(() => undefined);
    };
    window.addEventListener("pudans:sudo-required", handler);
    return () => window.removeEventListener("pudans:sudo-required", handler);
  }, [openSudo]);

  const activeMethod = methods.find((item) => item.name === method);

  const handleSendCode = async () => {
    if (!method) {
      setError("请选择验证方式。");
      return;
    }
    setSending(true);
    setError(null);
    try {
      await sendSudoCode(method);
      toast.success("验证码已发送。");
    } catch (sendError) {
      setError(resolveApiError(sendError, ERROR_MESSAGES));
    } finally {
      setSending(false);
    }
  };

  const handleConfirm = async () => {
    if (!method) {
      setError("请选择验证方式。");
      return;
    }
    if (!code.trim()) {
      setError("请输入验证码。");
      return;
    }
    setPending(true);
    setError(null);
    try {
      await confirmSudo(method, code.trim());
      toast.success("安全确认已完成。");
      finish(true);
    } catch (confirmError) {
      setError(resolveApiError(confirmError, ERROR_MESSAGES));
    } finally {
      setPending(false);
    }
  };

  return (
    <SudoContext.Provider value={{ requireSudo }}>
      {children}
      <Dialog
        open={open}
        onOpenChange={(nextOpen) => {
          if (!nextOpen && !pending) {
            finish(false);
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldCheck className="size-4" />
              安全确认
            </DialogTitle>
            <DialogDescription>
              该操作涉及账号安全，请先完成二次验证。
              {activeMethod?.maskedTarget ? ` 验证对象：${activeMethod.maskedTarget}。` : ""}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {methods.length > 1 ? (
              <div className="space-y-2">
                <p className="text-xs font-medium text-muted-foreground">验证方式</p>
                <Select value={method} onValueChange={setMethod} disabled={pending}>
                  <SelectTrigger className="w-full" aria-label="选择安全验证方式">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {methods.map((item) => (
                      <SelectItem key={item.name} value={item.name}>
                        {item.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}

            <div className="space-y-2">
              <p className="text-xs font-medium text-muted-foreground">验证码</p>
              <div className="flex items-center gap-2">
                <Input
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                  placeholder="请输入 6 位验证码"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  disabled={pending}
                  aria-label="安全确认验证码"
                />
                {activeMethod?.canSendCode ? (
                  <Button
                    type="button"
                    variant="outline"
                    onClick={handleSendCode}
                    disabled={pending || sending}
                  >
                    {sending ? "发送中…" : "发送验证码"}
                  </Button>
                ) : null}
              </div>
            </div>

            {error ? (
              <p className="text-xs text-destructive" role="alert">
                {error}
              </p>
            ) : null}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => finish(false)}
              disabled={pending}
            >
              取消
            </Button>
            <Button type="button" onClick={handleConfirm} disabled={pending || !code.trim()}>
              {pending ? "确认中…" : "完成确认"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </SudoContext.Provider>
  );
}
