"use client";

import { useRouter } from "next/navigation";

import {
    CalendarIcon,
    CheckCircleIcon,
    ChevronLeftIcon,
    DesktopIcon,
    GiftIcon,
    ImageIcon,
    MobileIcon,
    ShareIcon,
    TimeIcon,
    UserIcon,
} from "tdesign-icons-react";

import { INDEX_PAGE_KEYS, indexPageHref } from "@/routes/(_workspace)/(_index)/paths";

import { INTRO_LINE_KEYS, REWARD_KEYS, STEP_KEYS, WORK_KEYS } from "./composables/_activity-detail";
import styles from "./styles/activity-detail-view.module.css";

type ActivityDetailViewProps = {
    name: string;
};

export function ActivityDetailView({ name }: ActivityDetailViewProps) {
    const router = useRouter();

    return (
        <section aria-labelledby="activity-detail-title" className={styles.page} data-activity-id={name}>
            <div className={styles.scroller}>
                <div className={styles.topBar}>
                    <button
                        aria-label="返回创作活动"
                        className={styles.backButton}
                        onClick={() => {
                            if (window.history.length > 1) {
                                router.back();
                                return;
                            }

                            router.push(indexPageHref(INDEX_PAGE_KEYS.create));
                        }}
                        type="button"
                    >
                        <ChevronLeftIcon size={22} />
                    </button>
                    <h1 className={styles.pageTitle} id="activity-detail-title">
                        创作活动
                    </h1>
                    <span className={styles.pendingBadge}>活动详情接口待接入</span>
                </div>

                <div aria-label="活动主视觉，接口待接入" className={styles.hero} role="img">
                    <span className={styles.heroBadge}>创作活动</span>
                    <span className={styles.heroIcon}>
                        <CalendarIcon size={34} />
                    </span>
                    <span className={styles.heroLabel}>活动主视觉接口待接入</span>
                </div>

                <div className={styles.layout}>
                    <div className={styles.mainColumn}>
                        <section className={styles.panel}>
                            <div className={styles.panelHeader}>
                                <h2>活动介绍</h2>
                                <span className={styles.sectionPending}>接口待接入</span>
                            </div>
                            <div aria-hidden className={styles.introCopy}>
                                {INTRO_LINE_KEYS.map((key, index) => (
                                    <span
                                        className={`${styles.skeletonLine} ${
                                            index === INTRO_LINE_KEYS.length - 1 ? styles.skeletonLineShort : ""
                                        }`}
                                        key={key}
                                    />
                                ))}
                            </div>
                        </section>

                        <section className={styles.panel}>
                            <div className={styles.panelHeader}>
                                <h2>参与方式</h2>
                                <span className={styles.sectionPending}>接口待接入</span>
                            </div>
                            <div className={styles.steps}>
                                {STEP_KEYS.map((key, index) => (
                                    <div className={styles.stepCard} key={key}>
                                        <span className={styles.stepNumber}>{String(index + 1).padStart(2, "0")}</span>
                                        <span aria-hidden className={styles.stepCopy}>
                                            <span className={`${styles.skeletonLine} ${styles.skeletonLineMedium}`} />
                                            <span className={`${styles.skeletonLine} ${styles.skeletonLineWide}`} />
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className={styles.panel}>
                            <div className={styles.panelHeader}>
                                <h2>活动作品</h2>
                                <span className={styles.sectionPending}>作品接口待接入</span>
                            </div>
                            <div aria-hidden className={styles.worksGrid}>
                                {WORK_KEYS.map((key) => (
                                    <span className={styles.workCard} key={key}>
                                        <ImageIcon size={20} />
                                    </span>
                                ))}
                            </div>
                        </section>
                    </div>

                    <aside aria-label="活动信息" className={styles.sideColumn}>
                        <section className={styles.panel}>
                            <div className={styles.panelHeader}>
                                <h2>活动信息</h2>
                            </div>
                            <div className={styles.infoList}>
                                <div className={styles.infoRow}>
                                    <CalendarIcon size={17} />
                                    <span>活动时间</span>
                                    <span aria-hidden className={styles.infoValue} />
                                </div>
                                <div className={styles.infoRow}>
                                    <TimeIcon size={17} />
                                    <span>投稿截止</span>
                                    <span aria-hidden className={styles.infoValue} />
                                </div>
                                <div className={styles.infoRow}>
                                    <UserIcon size={17} />
                                    <span>主办方</span>
                                    <span aria-hidden className={styles.infoValue} />
                                </div>
                                <div className={styles.infoRow}>
                                    <ShareIcon size={17} />
                                    <span>投稿平台</span>
                                    <span aria-hidden className={styles.infoValue} />
                                </div>
                            </div>
                            <button
                                aria-disabled="true"
                                className={styles.joinButton}
                                title="创作活动接口待接入"
                                type="button"
                            >
                                <CheckCircleIcon size={17} />
                                参与活动
                            </button>
                        </section>

                        <section className={styles.panel}>
                            <div className={styles.panelHeader}>
                                <h2>活动奖励</h2>
                            </div>
                            <div className={styles.rewardList}>
                                {REWARD_KEYS.map((key, index) => (
                                    <div className={styles.rewardRow} key={key}>
                                        <span className={styles.rewardIcon}>
                                            <GiftIcon size={18} />
                                        </span>
                                        <span aria-hidden className={styles.rewardCopy}>
                                            <span
                                                className={`${styles.skeletonLine} ${
                                                    index === 0 ? styles.skeletonLineWide : styles.skeletonLineMedium
                                                }`}
                                            />
                                            <span className={`${styles.skeletonLine} ${styles.skeletonLineShort}`} />
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className={styles.panel}>
                            <div className={styles.panelHeader}>
                                <h2>投稿方式</h2>
                            </div>
                            <div className={styles.platformList}>
                                <span className={styles.platformRow}>
                                    <DesktopIcon size={18} />
                                    网页端
                                </span>
                                <span className={styles.platformRow}>
                                    <MobileIcon size={18} />
                                    移动端
                                </span>
                            </div>
                        </section>
                    </aside>
                </div>
            </div>
        </section>
    );
}
