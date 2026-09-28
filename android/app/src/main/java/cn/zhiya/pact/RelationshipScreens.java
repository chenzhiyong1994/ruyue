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
        LinearLayout welcome = PactUi.hero(activity, parent);
        welcome.addView(PactUi.badge(activity, "我们 · 彼此的私人空间"));
        welcome.addView(PactUi.serif(activity, "把重要的人，\n放进彼此的日常。", 28));
        muted(welcome, "从一段关系开始，把答应的小事，慢慢做成共同的记忆。", 14);
        welcome.addView(PactUi.button(activity, "创建关系", true, v -> create()));
        welcome.addView(PactUi.button(activity, "加入关系", false, v -> join()));
        JSONObject current = state.get();
        JSONArray relationships = current == null ? null : current.optJSONArray("relationships");
        if (relationships == null || relationships.length() == 0) {
            LinearLayout empty = PactUi.card(activity, parent);
            empty.addView(PactUi.serif(activity, "给「我们」，留一个位置。", 22));
            muted(empty, "还没有关系。从与你一起完成约定的人开始。", 15);
            divider(empty);
            text(empty, "邀请对方，各自确认", 16);
            muted(empty, "彼此使用自己的账号。加入后，约定只在这段关系里分享。", 14);
            text(empty, "为孩子，建立代记档案", 16);
            muted(empty, "由家长记录孩子的参与与收讫，每一步都会注明来源。", 14);
            return;
        }
        LinearLayout section=PactUi.sectionTitle(activity,parent,"","");section.removeAllViews();
        LinearLayout heading=PactUi.row(activity);section.addView(heading);
        heading.addView(PactUi.title(activity,"每一段我们",19),new LinearLayout.LayoutParams(0,-2,1));
        if(relationships.length()>1||!relationshipFilter.get().isEmpty()){
            android.widget.Button scope=PactUi.button(activity,"查看范围",false,v->chooseRelationship.run());
            scope.setTextSize(13);scope.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            scope.setPadding(PactUi.dp(activity,8),0,0,0);
            heading.addView(scope,new LinearLayout.LayoutParams(-2,-2));
        }
        String scopeName="";
        for(int i=0;i<relationships.length();i++){JSONObject row=relationships.optJSONObject(i);if(row!=null&&row.optString("id").equals(relationshipFilter.get()))scopeName=row.optString("name");}
        muted(section,scopeName.isEmpty()?"关系独立保存，心意各有归处。":"当前查看："+scopeName+" · 可在查看范围中恢复全部",13);
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
        String status = relationship.optString("status");
        boolean proxy = "proxy".equals(relationship.optString("mode"));
        TextView statusView = PactUi.badge(activity, statusLabel(status));
        if ("ended".equals(status)) {
            statusView.setTextColor(PactUi.MUTED);
            statusView.setBackground(PactUi.background(PactUi.LAVENDER, PactUi.dp(activity, 12)));
        }
        card.addView(statusView);
        card.addView(PactUi.serif(activity, relationship.optString("name"), 25));
        muted(card, typeLabel(relationship.optString("type")) + " · "
                + (proxy ? "家长代记" : "双方账号"), 13);
        divider(card);
        JSONArray members = relationship.optJSONArray("members");
        for (int i = 0; members != null && i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member != null) member(card, member);
        }
        if ("ended".equals(status)) {
            note(card, "记录仍在，承诺仍算数", "关系已解除，共同记录仍保留；已达成的奖励可以继续结算。", false);
            return;
        }
        if (proxy) note(card, "家长代记，来源清楚", "孩子的接受、提交与收讫由家长代记，完成时会显示“家长代记已结案”。", true);
        else if ("active".equals(status)) muted(card, "各自确认，一起完成。这里的约定只与你们分享。", 14);
        String code = relationship.isNull("inviteCode") ? "" : relationship.optString("inviteCode");
        if ("inviting".equals(status) && me().equals(relationship.optString("ownerId"))) {
            LinearLayout invitation = note(card, "把邀请，交给重要的人", "对方加入后，你们就可以开始第一份约定。", false);
            if (!code.isEmpty()) {
                TextView invitationCode = PactUi.text(activity, "邀请码：" + code, 14, PactUi.INK);
                invitationCode.setTypeface(Typeface.MONOSPACE);
                invitationCode.setTextIsSelectable(true);
                invitation.addView(invitationCode);
                muted(invitation, "邀请有效期：" + PactLabels.date(relationship.optString("inviteExpiresAt")), 13);
                invitation.addView(PactUi.button(activity, "复制邀请码", true, v -> copy(code)));
            }
            invitation.addView(PactUi.button(activity, "重新生成邀请", false, v -> renew(relationship)));
        }
        divider(card);
        card.addView(PactUi.button(activity, "修改称呼", false, v -> rename(relationship)));
        card.addView(PactUi.button(activity, "解除关系", false, v -> end(relationship)));
    }

    private void member(LinearLayout parent, JSONObject member) {
        boolean child = "child".equals(member.optString("kind"));
        boolean self = me().equals(member.optString("id"));
        String name = member.optString("name");
        LinearLayout row = PactUi.row(activity);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = PactUi.dp(activity, 6);
        rowParams.bottomMargin = PactUi.dp(activity, 8);
        parent.addView(row, rowParams);
        String initial = name.isEmpty() ? "人" : name.substring(0, name.offsetByCodePoints(0, 1));
        TextView avatar = PactUi.title(activity, initial, 19);
        avatar.setGravity(Gravity.CENTER);
        avatar.setPadding(0, 0, 0, 0);
        avatar.setTextColor(PactUi.ROSE);
        avatar.setBackground(PactUi.background(child ? PactUi.LAVENDER : PactUi.BLUSH, PactUi.dp(activity, 22)));
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(avatar, new LinearLayout.LayoutParams(PactUi.dp(activity, 44), PactUi.dp(activity, 44)));
        LinearLayout identity = PactUi.column(activity);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
        identityParams.leftMargin = PactUi.dp(activity, 12);
        row.addView(identity, identityParams);
        identity.addView(PactUi.title(activity, name, 17));
        muted(identity, child ? "孩子档案 · 由家长代记" : self ? "我 · 本人账号" : "对方 · 独立账号", 13);
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
        muted(box, description, 14);
        return box;
    }

    private void divider(LinearLayout parent) {
        View line = new View(activity);
        line.setBackgroundColor(PactUi.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, PactUi.dp(activity, 1));
        params.topMargin = PactUi.dp(activity, 12);
        params.bottomMargin = PactUi.dp(activity, 12);
        parent.addView(line, params);
    }

    private void create() {
        PactForm form = new PactForm(activity, "创建关系");
        muted(form.fields, "给彼此一个空间，从一份认真说好的小事开始。", 15);
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
        form.fields.addView(PactUi.badge(activity, "接受邀请 · 走进我们"));
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
        muted(form.fields, "在「" + relationship.optString("name") + "」里，让对方用熟悉的名字叫你。", 15);
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
        note(form.fields, "结束关系，不抹去已经答应的事", "已有共同记录继续保留，已经达成的奖励不会被取消，仍需完成兑现和收讫。", true);
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
        muted(parent, label, 14);
        Spinner spinner = new Spinner(activity); spinner.setContentDescription(label); spinner.setMinimumHeight(PactUi.dp(activity, 52));
        spinner.setPopupBackgroundDrawable(PactUi.background(PactUi.BACKGROUND, PactUi.dp(activity, 14)));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = PactUi.dp(activity, 12);
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
