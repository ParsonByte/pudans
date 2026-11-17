import { useMutation } from "@tanstack/react-query";
import { Loader2, PlugZap } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  type AuthenticationConnectionTestRequest,
  resolveParameterSettingError,
  testAuthenticationConnection,
} from "@/modules/workspace/console/settings/lib/parameter-api";

type TestConnectionButtonProps = {
  buildRequest: () => AuthenticationConnectionTestRequest;
  disabled?: boolean;
};

export function TestConnectionButton({ buildRequest, disabled }: TestConnectionButtonProps) {
  const mutation = useMutation({
    mutationFn: testAuthenticationConnection,
    onSuccess: (result) => {
      if (result.success) {
        toast.success(result.message);
      } else {
        toast.error(result.message);
      }
    },
    onError: (error) => {
      toast.error(resolveParameterSettingError(error));
    },
  });

  return (
    <Button
      type="button"
      size="sm"
      variant="outline"
      disabled={disabled || mutation.isPending}
      onClick={() => mutation.mutate(buildRequest())}
    >
      {mutation.isPending ? <Loader2 className="animate-spin" /> : <PlugZap />}
      测试连接
    </Button>
  );
}
