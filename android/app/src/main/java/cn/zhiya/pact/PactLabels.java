package cn.zhiya.pact;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Pure presentation rules; state authority always remains on the server. */
public final class PactLabels {
    private PactLabels() {}

    public static String task(String value) {
        switch (value) {
            case "draft": return "草稿";
            case "proposed": return "待接受";
            case "active": return "待完成";
            case "submitted": return "待验收";
            case "achieved": return "已达成";
            case "declined": return "已拒绝";
            case "cancelled": return "已取消";
            case "expired": return "到期未达成";
            default: return value;
        }
    }

    public static String reward(String value) {
        switch (value) {
            case "unearned": return "达成后获得";
            case "pending": return "待兑现";
            case "offered":
            case "received": return "已兑现";
            default: return value;
        }
    }

    public static String agreement(String taskState, String rewardState) {
        return "achieved".equals(taskState) ? reward(rewardState) : task(taskState);
    }

    public static boolean settled(String rewardState) {
        return "received".equals(rewardState) || "offered".equals(rewardState);
    }

    public static String action(String value) {
        switch (value) {
            case "create": return "创建约定";
            case "edit": return "编辑条款";
            case "publish": return "提出约定";
            case "accept": return "接受约定";
            case "decline": return "拒绝约定";
            case "withdraw": return "撤回约定";
            case "progress": return "记录进度";
            case "submit": return "提交完成";
            case "review": return "验收结果";
            case "give": return "标记已兑现";
            case "receive": return "确认奖励已收到";
            case "dispute": return "反馈尚未收到";
            case "comment": return "补充说明";
            case "propose_amend": return "提出修改";
            case "propose_cancel": return "提出取消";
            case "resolve_proposal": return "回应协商";
            default: return value;
        }
    }

    public static String date(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) return "不限期";
        try { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)); }
        catch (RuntimeException e) { return value; }
    }

    public static String validateBaseUrl(String raw, boolean debug) {
        try {
            URI uri = new URI(raw.trim());
            String host = uri.getHost();
            if (uri.getPort() == 0 || uri.getPort() > 65535)
                throw new IllegalArgumentException("服务端口必须在 1–65535 之间");
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")))
                throw new IllegalArgumentException("服务地址须为完整域名与端口，不能包含路径或账号");
            boolean local = host.equals("10.0.2.2") || host.equals("127.0.0.1") || host.equals("localhost");
            if (!"https".equals(uri.getScheme()) && !(debug && local && "http".equals(uri.getScheme())))
                throw new IllegalArgumentException("服务地址必须使用 HTTPS；调试时仅本机地址允许 HTTP");
            return raw.trim().replaceAll("/+$", "");
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("服务地址格式不正确");
        }
    }
}
