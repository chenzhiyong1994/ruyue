package cn.zhiya.pact;

import android.app.Activity;
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
        form = new PactForm(activity, "一份新的约定");
        stepLabel = PactUi.text(activity, "", 12, PactUi.MUTED);
        heading = PactUi.serif(activity, "", 29);
        description = PactUi.text(activity, "", 14, PactUi.MUTED);
        form.fields.addView(stepLabel);
        form.fields.addView(heading);
        form.fields.addView(description);
        for (int n = 0; n < pages.length; n++) {
            pages[n] = PactUi.column(activity);
            form.fields.addView(pages[n]);
        }

        LinearLayout relationCard = PactUi.card(activity, pages[0]);
        relationCard.addView(PactUi.text(activity, "与谁，认真说好一件事", 13, PactUi.MUTED));
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
        relationship.setSelection(selected);
        relationCard.addView(relationship, new LinearLayout.LayoutParams(-1, -2));
        role = PactUi.text(activity, "", 13, PactUi.MUTED);
        relationCard.addView(role);
        relationship.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateRole(); }
        });
        title = PactUi.input(activity, pages[0], "约定标题", "", false);
        title.setHint("给这件小事，起个名字");
        conditions = PactUi.input(activity, pages[0], "达成条件", "", true);
        conditions.setHint("怎样才算完成？把彼此的期待说清楚");
        count = PactUi.input(activity, pages[0], "目标次数", "1", false);
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        due = PactUi.input(activity, pages[0], "完成截止日期", "", false);
        due.setHint("yyyy-MM-dd，留空为不限期");

        LinearLayout rewardHero = PactUi.hero(activity, pages[1]);
        rewardHero.addView(PactUi.badge(activity, "留给做到的那一天"));
        rewardHero.addView(PactUi.serif(activity, "一点相伴的时间，\n也可以是很好的奖励。", 23));
        PactUi.art(activity, rewardHero, 64);
        reward = PactUi.input(activity, pages[1], "奖励内容", "", true);
        reward.setHint("完成后，我答应对方什么？");
        days = PactUi.input(activity, pages[1], "兑现天数", "7", false);
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        pages[1].addView(PactUi.text(activity,
                "验收达成后开始计算兑现时间。奖励给出后，还需要收讫确认。", 13, PactUi.MUTED));

        previous = PactUi.button(activity, "上一步", false, v -> back());
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
        String value = otherName() + "来完成，我来验收与兑现。"
                + (proxy() ? "\n家长代记：接受、执行和收讫会标明代记来源。" : "\n发出后，等对方接受才开始。仅彼此可见。");
        role.setText(value);
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
        String[] titles = {"想一起，做点什么？", "给做到的那天，\n一点期待。", "最后，一起说清楚。"};
        String[] descriptions = {"从一件小事开始，给彼此一点期待。", "奖励可以很小，认真兑现就很好。", "发出之前，再看看彼此答应了什么。"};
        String[] actions = {"下一步：约定奖励", "下一步：确认约定", "发出约定"};
        stepLabel.setText(String.format(Locale.getDefault(), "STEP %02d / 03", step + 1));
        stepLabel.setContentDescription(String.format(Locale.getDefault(), "创建第 %d 步，共 3 步", step + 1));
        heading.setText(titles[step]);
        description.setText(descriptions[step]);
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
        LinearLayout card = PactUi.hero(activity, pages[2]);
        card.addView(PactUi.badge(activity, selectedRelationship().optString("name") + " · 等你发出"));
        card.addView(PactUi.serif(activity, title.getText().toString(), 25));
        previewTerm(card, "完成条件", conditions.getText().toString());
        previewTerm(card, "目标与截止", count.getText().toString() + " 次 · "
                + (due.getText().toString().trim().isEmpty() ? "不限期" : due.getText().toString().trim() + " 前"));
        previewTerm(card, "答应的奖励", reward.getText().toString());
        previewTerm(card, "兑现时间", "达成后 " + days.getText().toString() + " 天内");
        LinearLayout roles = PactUi.card(activity, pages[2]);
        roles.addView(PactUi.title(activity, "彼此的角色", 17));
        roles.addView(PactUi.text(activity, otherName() + "负责完成，我负责验收与兑现。", 15, PactUi.INK));
        roles.addView(PactUi.text(activity, proxy()
                ? "本约定由家长代记。代为接受、提交与收讫均会记录实际操作人，不代表孩子独立确认。"
                : "对方接受后，约定才会生效。生效后，修改条款需要双方同意；奖励给出后，由对方确认收到。", 13, PactUi.MUTED));
        pages[2].addView(PactUi.button(activity, "存为草稿", false, v -> send(false)));
    }

    private void previewTerm(LinearLayout parent, String label, String value) {
        parent.addView(PactUi.text(activity, label, 12, PactUi.MUTED));
        parent.addView(PactUi.text(activity, value, 16, PactUi.INK));
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
