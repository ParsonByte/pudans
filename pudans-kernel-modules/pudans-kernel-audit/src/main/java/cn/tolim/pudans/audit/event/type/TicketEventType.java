package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.ConsoleResource;
import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

public class TicketEventType {

    public static final Type TICKET_ISSUED               = new Type("pudans:event:ticket:issued",
        "签发SSO票据", ConsoleResource.SSO_TICKET_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type TICKET_EXCHANGED            = new Type("pudans:event:ticket:exchanged",
        "SSO票据换取会话", ConsoleResource.SSO_TICKET_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type TICKET_REJECTED             = new Type("pudans:event:ticket:rejected",
        "SSO票据拒绝", ConsoleResource.SSO_TICKET_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type BACKCHANNEL_LOGOUT_SENT     = new Type(
        "pudans:event:ticket:backchannel_logout_sent", "发送back-channel登出通知",
        ConsoleResource.SSO_TICKET_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type BACKCHANNEL_LOGOUT_RECEIVED = new Type(
        "pudans:event:ticket:backchannel_logout_received", "接收back-channel登出通知",
        ConsoleResource.SSO_TICKET_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

    public static final Type BACKCHANNEL_LOGOUT_FAILED   = new Type(
        "pudans:event:ticket:backchannel_logout_failed", "back-channel登出通知失败",
        ConsoleResource.SSO_TICKET_RESOURCE, List.of(UserType.USER, UserType.ADMIN));

}