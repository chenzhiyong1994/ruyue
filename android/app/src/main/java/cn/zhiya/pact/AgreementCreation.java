package cn.zhiya.pact;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Three local steps share one form and one idempotency identity until the final write. */
final class AgreementCreation {
    interface Sender { void send(PactForm form, JSONObject body); }

    private final Activity activity;
    private final List<JSONObject> relationships;
    private final String userId;
    private final Sender sender;
    private final PactForm form;
    private final LinearLayout[] pages = new LinearLayout[3];
    private final View[] stepSegments = new View[3];
    private final TextView stepLabel, heading, description, role;
    private final EditText title, conditions, count, due, reward, days;
    private final Spinner relationship;
    private final Button previous;
    private int step;

    AgreementCreation(Activity activity, List<JSONObject> relationships, String userId,
                      String selectedRelationship, Sender sender) {
        this.activity = activity;
        this.relationships = relationships;
        this.userId = userId;
        this.sender = sender;
        form = new PactForm(activity, "创建约定");
        stepLabel = PactUi.text(activity, "", 12, PactUi.MUTED);
        heading = PactUi.title(activity, "", 24);
        description = PactUi.text(activity, "", 14, PactUi.MUTED);
        LinearLayout steps=PactUi.row(activity);
        steps.addView(stepLabel,new LinearLayout.LayoutParams(0,-2,1));
        for(int n=0;n<stepSegments.length;n++){
            View segment=new View(activity);segment.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);stepSegments[n]=segment;
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(PactUi.dp(activity,24),PactUi.dp(activity,4));params.leftMargin=PactUi.dp(activity,4);steps.addView(segment,params);
        }
        form.fields.addView(steps,new LinearLayout.LayoutParams(-1,-2));
        PactUi.gap(activity,form.fields,8);
        form.fields.addView(heading);
        LinearLayout.LayoutParams descriptionParams=new LinearLayout.LayoutParams(-1,-2);descriptionParams.topMargin=PactUi.dp(activity,8);form.fields.addView(description,descriptionParams);
        PactUi.gap(activity,form.fields,24);
        for (int n = 0; n < pages.length; n++) {
            pages[n] = PactUi.column(activity);
            form.fields.addView(pages[n]);
        }

        LinearLayout relationCard = PactUi.card(activity, pages[0]);
        tintCard(relationCard, false);
        relationCard.addView(PactUi.text(activity, "所属关系", 13, PactUi.MUTED));
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int n = 0; n < relationships.size(); n++) {
            JSONObject item = relationships.get(n);
            names.add(item.optString("name"));
            if (selectedRelationship.equals(item.optString("id"))) selected = n;
        }
        relationship = new Spinner(activity);
        relationship.setContentDescription("所属关系");
        relationship.setMinimumHeight(PactUi.dp(activity, 48));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        relationship.setAdapter(adapter);
        PactUi.styleSpinner(activity,relationship);
        relationship.setSelection(selected);
        relationCard.addView(relationship, new LinearLayout.LayoutParams(-1, -2));
        role = PactUi.text(activity, "", 12, PactUi.MUTED);
        PactUi.gap(activity,relationCard,4);
        relationCard.addView(role);
        PactUi.gap(activity,pages[0],8);
        relationship.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateRole(); }
        });
        title = PactUi.input(activity, pages[0], "约定标题", "", false);
        title.setHint("例如：晚饭后散步 4 次");
        conditions = PactUi.input(activity, pages[0], "达成条件", "", true);
        conditions.setHint("填写可以核对的完成条件");
        count = PactUi.input(activity, pages[0], "目标次数", "1", false);
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        due = PactUi.input(activity, pages[0], "完成截止日期", "", false);
        due.setHint("yyyy-MM-dd，留空为不限期");

        reward = PactUi.input(activity, pages[1], "奖励内容", "", true);
        reward.setHint("填写验收通过后给予的奖励");
        days = PactUi.input(activity, pages[1], "兑现天数", "7", false);
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        pages[1].addView(PactUi.text(activity,
                "验收通过后开始计算兑现时间，由兑现方标记已兑现。", 13, PactUi.MUTED));

        previous = PactUi.textButton(activity, "上一步", v -> back());
        updateRole();
        form.setBackAction(this::back);
        form.showFullscreen("下一步：约定奖励", v -> next());
        form.addFooterAction(previous);
        renderStep();
    }

    private JSONObject selectedRelationship() { return relationships.get(relationship.getSelectedItemPosition()); }
    private JSONObject otherMember() {
        JSONArray members = selectedRelationship().optJSONArray("members");
        for (int n = 0; members != null && n < members.length(); n++) {
            JSONObject member = members.optJSONObject(n);
            if (member != null && !userId.equals(member.optString("id"))) return member;
        }
        return new JSONObject();
    }
    private String otherName() { return otherMember().optString("name", "对方"); }
    private boolean proxy() { return "proxy".equals(selectedRelationship().optString("mode")); }
    private void updateRole() {
        String value = otherName() + "完成 · 我验收与兑现"
                + (proxy() ? "\n家长代记，操作会标明来源。" : "");
        role.setText(value);
        description.setText(activity.getString(R.string.creation_relationship_context,
                selectedRelationship().optString("name"), otherName(),
                proxy() ? activity.getString(R.string.creation_proxy_suffix) : ""));
    }

    private void next() {
        if (form.busy) return;
        if (step == 0 && validFirstStep()) { step = 1; renderStep(); }
        else if (step == 1 && validReward()) { step = 2; renderStep(); }
        else if (step == 2) send(true);
    }

    private void back() {
        if (form.busy) return;
        if (step > 0) { step--; renderStep(); }
        else form.dialog.dismiss();
    }

    private void renderStep() {
        String[] titles = {"约定内容", "奖励设置", "确认约定"};
        String[] actions = {"下一步：约定奖励", "下一步：确认约定", "发出约定"};
        stepLabel.setText(String.format(Locale.getDefault(), "步骤 %d / 3", step + 1));
        stepLabel.setContentDescription(String.format(Locale.getDefault(), "创建第 %d 步，共 3 步", step + 1));
        for(int n=0;n<stepSegments.length;n++){
            GradientDrawable segment;
            if(n==step)segment=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{PactUi.ROSE,Color.rgb(184,117,145)});
            else segment=PactUi.background(n<step?Color.rgb(215,174,190):PactUi.LINE,0);
            segment.setCornerRadius(PactUi.dp(activity,2));stepSegments[n].setBackground(segment);
        }
        heading.setText(titles[step]);
        description.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        updateRole();
        for (int n = 0; n < pages.length; n++) pages[n].setVisibility(n == step ? View.VISIBLE : View.GONE);
        previous.setVisibility(step == 0 ? View.GONE : View.VISIBLE);
        form.submit.setText(actions[step]);
        form.submit.setContentDescription(actions[step]);
        form.error.setText("");
        if (step == 2) preview();
        form.fields.post(() -> stepLabel.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, stepLabel.getWidth(), stepLabel.getHeight()), true));
    }

    private void preview() {
        pages[2].removeAllViews();
        LinearLayout card = PactUi.card(activity, pages[2]);
        tintCard(card, true);
        card.addView(PactUi.badge(activity, selectedRelationship().optString("name") + " · 待发出"));
        PactUi.gap(activity,card,12);
        card.addView(PactUi.title(activity, title.getText().toString(), 22));PactUi.gap(activity,card,12);
        PactUi.divider(activity,card);PactUi.gap(activity,card,12);
        previewTerm(card, "完成条件", conditions.getText().toString());
        previewTerm(card, "目标与截止", count.getText().toString() + " 次 · "
                + (due.getText().toString().trim().isEmpty() ? "不限期" : due.getText().toString().trim() + " 前"));
        previewTerm(card, "奖励内容", reward.getText().toString());
        previewTerm(card, "兑现时间", "达成后 " + days.getText().toString() + " 天内");
        LinearLayout roles = PactUi.card(activity, pages[2]);
        tintCard(roles, false);
        roles.addView(PactUi.title(activity, "双方角色", 17));
        PactUi.gap(activity,roles,12);
        roles.addView(PactUi.text(activity, otherName() + "负责完成，我负责验收与兑现。", 15, PactUi.INK));
        PactUi.gap(activity,roles,8);
        roles.addView(PactUi.text(activity, proxy()
                ? "本约定由家长代记。代为接受与提交均会记录实际操作人，不代表孩子独立确认。奖励由兑现方标记完成。"
                : "对方接受后，约定才会生效。生效后，修改条款需要双方同意；兑现方标记已兑现后，约定结束。", 13, PactUi.MUTED));
        pages[2].addView(PactUi.textButton(activity, "存为草稿", v -> send(false)));
    }

    private void previewTerm(LinearLayout parent, String label, String value) {
        PactUi.meta(activity,parent,label,value);
    }

    private void tintCard(LinearLayout card, boolean confirm) {
        GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                confirm?new int[]{Color.rgb(255,253,251),Color.rgb(249,239,244),Color.rgb(247,242,250)}
                        :new int[]{Color.rgb(255,253,251),Color.rgb(248,243,248)});
        surface.setCornerRadius(PactUi.dp(activity,20));surface.setStroke(PactUi.dp(activity,1),PactUi.LINE);card.setBackground(surface);
    }

    private boolean validFirstStep() {
        if (!textLength(title, 100, "请为约定填写 1–100 字的标题。")
                || !textLength(conditions, 2000, "请用 1–2000 字说清达成条件。")) return false;
        if (!positiveNumber(count, 10000, "目标次数需要是 1–10000 的整数。")) return false;
        try { deadline(); }
        catch (RuntimeException error) { form.fail("完成截止请填写 yyyy-MM-dd，也可以留空。"); due.requestFocus(); return false; }
        if (otherMember().optString("id").isEmpty()) { form.fail("这段关系的另一名成员尚未就绪，请返回刷新。"); return false; }
        return true;
    }
    private boolean validReward() {
        return textLength(reward, 2000, "请用 1–2000 字填写答应的奖励。")
                && positiveNumber(days, 365, "兑现天数需要是 1–365 的整数。");
    }
    private boolean textLength(EditText input, int maximum, String message) {
        String value = input.getText().toString().trim();
        if (!value.isEmpty() && value.codePointCount(0, value.length()) <= maximum) return true;
        form.fail(message); input.requestFocus(); return false;
    }
    private boolean positiveNumber(EditText input, int maximum, String message) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value > 0 && value <= maximum) return true;
        } catch (NumberFormatException ignored) { /* Show a field-specific error. */ }
        form.fail(message); input.requestFocus(); return false;
    }
    private Object deadline() {
        String value = due.getText().toString().trim();
        return value.isEmpty() ? JSONObject.NULL
                : LocalDate.parse(value).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toString();
    }
    private void send(boolean publish) {
        if (form.busy || step != 2 || !validFirstStep() || !validReward()) return;
        try {
            JSONObject body = new JSONObject().put("relationshipId", selectedRelationship().getString("id"))
                    .put("executorId", otherMember().getString("id"))
                    .put("title", title.getText().toString()).put("conditions", conditions.getText().toString())
                    .put("targetCount", Integer.parseInt(count.getText().toString().trim())).put("dueAt", deadline())
                    .put("reward", reward.getText().toString()).put("redeemWithinDays", Integer.parseInt(days.getText().toString().trim()))
                    .put("publish", publish);
            sender.send(form, body);
        } catch (org.json.JSONException error) { form.fail("这段关系暂时不可用，请返回刷新后重试。"); }
    }
}
