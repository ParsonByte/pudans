import { Broom } from "lucide-react";
import { Button, Tooltip } from "tdesign-react";

import styles from "./floating-chat-composer.module.css";

type ComposerResetButtonProps = {
    onReset: () => void;
};

export function ComposerResetButton({ onReset }: ComposerResetButtonProps) {
    return (
        <Tooltip content="清空输入内容">
            <Button
                aria-label="清空输入内容"
                className={styles.iconButton}
                icon={<Broom size={16} />}
                onClick={onReset}
                shape="square"
                type="button"
                variant="text"
            />
        </Tooltip>
    );
}
