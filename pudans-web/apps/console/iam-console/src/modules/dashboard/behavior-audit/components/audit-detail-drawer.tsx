"use client";

import { useEffect, useState } from "react";

import type { AuditRecordView, GeoLocation, Target } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { ArrowLeftOutlined } from "@ant-design/icons";
import type { TableProps } from "antd";
import { Button, Descriptions, Drawer, Table, Tag, Typography } from "antd";
import dayjs from "dayjs";

import { JsonTree } from "@/components/json-tree/json-tree";

import "./audit-detail-drawer.modules.less";

type AuditDetailDrawerProps = {
  onClose: () => void;
  open: boolean;
  record: AuditRecordView | null;
};

function formatTime(value?: string) {
  return value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";
}

function region(geo?: GeoLocation) {
  return [geo?.provinceName, geo?.cityName].filter(Boolean).join(" ") || "-";
}

function targetNames(targets?: Target[]) {
  const names = (targets ?? []).map((target) => target.name ?? target.id).filter(Boolean);
  return names.length ? names.join("、") : "-";
}

function parseValue(value?: string) {
  if (!value) {
    return undefined;
  }
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function toEventJson(record: AuditRecordView) {
  const data: Record<string, unknown> = { ...record };
  for (const key of ["eventContent", "eventParam", "eventResult"] as const) {
    const parsed = parseValue(record[key]);
    if (parsed !== undefined) {
      data[key] = parsed;
    }
  }
  return data;
}

export function AuditDetailDrawer({ onClose, open, record }: AuditDetailDrawerProps) {
  const [relatedRecords, setRelatedRecords] = useState<AuditRecordView[]>([]);
  const [recordView, setRecordView] = useState<AuditRecordView | null>(null);

  useEffect(() => {
    setRecordView(null);

    if (!open || !record) {
      setRelatedRecords([]);
      return;
    }

    if (!record.requestId) {
      setRelatedRecords([record]);
      return;
    }

    let cancelled = false;

    apiClient.iam.auditRecord
      .findByRequest({ requestId: record.requestId })
      .then((response) => {
        if (!cancelled) {
          const items = response.data.result ?? [];
          setRelatedRecords(items.length ? items : [record]);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRelatedRecords([record]);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, record]);

  const columns: TableProps<AuditRecordView>["columns"] = [
    {
      dataIndex: "eventTime",
      key: "eventTime",
      render: (value) => formatTime(value),
      title: "事件发生时间",
    },
    {
      dataIndex: "id",
      key: "id",
      render: (value) =>
        value ? <Typography.Text copyable>{value}</Typography.Text> : "-",
      title: "事件 ID",
    },
    {
      dataIndex: "targets",
      key: "targets",
      render: (targets) => targetNames(targets),
      title: "关联资源",
    },
    {
      dataIndex: "geoLocation",
      key: "geoLocation",
      render: (geo) => region(geo),
      title: "地域",
    },
    {
      key: "action",
      render: (_, item) => (
        <Button onClick={() => setRecordView(item)} type="link">
          事件记录
        </Button>
      ),
      title: "操作",
      width: 100,
    },
  ];

  const status = record?.eventStatus;
  const ip = record?.geoLocation?.ip;

  return (
    <Drawer
      destroyOnHidden
      footer={
        <div className="audit-detail__footer">
          <Button onClick={onClose} type="primary">
            关闭
          </Button>
        </div>
      }
      onClose={onClose}
      open={open}
      size={1000}
      title="查看详情"
    >
      {recordView ? (
        <div className="audit-detail">
          <button
            className="audit-detail__back"
            onClick={() => setRecordView(null)}
            type="button"
          >
            <ArrowLeftOutlined />
            返回列表
          </button>
          <h3 className="audit-detail__title">事件记录</h3>
          <JsonTree data={toEventJson(recordView)} />
        </div>
      ) : record ? (
        <div className="audit-detail">
          <section className="audit-detail__section">
            <h3 className="audit-detail__title">基本信息</h3>
            <Descriptions colon={false} column={2} size="small">
              <Descriptions.Item label="事件名称">
                {record.eventTypeName ?? record.eventType ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="事件分类">
                {record.eventCategory ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="操作者">{record.actorId ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="操作者类型">
                {record.actorType ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="事件源 IP">
                {ip ? <Typography.Text copyable>{ip}</Typography.Text> : "-"}
              </Descriptions.Item>
              <Descriptions.Item label="操作结果">
                {status ? (
                  <Tag color={status === "success" ? "success" : "error"}>
                    {record.eventStatusName ?? (status === "success" ? "成功" : "失败")}
                  </Tag>
                ) : (
                  "-"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="事件时间">
                {formatTime(record.eventTime ?? record.createTime)}
              </Descriptions.Item>
              <Descriptions.Item label="请求 ID">
                {record.requestId ? (
                  <Typography.Text copyable>{record.requestId}</Typography.Text>
                ) : (
                  "-"
                )}
              </Descriptions.Item>
            </Descriptions>
          </section>
          <section className="audit-detail__section">
            <h3 className="audit-detail__title">事件列表</h3>
            <Table
              columns={columns}
              dataSource={relatedRecords.length ? relatedRecords : [record]}
              pagination={{ pageSize: 5, showSizeChanger: false }}
              rowKey="id"
              size="small"
            />
          </section>
        </div>
      ) : null}
    </Drawer>
  );
}