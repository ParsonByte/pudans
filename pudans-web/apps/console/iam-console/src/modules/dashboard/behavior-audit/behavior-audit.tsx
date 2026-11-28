"use client";

import { useCallback, useEffect, useState } from "react";

import type { AuditEventTypeView, AuditQuery, AuditRecordView } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import {
  ColumnHeightOutlined,
  DownOutlined,
  ReloadOutlined,
  SettingOutlined,
  UpOutlined,
} from "@ant-design/icons";
import type { TableProps } from "antd";
import {
  Badge,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Dropdown,
  Form,
  Input,
  Popover,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  notification,
} from "antd";
import dayjs from "dayjs";
import type { Dayjs } from "dayjs";

import { AuditDetailDrawer } from "./components/audit-detail-drawer";

import "./behavior-audit.modules.less";

const { RangePicker } = DatePicker;

const COLUMN_OPTIONS = [
  { label: "事件类型", value: "eventType" },
  { label: "操作者", value: "actorId" },
  { label: "客户端 IP", value: "geoLocation" },
  { label: "操作系统", value: "platform" },
  { label: "浏览器", value: "browser" },
  { label: "事件时间", value: "eventTime" },
  { label: "事件状态", value: "eventStatus" },
  { label: "操作", value: "action" },
];

const DENSITY_OPTIONS = [
  { key: "large", label: "默认" },
  { key: "middle", label: "中等" },
  { key: "small", label: "紧凑" },
];

type SearchValues = {
  actorId?: string;
  eventStatus?: string;
  eventTypes?: string[];
  range?: [Dayjs, Dayjs];
};

type Filters = {
  actorId?: string;
  endTime?: string;
  eventStatus?: string;
  eventTypes?: string[];
  startTime?: string;
};

type EventTypeOption = {
  label: string;
  options: { label: string; value: string }[];
};

function buildEventTypeOptions(types: AuditEventTypeView[]) {
  const groups = new Map<string, EventTypeOption>();

  for (const type of types) {
    if (!type.code) {
      continue;
    }
    const key = type.category ?? type.categoryName ?? "other";
    const group = groups.get(key) ?? {
      label: type.categoryName ?? type.category ?? "其他",
      options: [],
    };
    group.options.push({ label: type.name ?? type.code, value: type.code });
    groups.set(key, group);
  }

  return [...groups.values()];
}

function formatTime(value?: string) {
  return value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";
}

export function BehaviorAudit() {
  const [form] = Form.useForm<SearchValues>();
  const [notificationApi, notificationHolder] = notification.useNotification();
  const [records, setRecords] = useState<AuditRecordView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [sort, setSort] = useState<{ field: string; order: "asc" | "desc" }>({
    field: "eventTime",
    order: "desc",
  });
  const [filters, setFilters] = useState<Filters>({});
  const [eventTypeOptions, setEventTypeOptions] = useState<EventTypeOption[]>([]);
  const [expanded, setExpanded] = useState(false);
  const [tableSize, setTableSize] = useState<"large" | "middle" | "small">("large");
  const [visibleColumns, setVisibleColumns] = useState<string[]>(
    COLUMN_OPTIONS.map((option) => option.value)
  );
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRecord, setDetailRecord] = useState<AuditRecordView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);

    try {
      const query: AuditQuery = {
        actorId: filters.actorId,
        endTime: filters.endTime,
        eventStatus: filters.eventStatus,
        eventTypes: filters.eventTypes,
        page,
        size,
        sortBy: sort.field,
        sortDirection: sort.order,
        startTime: filters.startTime,
      };
      const response = await apiClient.iam.auditRecord.search({ query });

      setRecords(response.data.result?.items ?? []);
      setTotal(response.data.result?.total ?? 0);
    } catch {
      setRecords([]);
      setTotal(0);
      notificationApi.error({
        description: "请稍后重试，或确认 IAM 服务是否可用。",
        key: "behavior-audit-load-error",
        title: "审计记录加载失败",
      });
    } finally {
      setLoading(false);
    }
  }, [filters, notificationApi, page, size, sort]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;

    apiClient.iam.auditRecord
      .eventTypes()
      .then((response) => {
        if (!cancelled) {
          setEventTypeOptions(buildEventTypeOptions(response.data.result ?? []));
        }
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, []);

  const submit = (values: SearchValues) => {
    setFilters({
      actorId: values.actorId?.trim() || undefined,
      endTime: values.range?.[1]?.toISOString(),
      eventStatus: values.eventStatus,
      eventTypes: values.eventTypes,
      startTime: values.range?.[0]?.toISOString(),
    });
    setPage(1);
  };

  const reset = () => {
    form.resetFields();
    setFilters({});
    setPage(1);
  };

  const openDetail = (record: AuditRecordView) => {
    setDetailRecord(record);
    setDetailOpen(true);
  };

  const columns: TableProps<AuditRecordView>["columns"] = [
    {
      dataIndex: "eventType",
      ellipsis: true,
      key: "eventType",
      render: (_, record) => record.eventTypeName ?? record.eventType ?? "-",
      title: "事件类型",
      width: 150,
    },
    {
      dataIndex: "actorId",
      ellipsis: true,
      key: "actorId",
      render: (_, record) => record.actorId ?? "-",
      title: "操作者",
      width: 150,
    },
    {
      dataIndex: "geoLocation",
      key: "geoLocation",
      render: (_, record) => {
        const ip = record.geoLocation?.ip;
        return <Badge status={ip ? "success" : "default"} text={ip ?? "-"} />;
      },
      title: "客户端 IP",
      width: 150,
    },
    {
      dataIndex: "userAgent",
      key: "platform",
      render: (_, record) => record.userAgent?.platform ?? "-",
      title: "操作系统",
      width: 120,
    },
    {
      dataIndex: "userAgent",
      key: "browser",
      render: (_, record) => record.userAgent?.browser ?? "-",
      title: "浏览器",
      width: 120,
    },
    {
      dataIndex: "eventTime",
      defaultSortOrder: "descend",
      key: "eventTime",
      render: (value) => formatTime(value),
      sorter: true,
      title: "事件时间",
      width: 180,
    },
    {
      dataIndex: "eventStatus",
      key: "eventStatus",
      render: (_, record) => {
        const status = record.eventStatus;
        if (!status) {
          return "-";
        }
        return (
          <Tag color={status === "success" ? "success" : "error"}>
            {record.eventStatusName ?? (status === "success" ? "成功" : "失败")}
          </Tag>
        );
      },
      title: "事件状态",
      width: 110,
    },
    {
      fixed: "right",
      key: "action",
      render: (_, record) => (
        <Button onClick={() => openDetail(record)} type="link">
          查看详情
        </Button>
      ),
      title: "操作",
      width: 100,
    },
  ];

  const handleTableChange: TableProps<AuditRecordView>["onChange"] = (
    pagination,
    _tableFilters,
    sorter,
    extra
  ) => {
    if (extra.action === "paginate") {
      setPage(pagination.current ?? 1);
      setSize(pagination.pageSize ?? 10);
    }
    if (extra.action === "sort" && !Array.isArray(sorter)) {
      setSort({
        field: String(sorter.field ?? "eventTime"),
        order: sorter.order === "ascend" ? "asc" : "desc",
      });
      setPage(1);
    }
  };

  const visibleColumnDefs = columns.filter((column) =>
    visibleColumns.includes(String(column.key))
  );

  const primaryFields = (
    <>
      <Form.Item label="事件类型" name="eventTypes">
        <Select
          allowClear
          maxTagCount="responsive"
          mode="multiple"
          options={eventTypeOptions}
          placeholder="请选择事件类型"
          showSearch
          style={{ width: "100%" }}
        />
      </Form.Item>
      <Form.Item label="操作者" name="actorId">
        <Input allowClear placeholder="请输入用户名" />
      </Form.Item>
      <Form.Item label="事件时间" name="range">
        <RangePicker showTime style={{ width: "100%" }} />
      </Form.Item>
    </>
  );

  const statusField = (
    <Form.Item label="事件状态" name="eventStatus">
      <Select
        allowClear
        options={[
          { label: "成功", value: "success" },
          { label: "失败", value: "fail" },
        ]}
        placeholder="请选择"
        style={{ width: "100%" }}
      />
    </Form.Item>
  );

  const searchActions = (
    <>
      <Button onClick={reset}>重置</Button>
      <Button htmlType="submit" type="primary">
        查询
      </Button>
      <Button
        icon={expanded ? <UpOutlined /> : <DownOutlined />}
        onClick={() => setExpanded((current) => !current)}
        type="text"
      >
        {expanded ? "收起" : "展开"}
      </Button>
    </>
  );

  return (
    <Card>
      {notificationHolder}
      <div className="behavior-audit__search-wrap">
        <Form
          className="behavior-audit__search"
          form={form}
          labelAlign="right"
          labelCol={{ flex: "90px" }}
          onFinish={submit}
          wrapperCol={{ flex: 1 }}
        >
          {expanded ? (
            <div className="behavior-audit__search-grid">
              {primaryFields}
              {statusField}
              <div className="behavior-audit__search-actions">{searchActions}</div>
            </div>
          ) : (
            <div className="behavior-audit__search-row">
              {primaryFields}
              <div className="behavior-audit__search-actions">{searchActions}</div>
            </div>
          )}
        </Form>
      </div>
      <div className="behavior-audit__toolbar">
        <Space size={4}>
          <Tooltip title="刷新">
            <Button icon={<ReloadOutlined />} onClick={() => void load()} type="text" />
          </Tooltip>
          <Dropdown
            menu={{
              items: DENSITY_OPTIONS,
              onClick: ({ key }) => setTableSize(key as "large" | "middle" | "small"),
              selectable: true,
              selectedKeys: [tableSize],
            }}
            trigger={["click"]}
          >
            <Tooltip title="密度">
              <Button icon={<ColumnHeightOutlined />} type="text" />
            </Tooltip>
          </Dropdown>
          <Popover
            content={
              <Checkbox.Group
                className="behavior-audit__columns"
                onChange={(values) => setVisibleColumns(values.map(String))}
                options={COLUMN_OPTIONS}
                value={visibleColumns}
              />
            }
            placement="bottomRight"
            trigger="click"
          >
            <Tooltip title="列设置">
              <Button icon={<SettingOutlined />} type="text" />
            </Tooltip>
          </Popover>
        </Space>
      </div>
      <Table
        columns={visibleColumnDefs}
        dataSource={records}
        loading={loading}
        onChange={handleTableChange}
        pagination={{
          current: page,
          pageSize: size,
          showQuickJumper: true,
          showSizeChanger: true,
          showTotal: (value, range) => `第 ${range[0]}-${range[1]} 条/总共 ${value} 条`,
          total,
        }}
        rowKey="id"
        scroll={{ x: 1100 }}
        size={tableSize}
      />
      <AuditDetailDrawer
        onClose={() => setDetailOpen(false)}
        open={detailOpen}
        record={detailRecord}
      />
    </Card>
  );
}