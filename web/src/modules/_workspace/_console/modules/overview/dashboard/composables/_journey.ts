import type { AuditJourney, AuditJourneyStep, AuditRecord } from "@pudans-dev/api-client";

const JOURNEY_SESSION_GAP_MS = 30 * 60 * 1000;

type JourneyRecordKind = "action" | "page";

type JourneyTraceRecord = {
    kind: JourneyRecordKind;
    record: AuditRecord;
    sessionIndex: number;
    traceStart: number;
};

export type JourneyTraceSession = {
    actionCount: number;
    end: string;
    entryPath: string;
    exitPath: string;
    index: number;
    number: number;
    pageCount: number;
    records: JourneyTraceRecord[];
    start: string;
};

type TraceCandidate = {
    kind: JourneyRecordKind;
    record: AuditRecord;
    traceStart: number;
};

type TraceSessionDraft = {
    end: number;
    lastTimestamp: number;
    records: TraceCandidate[];
    start: number;
};

function isPageRecord(record: AuditRecord): boolean {
    if (record.resourceType === "Page") {
        return true;
    }

    return (
        record.operation === "NAVIGATION" && Boolean(record.requestPath) && !record.requestPath?.startsWith("/apis/")
    );
}

export function pagePath(record?: AuditRecord): string {
    if (record?.resourceType === "Page" && record.resourceName) {
        return record.resourceName;
    }

    return record?.requestPath ?? "—";
}

function traceStart(record: AuditRecord): number {
    const timestamp = record.timestamp ? Date.parse(record.timestamp) : 0;

    return timestamp - Math.max(0, record.durationMs ?? 0);
}

function timestampOf(record: AuditRecord): number {
    return record.timestamp ? Date.parse(record.timestamp) : 0;
}

function stepRecords(step: AuditJourneyStep): TraceCandidate[] {
    const records: TraceCandidate[] = [];

    if (step.page) {
        records.push({
            kind: "page",
            record: step.page,
            traceStart: traceStart(step.page),
        });
    }

    for (const action of step.actions ?? []) {
        records.push({
            kind: isPageRecord(action) ? "page" : "action",
            record: action,
            traceStart: traceStart(action),
        });
    }

    return records;
}

function buildSession(session: TraceSessionDraft, index: number): JourneyTraceSession {
    const pages = session.records.filter((item) => item.kind === "page");

    return {
        actionCount: session.records.length - pages.length,
        end: new Date(session.end).toISOString(),
        entryPath: pagePath(pages[0]?.record),
        exitPath: pagePath(pages[pages.length - 1]?.record),
        index,
        number: index + 1,
        pageCount: pages.length,
        records: session.records.map((item) => ({ ...item, sessionIndex: index })),
        start: new Date(session.start).toISOString(),
    };
}

export function buildJourneySessions(journey?: AuditJourney): JourneyTraceSession[] {
    if (!journey?.steps?.length) {
        return [];
    }

    const records = journey.steps
        .flatMap(stepRecords)
        .sort(
            (left, right) =>
                left.traceStart - right.traceStart ||
                timestampOf(left.record) - timestampOf(right.record) ||
                left.record.metadata.name.localeCompare(right.record.metadata.name),
        );
    const sessions: TraceSessionDraft[] = [];
    let current: TraceSessionDraft | undefined;

    for (const item of records) {
        const timestamp = timestampOf(item.record);
        if (!current || item.traceStart - current.lastTimestamp > JOURNEY_SESSION_GAP_MS) {
            current = {
                end: timestamp,
                lastTimestamp: timestamp,
                records: [],
                start: item.traceStart,
            };
            sessions.push(current);
        }

        current.records.push(item);
        current.end = Math.max(current.end, timestamp);
        current.lastTimestamp = timestamp;
    }

    return sessions.map(buildSession);
}
