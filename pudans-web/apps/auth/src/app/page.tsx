"use client";

import { Button, Card, Form, Input, Typography, theme } from "antd";

export default function AuthPage() {
  const { token } = theme.useToken();

  return (
    <main
      style={{
        display: "grid",
        minHeight: "100dvh",
        padding: 24,
        background: token.colorBgLayout,
        placeItems: "center",
      }}
    >
      <Card style={{ width: 360, boxShadow: token.boxShadowSecondary }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>
          登录 PUDANS
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          使用你的账号登录企业身份平台。
        </Typography.Paragraph>
        <Form layout="vertical" requiredMark={false}>
          <Form.Item
            label="账号"
            name="account"
            rules={[{ message: "请输入账号", required: true }]}
          >
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ message: "请输入密码", required: true }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button block htmlType="submit" type="primary">
            登录
          </Button>
        </Form>
      </Card>
    </main>
  );
}