export type LegalDocumentKey = "terms" | "privacy" | "confidentiality";
export type LegalLanguage = "zh-CN" | "en-US";

export interface LegalSection {
  heading: string;
  paragraphs: string[];
}

export interface LegalDocument {
  kicker: string;
  title: string;
  updated: string;
  intro: string;
  sections: LegalSection[];
}

const documents: Record<LegalDocumentKey, Record<LegalLanguage, LegalDocument>> = {
  terms: {
    "zh-CN": {
      kicker: "LEGAL · TERMS",
      title: "用户协议",
      updated: "2026-11-15",
      intro:
        "欢迎使用 Pudans。本协议说明你在使用本实例提供的身份、权限与协作服务时，与你相关的权利和义务。",
      sections: [
        {
          heading: "1. 服务说明",
          paragraphs: [
            "Pudans 提供统一的身份认证、权限管理和应用接入能力。具体可用功能取决于实例管理员启用的模块和你的账号权限。",
            "你应当仅将本服务用于合法、正当的目的，并遵守你所在组织制定的内部规范。",
          ],
        },
        {
          heading: "2. 账号与安全",
          paragraphs: [
            "你应妥善保管登录凭据，不得将账号转借他人。发现异常登录或凭据泄露时，应及时联系实例管理员。",
            "管理员有权在必要时暂停或限制账号访问，以保护系统和组织数据安全。",
          ],
        },
        {
          heading: "3. 使用规范",
          paragraphs: [
            "不得利用本服务实施未经授权的访问、数据抓取、恶意扫描、拒绝服务攻击或其他危害系统安全的行为。",
            "不得上传或传播违法、侵权或含有恶意代码的内容。",
          ],
        },
        {
          heading: "4. 协议变更",
          paragraphs: [
            "本协议可能随产品迭代更新。更新后的条款将在本页面发布，继续使用服务即视为接受更新后的协议。",
          ],
        },
      ],
    },
    "en-US": {
      kicker: "LEGAL · TERMS",
      title: "Terms of Service",
      updated: "2026-11-15",
      intro:
        "Welcome to Pudans. These terms describe your rights and responsibilities when using the identity, permission and collaboration services provided by this instance.",
      sections: [
        {
          heading: "1. Service Scope",
          paragraphs: [
            "Pudans provides unified authentication, permission management and application access. Available features depend on the modules enabled by the instance administrator and on your account permissions.",
            "You may use the service only for lawful purposes and in accordance with the internal policies of your organization.",
          ],
        },
        {
          heading: "2. Accounts and Security",
          paragraphs: [
            "Keep your credentials safe and do not share your account. Contact the administrator if you notice unusual sign-ins or credential exposure.",
            "Administrators may suspend or restrict access when necessary to protect the system and organizational data.",
          ],
        },
        {
          heading: "3. Acceptable Use",
          paragraphs: [
            "Do not attempt unauthorized access, scraping, malicious scanning, denial-of-service attacks or any other activity that compromises the system.",
            "Do not upload or distribute unlawful, infringing or malicious content.",
          ],
        },
        {
          heading: "4. Changes",
          paragraphs: [
            "These terms may be updated as the product evolves. Updated terms are published on this page, and continued use of the service constitutes acceptance.",
          ],
        },
      ],
    },
  },
  privacy: {
    "zh-CN": {
      kicker: "LEGAL · PRIVACY",
      title: "隐私政策",
      updated: "2026-11-15",
      intro: "本政策说明 Pudans 在提供身份与权限服务时如何收集、使用、存储和保护你的个人信息。",
      sections: [
        {
          heading: "1. 信息收集",
          paragraphs: [
            "为完成身份认证和账号管理，我们可能处理你的用户名、邮箱、登录时间、登录设备信息以及管理员配置的组织信息。",
            "我们仅在实现服务功能所必需的范围内收集信息。",
          ],
        },
        {
          heading: "2. 信息使用",
          paragraphs: [
            "信息用于登录认证、权限校验、安全审计、异常登录提醒和服务改进，不会用于与服务无关的广告投放。",
            "除非获得授权或法律要求，我们不会向第三方披露你的个人信息。",
          ],
        },
        {
          heading: "3. 存储与保护",
          paragraphs: [
            "数据存储位置和保留期限由实例部署方决定。我们采用访问控制、传输加密和安全审计等措施保护数据。",
          ],
        },
        {
          heading: "4. 你的权利",
          paragraphs: [
            "你可以联系实例管理员查询、更正或删除与你相关的信息，或了解账号数据的处理方式。",
          ],
        },
      ],
    },
    "en-US": {
      kicker: "LEGAL · PRIVACY",
      title: "Privacy Policy",
      updated: "2026-11-15",
      intro:
        "This policy explains how Pudans collects, uses, stores and protects personal information while providing identity and permission services.",
      sections: [
        {
          heading: "1. Information We Collect",
          paragraphs: [
            "To authenticate users and manage accounts, we may process your username, email, sign-in times, device information and organization information configured by administrators.",
            "We collect information only to the extent necessary to provide the service.",
          ],
        },
        {
          heading: "2. How We Use Information",
          paragraphs: [
            "Information is used for authentication, permission checks, security auditing, unusual sign-in alerts and service improvement. It is not used for unrelated advertising.",
            "We do not disclose personal information to third parties unless authorized or required by law.",
          ],
        },
        {
          heading: "3. Storage and Protection",
          paragraphs: [
            "Data location and retention are determined by the instance operator. We protect data with access controls, encrypted transport and security auditing.",
          ],
        },
        {
          heading: "4. Your Rights",
          paragraphs: [
            "Contact the instance administrator to access, correct or delete information associated with you, or to learn how account data is processed.",
          ],
        },
      ],
    },
  },
  confidentiality: {
    "zh-CN": {
      kicker: "LEGAL · CONFIDENTIALITY",
      title: "数据保密协议",
      updated: "2026-11-15",
      intro: "本协议适用于你在使用 Pudans 过程中接触到的组织数据、账号信息和其他非公开信息。",
      sections: [
        {
          heading: "1. 保密范围",
          paragraphs: [
            "保密信息包括但不限于组织架构、成员信息、权限配置、审计日志、业务数据以及标注为内部或保密的内容。",
          ],
        },
        {
          heading: "2. 保密义务",
          paragraphs: [
            "你应当仅在履行工作职责所必需的范围内访问和使用保密信息，不得复制、传播或向无关人员披露。",
            "你应采取合理措施防止保密信息泄露，并在发现泄露风险时立即报告。",
          ],
        },
        {
          heading: "3. 例外情形",
          paragraphs: [
            "已经公开的信息、依法应当披露的信息，以及获得权利人明确授权的信息，不属于本协议限制范围。",
          ],
        },
        {
          heading: "4. 期限与责任",
          paragraphs: ["保密义务在账号终止后继续有效。违反本协议造成损失的，应依法承担相应责任。"],
        },
      ],
    },
    "en-US": {
      kicker: "LEGAL · CONFIDENTIALITY",
      title: "Data Confidentiality Agreement",
      updated: "2026-11-15",
      intro:
        "This agreement applies to organizational data, account information and other non-public information you encounter while using Pudans.",
      sections: [
        {
          heading: "1. Confidential Information",
          paragraphs: [
            "Confidential information includes organizational structure, member information, permission configuration, audit logs, business data and content marked internal or confidential.",
          ],
        },
        {
          heading: "2. Obligations",
          paragraphs: [
            "Access and use confidential information only as necessary for your responsibilities. Do not copy, distribute or disclose it to unauthorized people.",
            "Take reasonable measures to prevent disclosure and report suspected incidents immediately.",
          ],
        },
        {
          heading: "3. Exceptions",
          paragraphs: [
            "Information that is public, legally required to be disclosed, or explicitly authorized by the rights holder is outside the scope of this agreement.",
          ],
        },
        {
          heading: "4. Duration and Liability",
          paragraphs: [
            "Confidentiality obligations survive account termination. Liability for losses caused by a breach is governed by applicable law.",
          ],
        },
      ],
    },
  },
};

export function getLegalDocument(key: LegalDocumentKey, language: string): LegalDocument {
  return documents[key][language.startsWith("zh") ? "zh-CN" : "en-US"];
}
