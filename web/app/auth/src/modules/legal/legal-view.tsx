import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { Icon, Tabs, type TabItem } from "@pudans/components";
import { getLegalDocument, type LegalDocument, type LegalDocumentKey } from "./legal-copy";
import styles from "./legal-view.module.css";

const LEGAL_KEYS: readonly LegalDocumentKey[] = ["terms", "privacy", "confidentiality"];

function isLegalKey(value: string | null): value is LegalDocumentKey {
  return value !== null && (LEGAL_KEYS as readonly string[]).includes(value);
}

function LegalArticle({ document }: { document: LegalDocument }) {
  const { t } = useTranslation("auth");

  return (
    <article className={styles.article}>
      <p className={styles.kicker}>{document.kicker}</p>
      <h2 className={styles.docTitle}>{document.title}</h2>
      <p className={styles.updated}>{document.updated}</p>
      <p className={styles.intro}>{document.intro}</p>

      {document.sections.map((section) => (
        <section className={styles.section} key={section.heading}>
          <h3>{section.heading}</h3>
          {section.paragraphs.map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
        </section>
      ))}

      <p className={styles.note}>{t("legalNote")}</p>
    </article>
  );
}

export function LegalView() {
  const { t, i18n } = useTranslation("auth");
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get("tab");
  const activeTab: LegalDocumentKey = isLegalKey(requested) ? requested : "terms";

  const items: TabItem[] = LEGAL_KEYS.map((key) => {
    const document = getLegalDocument(key, i18n.language);
    return {
      key,
      label: document.title,
      content: <LegalArticle document={document} />,
    };
  });

  return (
    <div className={styles.view}>
      <div className={styles.header}>
        <div>
          <p className={styles.eyebrow}>LEGAL · PUDANS</p>
          <h1 className={styles.title}>{t("legalTitle")}</h1>
        </div>
        <Link className={styles.back} to="/">
          <Icon name="mdi:arrow-left" size={15} />
          {t("legalBack")}
        </Link>
      </div>

      <Tabs
        aria-label={t("legalTitle")}
        className={styles.tabs}
        panelClassName={styles.panel}
        size="sm"
        fullWidth
        value={activeTab}
        items={items}
        onValueChange={(value: string) => {
          if (isLegalKey(value)) {
            setSearchParams({ tab: value }, { replace: true });
          }
        }}
      />
    </div>
  );
}
