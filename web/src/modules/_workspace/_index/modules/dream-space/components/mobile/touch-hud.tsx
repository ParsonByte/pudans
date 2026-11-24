"use client";

import styles from "../../styles/dream-space.module.css";
import { MobileActionButtons } from "./mobile-action-buttons";
import { TouchLookArea } from "./touch-look-area";
import { VirtualJoystick } from "./virtual-joystick";

export function TouchHUD() {
    return (
        <>
            <TouchLookArea />
            <div className={styles.mobileControls}>
                <VirtualJoystick />
                <MobileActionButtons />
            </div>
        </>
    );
}
