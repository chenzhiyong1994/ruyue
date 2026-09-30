package cn.zhiya.pact;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.function.Supplier;

/** Private relationships and invitations, with all writes confirmed by the service. */
final class RelationshipScreens {
    private final Activity activity;
    private final ApiClient api;
    private final Supplier<JSONObject> state;
    private final Supplier<String> relationshipFilter;
    private final Runnable chooseRelationship, refresh, expired;

    RelationshipScreens(Activity activity, ApiClient api, Supplier<JSONObject> state, Supplier<String> relationshipFilter, Runnable chooseRelationship, Runnable refresh, Runnable expired) {
        this.activity = activity; this.api = api; this.state = state;
        this.relationshipFilter = relationshipFilter;
        this.chooseRelationship = chooseRelationship;
        this.refresh = refresh; this.expired = expired;
    }

    void render(LinearLayout parent) {
        JSONObject current = state.get();
        JSONArray relationships = current == null ? null : current.optJSONArray("relationships");
        LinearLayout toolbar = PactUi.row(activity);
        parent.addView(toolbar);
        toolbar.addView(PactUi.compactButton(activity, "创建关系", true, v -> create()));
        LinearLayout.LayoutParams joinParams=new LinearLayout.LayoutParams(-2,-2);joinParams.leftMargin=PactUi.dp(activity,12);
        toolbar.addView(PactUi.textButton(activity, "加入关系", v -> join()),joinParams);
        if ((relationships != null && relationships.length() > 1) || !relationshipFilter.get().isEmpty()) {
            View scope = PactUi.textButton(activity, "查看范围", v -> chooseRelationship.run());
            if (compactLayout()) {
                parent.addView(scope);
            } else {
                toolbar.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
                toolbar.addView(scope);
            }
        }
        String scopeName = "";
        for (int i = 0; relationships != null && i < relationships.length(); i++) {
            JSONObject row = relationships.optJSONObject(i);
            if (row != null && row.optString("id").equals(relationshipFilter.get())) scopeName = row.optString("name");
        }
        if (!scopeName.isEmpty()) {
            PactUi.gap(activity, parent, 8);
            muted(parent, "当前查看：" + scopeName, 13);
        }
        PactUi.gap(activity, parent, 16);
        if (relationships == null || relationships.length() == 0) {
            LinearLayout empty = PactUi.card(activity, parent);
            PactMood.emptyIllustration(activity, empty, false);
            empty.addView(PactUi.title(activity, "还没有关系", 19));
            PactUi.gap(activity, empty, 8);
            muted(empty, "创建关系邀请对方，或使用邀请码加入。双方使用各自的账号。", 14);
            PactUi.gap(activity, empty, 8);
            muted(empty, "孩子没有账号时，可建立家长代记关系；操作会标明代记来源。", 14);
            return;
        }
        int shown = 0;
        for (int i = relationships.length() - 1; i >= 0; i--) {
            JSONObject relationship = relationships.optJSONObject(i);
            if (relationship != null && (relationshipFilter.get().isEmpty()
                    || relationshipFilter.get().equals(relationship.optString("id")))) {
                card(parent, relationship);
                shown++;
            }
        }
        if (shown == 0) muted(parent, "当前范围下没有关系，可通过“查看范围”重新选择。", 15);
    }

    private void card(LinearLayout parent, JSONObject relationship) {
        LinearLayout card = PactUi.card(activity, parent);
        card.setBackground(PactUi.softSurface(activity,"proxy".equals(relationship.optString("mode")),20));
        String status = relationship.optString("status");
        boolean proxy = "proxy".equals(relationship.optString("mode"));
        LinearLayout heading = PactUi.row(activity);
        heading.setGravity(Gravity.TOP);
        card.addView(heading);
        TextView avatar = PactUi.avatar(activity, relationship.optString("name"), 40);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(PactUi.dp(activity, 40), PactUi.dp(activity, 40));
        avatarParams.rightMargin = PactUi.dp(activity, 12);
        heading.addView(avatar, avatarParams);
        LinearLayout identity = PactUi.column(activity);
        heading.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        identity.addView(PactUi.title(activity, relationship.optString("name"), 18));
        PactUi.gap(activity, identity, 4);
        muted(identity, typeLabel(relationship.optString("type")) + " · "
                + (proxy ? "家长代记" : "双方账号"), 12);
        PactUi.gap(activity, identity, 8);
        TextView statusView = PactUi.badge(activity, statusLabel(status));
        if ("ended".equals(status)) {
            statusView.setTextColor(PactUi.MUTED);
            statusView.setBackground(PactUi.background(PactUi.LAVENDER, PactUi.dp(activity, 12)));
        }
        identity.addView(statusView);
        if (!"ended".equals(status)) {
            View more = PactUi.iconButton(activity, "关系操作：" + relationship.optString("name"), "more",
                    view -> showActions(view, relationship));
            heading.addView(more);
        }
        PactUi.gap(activity, card, 16);
        PactUi.divider(activity, card);
        PactUi.gap(activity, card, 12);
        text(card, memberSummary(relationship), 14);
        PactUi.gap(activity, card, 8);
        muted(card, recordSummary(relationship), 12);
        if ("ended".equals(status)) {
            PactUi.gap(activity, card, 12);
            muted(card, "关系已解除，记录保留；已达成的奖励仍可结清。", 13);
            return;
        }
        if (proxy) {
            PactUi.gap(activity, card, 12);
            muted(card, "家长代记：接受与提交会记录实际操作人。", 12);
        }
        String code = relationship.isNull("inviteCode") ? "" : relationship.optString("inviteCode");
        if ("inviting".equals(status) && me().equals(relationship.optString("ownerId"))) {
            PactUi.gap(activity, card, 16);
            PactUi.divider(activity, card);
            PactUi.gap(activity, card, 12);
            LinearLayout invitation = PactUi.column(activity);
            card.addView(invitation);
            if (!code.isEmpty()) {
                TextView invitationCode = PactUi.text(activity, "邀请码：" + code, 14, PactUi.INK);
                invitationCode.setTypeface(Typeface.MONOSPACE);
                invitationCode.setTextIsSelectable(true);
                invitation.addView(invitationCode);
                PactUi.gap(activity, invitation, 4);
                muted(invitation, "邀请有效期：" + PactLabels.date(relationship.optString("inviteExpiresAt")), 13);
            }
            if (!code.isEmpty()) {
                PactUi.gap(activity, invitation, 4);
                invitation.addView(PactUi.textButton(activity, "复制邀请码", v -> copy(code)));
            }
        }
    }

    private boolean compactLayout() {
        return activity.getResources().getConfiguration().screenWidthDp < 360
                || activity.getResources().getConfiguration().fontScale >= 1.3f;
    }

    private void showActions(View anchor, JSONObject relationship) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, "修改称呼");
        if ("inviting".equals(relationship.optString("status")) && me().equals(relationship.optString("ownerId"))) {
            menu.getMenu().add(0, 2, 1, "重新生成邀请");
        }
        menu.getMenu().add(0, 3, 2, "解除关系");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) rename(relationship);
            else if (item.getItemId() == 2) renew(relationship);
            else if (item.getItemId() == 3) end(relationship);
            return true;
        });
        menu.show();
    }

    private String memberSummary(JSONObject relationship) {
        StringBuilder summary = new StringBuilder();
        JSONArray members = relationship.optJSONArray("members");
        for (int i = 0; members != null && i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null) continue;
            if (summary.length() > 0) summary.append(" · ");
            summary.append(member.optString("name"));
            summary.append("child".equals(member.optString("kind")) ? "（家长代记）"
                    : me().equals(member.optString("id")) ? "（我）" : "（对方）");
        }
        return summary.toString();
    }

    private String recordSummary(JSONObject relationship) {
        int total = 0, active = 0, rewards = 0;
        JSONObject current = state.get();
        JSONArray agreements = current == null ? null : current.optJSONArray("agreements");
        for (int i = 0; agreements != null && i < agreements.length(); i++) {
            JSONObject item = agreements.optJSONObject(i);
            if (item == null || !relationship.optString("id").equals(item.optString("relationshipId"))) continue;
            total++;
            if ("active".equals(item.optString("taskState"))) active++;
            String reward = item.optString("rewardState");
            if ("achieved".equals(item.optString("taskState")) && "pending".equals(reward)) rewards++;
        }
        return "约定 " + total + ("ended".equals(relationship.optString("status")) ? "" : " · 待完成 " + active)
                + " · 待兑现 " + rewards;
    }

    private LinearLayout note(LinearLayout parent, String title, String description, boolean lavender) {
        LinearLayout box = PactUi.column(activity);
        box.setPadding(PactUi.dp(activity, 14), PactUi.dp(activity, 12), PactUi.dp(activity, 14), PactUi.dp(activity, 12));
        box.setBackground(PactUi.background(lavender ? PactUi.LAVENDER : PactUi.BLUSH, PactUi.dp(activity, 16)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = PactUi.dp(activity, 10);
        params.bottomMargin = PactUi.dp(activity, 6);
        parent.addView(box, params);
        box.addView(PactUi.title(activity, title, 16));
        PactUi.gap(activity, box, 8);
        muted(box, description, 14);
        return box;
    }

    private void create() {
        PactForm form = new PactForm(activity, "创建关系");
        EditText name = PactUi.input(activity, form.fields, "关系名称", "", false);
        Spinner type = spinner(form.fields, "关系类型", new String[]{"亲子", "情侣", "自定义"});
        Spinner mode = spinner(form.fields, "关系模式", new String[]{"双方账号", "家长代记"});
        LinearLayout childFields = PactUi.column(activity);
        form.fields.addView(childFields);
        EditText child = PactUi.input(activity, childFields, "孩子称呼", "", false);
        childFields.setVisibility(View.GONE);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                childFields.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }
        });
        text(form.fields, "双方账号需要对方加入后生效。家长代记会明确记录实际操作人。", 14);
        form.show("确认创建", v -> {
            if (name.getText().toString().trim().isEmpty()) { form.fail("请填写关系名称。"); return; }
            boolean proxy = mode.getSelectedItemPosition() == 1;
            if (proxy && type.getSelectedItemPosition() != 0) { form.fail("家长代记只适用于亲子关系。"); return; }
            if (proxy && child.getText().toString().trim().isEmpty()) { form.fail("请填写孩子称呼。"); return; }
            try {
                String[] types = {"parent_child", "couple", "custom"};
                JSONObject body = new JSONObject().put("name", name.getText().toString())
                        .put("type", types[type.getSelectedItemPosition()]).put("mode", proxy ? "proxy" : "shared");
                if (proxy) body.put("childName", child.getText().toString());
                write(form, "/v1/relationships", body);
            } catch (org.json.JSONException error) { form.fail("请检查填写内容后重试。"); }
        });
    }

    private void join() {
        PactForm form = new PactForm(activity, "加入关系");
        EditText code = PactUi.input(activity, form.fields, "邀请码", "", false);
        text(form.fields, "填写对方发送的邀请码。加入后，仅你们两人可以查看这段关系的约定。", 14);
        form.show("确认加入", v -> {
            if (code.getText().toString().trim().isEmpty()) { form.fail("请填写邀请码。"); return; }
            try { write(form, "/v1/relationships/join", new JSONObject().put("inviteCode", code.getText().toString().trim())); }
            catch (org.json.JSONException error) { form.fail("请检查邀请码后重试。"); }
        });
    }

    private void rename(JSONObject relationship) {
        PactForm form = new PactForm(activity, "修改称呼");
        muted(form.fields, "所属关系：" + relationship.optString("name"), 14);
        String current = "";
        JSONArray members = relationship.optJSONArray("members");
        for (int i = 0; members != null && i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member != null && me().equals(member.optString("id"))) current = member.optString("name");
        }
        EditText name = PactUi.input(activity, form.fields, "我的称呼", current, false);
        text(form.fields, "只修改你在这段关系中的称呼。", 14);
        form.show("确认修改", v -> {
            if (name.getText().toString().trim().isEmpty()) { form.fail("请填写你的称呼。"); return; }
            try { write(form, path(relationship), action(relationship, "rename").put("name", name.getText().toString())); }
            catch (org.json.JSONException error) { form.fail("请检查称呼后重试。"); }
        });
    }

    private void renew(JSONObject relationship) {
        PactForm form = new PactForm(activity, "重新生成邀请");
        form.fields.addView(PactUi.badge(activity, "只更新这段关系的邀请"));
        text(form.fields, "重新生成后，原邀请码立即失效。请把新的邀请码交给对方。", 16);
        form.show("确认重新生成", v -> submitAction(form, relationship, "renew_invite"));
    }

    private void end(JSONObject relationship) {
        PactForm form = new PactForm(activity, "确认解除关系");
        text(form.fields, "你将解除「" + relationship.optString("name") + "」。双方将停止新增约定与普通互动。", 17);
        note(form.fields, "已有记录与奖励保留", "已经达成的奖励不会被取消，仍需完成兑现。", true);
        form.show("确认解除关系", v -> submitAction(form, relationship, "end"));
    }

    private void submitAction(PactForm form, JSONObject relationship, String name) {
        try { write(form, path(relationship), action(relationship, name)); }
        catch (org.json.JSONException error) { form.fail("关系信息已变化，请刷新后重试。"); }
    }

    private void write(PactForm form, String path, JSONObject body) {
        if (form.busy) return;
        try {
            JSONObject request = form.request.prepare(body);
            form.busy(true);
            api.async(() -> api.call(path, request), new ApiClient.Callback<JSONObject>() {
                public void success(JSONObject result) {
                    if (activity.isDestroyed()) return;
                    form.dialog.dismiss(); refresh.run();
                }
                public void failure(ApiClient.ApiError error) {
                    if (activity.isDestroyed()) return;
                    if (error.status == 401) { form.dialog.dismiss(); expired.run(); }
                    else form.fail(error.getMessage());
                }
            });
        } catch (org.json.JSONException error) { form.fail("请检查填写内容后重试。"); }
    }

    private Spinner spinner(LinearLayout parent, String label, String[] choices) {
        muted(parent, label, 13);
        PactUi.gap(activity, parent, 8);
        Spinner spinner = new Spinner(activity); spinner.setContentDescription(label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        PactUi.styleSpinner(activity, spinner);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = PactUi.dp(activity, 20);
        parent.addView(spinner, params); return spinner;
    }

    private void copy(String code) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("关系邀请码", code)); Toast.makeText(activity, "邀请码已复制", Toast.LENGTH_SHORT).show(); }
    }
    private String me() { JSONObject current = state.get(); JSONObject user = current == null ? null : current.optJSONObject("user"); return user == null ? "" : user.optString("id"); }
    private void text(LinearLayout parent, String value, int size) { parent.addView(PactUi.text(activity, value, size, PactUi.INK)); }
    private void muted(LinearLayout parent, String value, int size) { parent.addView(PactUi.text(activity, value, size, PactUi.MUTED)); }
    private static String path(JSONObject relation) { return "/v1/relationships/" + relation.optString("id") + "/actions"; }
    private static JSONObject action(JSONObject relation, String name) throws org.json.JSONException { return new JSONObject().put("expectedVersion", relation.getInt("version")).put("action", name); }
    private static String typeLabel(String type) { return "parent_child".equals(type) ? "亲子" : "couple".equals(type) ? "情侣" : "自定义"; }
    private static String statusLabel(String status) { return "active".equals(status) ? "已建立" : "inviting".equals(status) ? "等待对方加入" : "已解除"; }
}
