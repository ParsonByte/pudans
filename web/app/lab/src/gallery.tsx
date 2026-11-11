import { useState, type ReactNode } from "react";
import { Box, Stack, Typography } from "@mui/material";
import {
  Accordion,
  Alert,
  Avatar,
  Badge,
  Breadcrumbs,
  Button,
  Card,
  Checkbox,
  Code,
  CodeBlock,
  ConfirmDialog,
  DescriptionList,
  Dialog,
  DropdownMenu,
  EmptyState,
  ErrorDetailDialog,
  ErrorState,
  FileDropZone,
  FileTypeIcon,
  FormField,
  HighlightText,
  Icon,
  Input,
  LanguageSwitcher,
  Logo,
  OAuthAppCard,
  Pagination,
  PoweredBy,
  Progress,
  ProgressSteps,
  RelativeTime,
  ResponsiveTabs,
  SearchInput,
  Select,
  SelectDialog,
  SettingItem,
  SizeInput,
  Skeleton,
  SplitPane,
  StatCard,
  Switch,
  Table,
  Tabs,
  ThemeSwitch,
  Tooltip,
  useToast,
  type DropdownMenuItem,
  type TabItem,
  type TableColumn,
} from "@pudans/components";

const SELECT_OPTIONS = [
  { value: "one", label: "Option one" },
  { value: "two", label: "Option two" },
];

const TABLE_ROWS = [
  { id: "1", name: "Ada Lovelace", role: "Super admin" },
  { id: "2", name: "Grace Hopper", role: "Member" },
];

const TABLE_COLUMNS: TableColumn<(typeof TABLE_ROWS)[number]>[] = [
  { key: "name", header: "Name" },
  { key: "role", header: "Role" },
];

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section style={{ marginBottom: 32 }}>
      <Typography variant="h6" gutterBottom>
        {title}
      </Typography>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: "wrap", alignItems: "center" }}>
        {children}
      </Stack>
    </section>
  );
}

function ToastDemo() {
  const toast = useToast();
  return <Button onClick={() => toast.success("Saved")}>Show toast</Button>;
}

export function Gallery() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [selectOpen, setSelectOpen] = useState(false);
  const [tab, setTab] = useState("members");
  const [size, setSize] = useState(1024 * 1024);
  const [input, setInput] = useState("");
  const [search, setSearch] = useState("cloud");
  const [checked, setChecked] = useState(true);
  const [page, setPage] = useState(2);
  const [dropFiles, setDropFiles] = useState<File[]>([]);

  const tabs: TabItem[] = [
    { key: "members", label: "Members" },
    { key: "roles", label: "Roles" },
  ];
  const menuItems: DropdownMenuItem[] = [
    { key: "edit", label: "Edit" },
    { key: "duplicate", label: "Duplicate" },
    { key: "delete", label: "Delete", danger: true, divider: true },
  ];

  return (
    <Box sx={{ p: 4, maxWidth: 1280, mx: "auto" }}>
      <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 4 }}>
        <Logo name="pudans" height={32} />
        <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
          <ThemeSwitch />
          <LanguageSwitcher
            compact
            languages={[{ code: "en-US", label: "English" }, { code: "zh-CN", label: "中文" }]}
            value="en-US"
            onChange={() => undefined}
          />
        </Stack>
      </Stack>

      <Section title="Buttons">
        <Button>Primary</Button>
        <Button variant="outline">Outline</Button>
        <Button variant="ghost">Ghost</Button>
        <Button variant="danger">Danger</Button>
        <Button loading>Saving</Button>
        <Button disabled>Disabled</Button>
        <Button size="sm" startIcon={<Icon name="cr:Add" />}>
          Small
        </Button>
        <Button size="lg">Large</Button>
      </Section>

      <Section title="Form controls">
        <Box sx={{ width: 260 }}>
          <Input label="Display name" placeholder="Ada Lovelace" helperText="Shown to other users" />
        </Box>
        <Box sx={{ width: 260 }}>
          <Input label="Email" error helperText="Enter a valid email" defaultValue="not-an-email" />
        </Box>
        <Box sx={{ width: 200 }}>
          <Select label="Role" value="one" onChange={() => undefined} options={SELECT_OPTIONS} />
        </Box>
        <SizeInput value={size} onChange={setSize} label="Quota" />
        <FormField label="API key" description="Generated once" required>
          <Input placeholder="pk_..." value={input} onChange={(event) => setInput(event.target.value)} />
        </FormField>
        <Checkbox checked={checked} onChange={(event) => setChecked(event.target.checked)} label="Enable" />
      </Section>

      <Section title="Feedback">
        <Alert severity="info">Information message</Alert>
        <Alert severity="success" title="Saved">
          The record was saved.
        </Alert>
        <Alert severity="warning">Storage is almost full</Alert>
        <Alert severity="error">Request failed</Alert>
        <Badge tone="brand">Brand</Badge>
        <Badge tone="success" dot>
          Active
        </Badge>
        <Box sx={{ width: 220 }}>
          <Progress value={42} showValue label="Uploading" />
        </Box>
        <Progress variant="circular" value={68} />
        <Skeleton variant="text" width={160} />
        <Skeleton variant="circular" width={40} height={40} />
        <ToastDemo />
      </Section>

      <Section title="Status">
        <EmptyState title="No files" description="Drop files here to start uploading." variant="file-drop" />
        <ErrorState error={new Error("Connection timeout")} onRetry={() => undefined} />
      </Section>

      <Section title="Data display">
        <Box sx={{ minWidth: 380 }}>
          <Table columns={TABLE_COLUMNS} data={TABLE_ROWS} rowKey={(row) => row.id} zebra />
        </Box>
        <Pagination page={page} total={120} pageSize={20} onPageChange={setPage} />
        <DescriptionList
          items={[
            { key: "owner", label: "Owner", value: "Ada Lovelace" },
            { key: "size", label: "Size", value: "12.4 MB" },
          ]}
        />
        <Breadcrumbs items={[{ label: "Console", href: "/" }, { label: "Users" }]} />
        <RelativeTime datetime={Date.now() - 3600_000} />
        <FileTypeIcon name="report.pdf" />
        <FileTypeIcon name="photo.png" />
        <FileTypeIcon name="archive.zip" />
        <StatCard label="Total users" value="1,280" hint="Registered accounts" />
        <Card>Card surface</Card>
      </Section>

      <Section title="Navigation and overlays">
        <Tabs items={tabs} value={tab} onValueChange={setTab} />
        <ResponsiveTabs items={tabs} value={tab} onChange={setTab} />
        <DropdownMenu trigger={<Button variant="outline">Menu</Button>} items={menuItems} />
        <Tooltip title="Delayed tooltip">
          <Button variant="ghost">Tooltip</Button>
        </Tooltip>
        <Button onClick={() => setDialogOpen(true)}>Open dialog</Button>
        <Button onClick={() => setConfirmOpen(true)}>Confirm</Button>
        <Button onClick={() => setSelectOpen(true)}>Select</Button>
        <ProgressSteps
          current={1}
          steps={[
            { key: "one", label: "Verify" },
            { key: "two", label: "Configure" },
            { key: "three", label: "Done" },
          ]}
        />
        <SettingItem title="Two-factor authentication" description="Require a code at sign-in" control={<Switch defaultChecked />} />
        <Accordion title="Advanced options">
          <Typography variant="body2">Accordion body follows legacy MUI spacing.</Typography>
        </Accordion>
      </Section>

      <Section title="Brand and identity">
        <PoweredBy href="https://example.com" />
        <Avatar name="Ada Lovelace" size="md" />
        <OAuthAppCard name="GitHub" description="Read repositories" icon="cr:LinkOutlined" />
        <Code>npm run build</Code>
      </Section>

      <Section title="Motion and utilities">
        <HighlightText text="cloudreve frontend migration" searchWords={["cloudreve"]} />
        <SearchInput value={search} onChange={setSearch} shortcut="mod+k" />
        <Box sx={{ width: 360 }}>
          <CodeBlock code={'const answer = 42;\nconsole.log(answer);'} language="ts" />
        </Box>
        <Box sx={{ width: 320 }}>
          <FileDropZone onFiles={setDropFiles} description={dropFiles.length > 0 ? dropFiles.length + " file(s)" : "or click to browse"} />
        </Box>
        <Box sx={{ width: 560, height: 200, border: "1px solid", borderColor: "divider" }}>
          <SplitPane initialSize={220}>
            <Typography variant="body2" sx={{ p: 2 }}>
              Sidebar
            </Typography>
            <Typography variant="body2" sx={{ p: 2 }}>
              Content
            </Typography>
          </SplitPane>
        </Box>
      </Section>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title="Dialog title" description="Legacy Grow transition, radius 12."
        footer={<Button onClick={() => setDialogOpen(false)}>Close</Button>}>
        Dialog content
      </Dialog>
      <ConfirmDialog open={confirmOpen} title="Delete record?" description="This action cannot be undone." tone="danger"
        onConfirm={() => setConfirmOpen(false)} onCancel={() => setConfirmOpen(false)} />
      <SelectDialog open={selectOpen} title="Select an option" options={SELECT_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
        onSelect={() => setSelectOpen(false)} onCancel={() => setSelectOpen(false)} />
      <ErrorDetailDialog open={false} onClose={() => undefined} message="Boom" requestId="req-123" />
    </Box>
  );
}
