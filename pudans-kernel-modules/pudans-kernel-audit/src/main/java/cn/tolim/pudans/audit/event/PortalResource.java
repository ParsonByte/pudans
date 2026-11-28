package cn.tolim.pudans.audit.event;

public class PortalResource extends Resource {

    public static final PortalResource MY_ACCOUNT_RESOURCE = new PortalResource(
            "pudans:event:resource:my_account", "我的账户");

    public static final PortalResource MY_APP_RESOURCE = new PortalResource(
            "pudans:event:resource:my_app", "我的应用");

    public static final PortalResource SESSION_RESOURCE = new PortalResource(
            "pudans:event:resource:session", "会话管理");

    public PortalResource(String code, String name) {
        super(code, name);
    }
}