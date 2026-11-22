import { AuthFormCard } from "../../components/auth-form-card";
import { TwoFactorForm } from "./components/two-factor-form";

type TwoFactorViewProps = {
    redirectTo?: string;
    username?: string;
    onBack: () => void;
};

export function TwoFactorView({ onBack, redirectTo, username }: TwoFactorViewProps) {
    return (
        <AuthFormCard
            description={
                username
                    ? `请输入账号 ${username} 的身份验证器动态验证码。`
                    : "请输入身份验证器应用中的 6 位动态验证码。"
            }
            eyebrow="两步验证"
            title="确认你的身份"
        >
            <TwoFactorForm onBack={onBack} redirectTo={redirectTo} />
        </AuthFormCard>
    );
}
