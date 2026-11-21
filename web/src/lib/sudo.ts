import { axiosInstance } from "@pudans-dev/api-client";

export type SudoMethod = {
  name: string;
  canSendCode: boolean;
  maskedTarget?: string | null;
};

export type SudoStatus = {
  active: boolean;
  expiresAt?: string | null;
  methods: SudoMethod[];
};

const SUDO_PATH = "/sudo";

const FORM_HEADERS = { "Content-Type": "application/x-www-form-urlencoded" };

export async function fetchSudoStatus(): Promise<SudoStatus> {
  const response = await axiosInstance.get<SudoStatus>(SUDO_PATH);
  const methods = Array.isArray(response.data?.methods) ? response.data.methods : [];
  return {
    active: Boolean(response.data?.active),
    expiresAt: response.data?.expiresAt ?? null,
    methods,
  };
}

export async function sendSudoCode(method: string): Promise<void> {
  await axiosInstance.post(`${SUDO_PATH}/code`, new URLSearchParams({ method }).toString(), {
    headers: FORM_HEADERS,
  });
}

export async function confirmSudo(method: string, code: string): Promise<void> {
  await axiosInstance.post(
    `${SUDO_PATH}/confirm`,
    new URLSearchParams({ method, code }).toString(),
    { headers: FORM_HEADERS },
  );
}
