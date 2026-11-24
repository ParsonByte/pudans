"use client";

import {
    BookOpenText,
    Crosshair,
    EyeOff,
    Map,
    Maximize2,
    ScrollText,
    Settings2,
    SlidersHorizontal,
    Sparkles,
    Telescope,
} from "lucide-react";

import { setDreamDirectoryOpen, setDreamHudVisible, toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { toggleDocumentFullscreen } from "../../world/fullscreen";
import { AudioToggle } from "../audio/audio-toggle";
import { CameraModeSwitcher } from "../camera/camera-mode-switcher";
import { requestPointerLock } from "../camera/mouse-look";
import { PhotoModeCamera } from "../camera/photo-mode-camera";
import { ModeSwitcher } from "./mode-switcher";
import { NotificationBell } from "./notification-bell";
import { RegionTitle } from "./region-title";
import { SeedBadge } from "./seed-badge";
import { SessionBadge } from "./session-badge";

export function TopBar() {
    const directoryOpen = useDreamStore((state) => state.directoryOpen);

    return (
        <div className={styles.hudTop}>
            <div className={styles.topBar}>
                <div className={styles.topBarBrand}>
                    <span aria-hidden className={styles.logoMark}>
                        <Sparkles size={13} />
                    </span>
                    <RegionTitle />
                </div>
                <div className={styles.topMeta}>
                    <SeedBadge />
                    <SessionBadge />
                </div>
                <ModeSwitcher />
            </div>
            <div className={styles.topActions}>
                <button
                    aria-label="打开记忆目录"
                    className={`${styles.iconButton} ${directoryOpen ? styles.iconButtonActive : ""}`}
                    onClick={() => setDreamDirectoryOpen(!directoryOpen)}
                    type="button"
                >
                    <Telescope aria-hidden size={15} />
                </button>
                <button
                    aria-label="打开小地图"
                    className={styles.iconButton}
                    onClick={() => toggleDreamPanel("map")}
                    type="button"
                >
                    <Map aria-hidden size={15} />
                </button>
                <NotificationBell />
                <button
                    aria-label="打开梦境日志"
                    className={styles.iconButton}
                    onClick={() => toggleDreamPanel("journal")}
                    type="button"
                >
                    <BookOpenText aria-hidden size={15} />
                </button>
                <button
                    aria-label="打开记忆筛选"
                    className={styles.iconButton}
                    onClick={() => toggleDreamPanel("filters")}
                    type="button"
                >
                    <SlidersHorizontal aria-hidden size={15} />
                </button>
                <CameraModeSwitcher />
                <button
                    aria-label="锁定鼠标视角"
                    className={styles.iconButton}
                    onClick={requestPointerLock}
                    type="button"
                >
                    <Crosshair aria-hidden size={15} />
                </button>
                <PhotoModeCamera />
                <button
                    aria-label="打开灵感重组"
                    className={styles.iconButton}
                    onClick={() => toggleDreamPanel("inspiration")}
                    type="button"
                >
                    <Sparkles aria-hidden size={15} />
                </button>
                <button
                    aria-label="打开区域叙事"
                    className={styles.iconButton}
                    onClick={() => toggleDreamPanel("story")}
                    type="button"
                >
                    <ScrollText aria-hidden size={15} />
                </button>
                <AudioToggle />
                <button
                    aria-label="打开设置"
                    className={styles.iconButton}
                    onClick={() => toggleDreamPanel("settings")}
                    type="button"
                >
                    <Settings2 aria-hidden size={15} />
                </button>
                <button
                    aria-label="切换全屏"
                    className={styles.iconButton}
                    onClick={toggleDocumentFullscreen}
                    type="button"
                >
                    <Maximize2 aria-hidden size={15} />
                </button>
                <button
                    aria-label="隐藏界面"
                    className={styles.iconButton}
                    onClick={() => setDreamHudVisible(false)}
                    type="button"
                >
                    <EyeOff aria-hidden size={15} />
                </button>
            </div>
        </div>
    );
}
