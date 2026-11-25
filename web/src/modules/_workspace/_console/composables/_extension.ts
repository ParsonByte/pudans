// 扩展资源共用的元数据与列表结构，供控制台各资源模块复用。
export type ExtensionMeta = {
    annotations?: Record<string, string>;
    creationTimestamp?: string;
    labels?: Record<string, string>;
    name: string;
    version?: number;
};

export type ExtensionList<T> = {
    items?: T[];
    page?: number;
    size?: number;
    total?: number;
};
