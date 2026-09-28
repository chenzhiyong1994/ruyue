package cn.zhiya.pact;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;

/** Explicit UI steps from the isolated host smoke. Never clears application data. */
public final class PactInstrumentation extends Instrumentation {
    private Activity activity;
    private Bundle arguments;
    private final java.util.Map<String,Integer> scrollBookmarks=new java.util.HashMap<>();

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            require(getTargetContext().getPackageName().equals("cn.zhiya.pact.preview"), "Preview package required");
            require("yes".equals(arguments.getString("isolated")), "Explicit isolated test device required");
            String encoded = arguments.getString("steps", "");
            require(!encoded.isEmpty(), "Explicit UI steps required");
            JSONArray steps = new JSONArray(new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8));
            open();
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                execute(step);
                Bundle progress = new Bundle();
                progress.putString("stream", "STEP " + (i + 1) + ": " + step.getString("op") + " " + step.optString("label") + "\n");
                sendStatus(0, progress);
            }
            capture();
            result.putString("stream", "PASS: " + steps.length() + " real Android UI steps\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            try { capture(); } catch (Exception ignored) { /* Preserve the original failure. */ }
            result.putString("stream", "FAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void capture() throws Exception {
        // A main-thread idle queue can precede the next rendered frame after a
        // synchronous scroll or text update. Capture after accessibility settles.
        getUiAutomation().waitForIdle(150,3000);
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        require(screenshot != null, "Cannot capture final UI");
        try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getFilesDir(), "pact-ui-capture.png"))) {
            require(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output), "Cannot save final UI");
        } finally { screenshot.recycle(); }
    }

    private void open() {
        Intent intent = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
        require(intent != null, "Launcher missing");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = startActivitySync(intent);
        waitForIdleSync();
    }

    private void execute(JSONObject step) throws Exception {
        String op = step.getString("op"), label = step.optString("label");
        switch (op) {
            case "await": await(label); break;
            case "click": click(label,step.optString("rememberScroll")); break;
            case "fill":
                await(label);
                boolean[] filled = {false};
                runOnMainSync(() -> {
                    View v = findType(activeRoot(), label, EditText.class);
                    if (v instanceof EditText) {
                        v.requestRectangleOnScreen(new Rect(0, 0, v.getWidth(), v.getHeight()), true);
                        ((EditText) v).setText(step.optString("value"));
                        filled[0] = true;
                    }
                });
                if (!filled[0]) {
                    AccessibilityNodeInfo field = accessible(label, NodeKind.EDITABLE);
                    Bundle text = new Bundle();
                    text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.optString("value"));
                    require(field != null && field.isEditable() && field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text), "Missing field: " + label);
                }
                waitForIdleSync();
                break;
            case "focus":
                await(label);boolean[] focused={false};
                runOnMainSync(()->{
                    View field=findType(activeRoot(),label,EditText.class);
                    if(field==null)return;
                    field.requestRectangleOnScreen(new Rect(0,0,field.getWidth(),field.getHeight()),true);
                    focused[0]=field.requestFocus();
                    android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    ime.showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
                require(focused[0],"Search field did not receive focus: "+label);waitForIdleSync();break;
            case "type":
                String keys=step.getString("value");require(keys.matches("[A-Za-z0-9 ]+"),"Typing fixture must use simple ASCII");
                sendStringSync(keys);waitForIdleSync();break;
            case "focused":
                boolean[] retained={false};runOnMainSync(()->{View field=findType(activeRoot(),label,EditText.class);retained[0]=field!=null&&field.hasFocus();});
                require(retained[0],"Search focus was lost: "+label);break;
            case "select-text":
                boolean[] positioned={false};runOnMainSync(()->{
                    View field=findType(activeRoot(),label,EditText.class);if(field==null)return;
                    EditText edit=(EditText)field;int start=step.optInt("start"),end=step.optInt("end",start);
                    if(start<0||end<start||end>edit.length())return;edit.setSelection(start,end);positioned[0]=true;
                });require(positioned[0],"Cannot set search selection");break;
            case "selection":
                int[] selection={-1,-1};runOnMainSync(()->{View field=findType(activeRoot(),label,EditText.class);if(field instanceof EditText){selection[0]=((EditText)field).getSelectionStart();selection[1]=((EditText)field).getSelectionEnd();}});
                require(selection[0]==step.optInt("start")&&selection[1]==step.optInt("end",step.optInt("start")),"Search selection changed to "+selection[0]+":"+selection[1]);break;
            case "select":
                await(label);
                boolean[] selected = {false};
                runOnMainSync(() -> {
                    View v = findType(activeRoot(), label, Spinner.class);
                    if (!(v instanceof Spinner)) return;
                    Spinner spinner = (Spinner) v;
                    spinner.requestRectangleOnScreen(new Rect(0, 0, spinner.getWidth(), spinner.getHeight()), true);
                    String value = step.optString("value");
                    for (int n = 0; n < spinner.getCount(); n++) {
                        if (value.equals(String.valueOf(spinner.getItemAtPosition(n)))) { spinner.setSelection(n); selected[0] = true; return; }
                    }
                    throw new AssertionError("Missing option: " + value);
                });
                if (!selected[0]) {
                    AccessibilityNodeInfo spinner = accessible(label, NodeKind.SPINNER);
                    require(spinner != null && spinner.isEnabled() && spinner.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Missing spinner: " + label);
                    waitForIdleSync();
                    click(step.optString("value"));
                }
                waitForIdleSync();
                break;
            case "photo":
                File fixture = new File(getTargetContext().getCacheDir(), "pact-smoke-synthetic.png");
                Bitmap sample = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                sample.eraseColor(Color.rgb(40, 86, 74));
                try (FileOutputStream output = new FileOutputStream(fixture)) {
                    require(sample.compress(Bitmap.CompressFormat.PNG, 100, output), "Cannot write synthetic photo");
                }
                sample.recycle();
                IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
                filter.addCategory(Intent.CATEGORY_OPENABLE);
                filter.addDataType("image/*");
                Intent picked = new Intent().setData(Uri.fromFile(fixture)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ActivityMonitor monitor = addMonitor(filter, new ActivityResult(Activity.RESULT_OK, picked), true);
                try {
                    click(label);
                    require(monitor.getHits() == 1, "Photo action did not launch document picker");
                } finally { removeMonitor(monitor); }
                break;
            case "absent":
                runOnMainSync(() -> require(find(activeRoot(), label, false) == null, "Unexpected view: " + label));
                require(popup(label) == null, "Unexpected popup view: " + label);
                break;
            case "layout":
                AssertionError[] violation = {null};
                runOnMainSync(() -> {
                    try { checkLayout(activeRoot()); }
                    catch (AssertionError error) { violation[0] = error; }
                });
                if (violation[0] != null) throw violation[0];
                break;
            case "layout-negative-control":
                boolean[] rejected = {false};
                runOnMainSync(() -> {
                    TextView overflow = new TextView(activity);
                    overflow.setText("Visible text must not fit inside this narrow view");
                    overflow.setSingleLine(true); overflow.setEllipsize(null);
                    overflow.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    overflow.layout(0, 0, 80, overflow.getMeasuredHeight());
                    try { checkLayout(overflow); }
                    catch (AssertionError expected) { rejected[0] = true; }
                });
                require(rejected[0], "Layout check accepted visibly overflowing text");
                break;
            case "scroll":
                runOnMainSync(()->mainScroll().scrollTo(0,step.optInt("y")));waitForIdleSync();break;
            case "remember-scroll":
                int[] position={0};runOnMainSync(()->position[0]=mainScroll().getScrollY());
                require(position[0]>=step.optInt("minimum",0),"List did not reach required scroll depth");
                scrollBookmarks.put(step.getString("key"),position[0]);break;
            case "assert-scroll":
                Integer expected=scrollBookmarks.get(step.getString("key"));require(expected!=null,"Missing scroll bookmark");
                long deadline=SystemClock.uptimeMillis()+3000;int[] actual={0};
                do {waitForIdleSync();runOnMainSync(()->actual[0]=mainScroll().getScrollY());
                    if(Math.abs(actual[0]-expected)<=step.optInt("tolerance",4))break;
                    SystemClock.sleep(50);
                } while(SystemClock.uptimeMillis()<deadline);
                require(Math.abs(actual[0]-expected)<=step.optInt("tolerance",4),"Scroll changed from "+expected+" to "+actual[0]);break;
            case "back": sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); waitForIdleSync(); break;
            case "restart": runOnMainSync(() -> activity.finish()); open(); break;
            default: throw new AssertionError("Unknown UI operation: " + op);
        }
    }

    private void await(String label) {
        long deadline = SystemClock.uptimeMillis() + 25000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] found = {false};
            runOnMainSync(() -> found[0] = find(activeRoot(), label, false) != null);
            if (found[0] || popup(label) != null) { waitForIdleSync(); return; }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for: " + label);
    }

    private void click(String label) {click(label,"");}
    private void click(String label,String bookmark) {
        await(label);
        boolean[] clicked = {false};
        runOnMainSync(() -> {
            View view = find(activeRoot(), label, true);
            if (view == null) return;
            require(view.isEnabled(), "Disabled action: " + label);
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            View view = find(activeRoot(), label, true);
            if (view == null) return;
            Rect visible = new Rect();
            require(view.getGlobalVisibleRect(visible) && visible.height() > 0, "Action not reachable: " + label);
            if(!bookmark.isEmpty())scrollBookmarks.put(bookmark,mainScroll().getScrollY());
            clicked[0] = view.performClick();
        });
        if (!clicked[0]) {
            AccessibilityNodeInfo node = accessible(label, NodeKind.CLICKABLE);
            require(node != null && node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Missing action: " + label);
        }
        waitForIdleSync();
    }

    private ScrollView mainScroll(){
        ScrollView scroll=findScroll(activity.getWindow().getDecorView());require(scroll!=null,"Missing main scroll surface");return scroll;
    }
    private ScrollView findScroll(View view){
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ScrollView found=findScroll(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }

    private static View find(View view, String label, boolean actionable) {
        if (view.getVisibility() != View.VISIBLE) return null;
        CharSequence description = view.getContentDescription();
        CharSequence text = view instanceof TextView ? ((TextView) view).getText() : null;
        if ((label.contentEquals(description == null ? "" : description) || label.contentEquals(text == null ? "" : text))
                && (!actionable || view.hasOnClickListeners())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) {
                View found = find(group.getChildAt(n), label, actionable);
                if (found != null) return found;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo popup(String label) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return findNode(root, label, NodeKind.ANY);
    }

    /** Dialogs and Spinner popups own separate window roots from the Activity. */
    private View activeRoot() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            java.util.List<View> roots = android.view.inspector.WindowInspector.getGlobalWindowViews();
            for (int n = roots.size() - 1; n >= 0; n--) {
                View root = roots.get(n);
                if (root.isShown() && root.hasWindowFocus()) return root;
            }
        }
        return activity.getWindow().getDecorView();
    }

    private enum NodeKind { ANY, EDITABLE, CLICKABLE, SPINNER }

    private AccessibilityNodeInfo accessible(String label, NodeKind kind) {
        AccessibilityNodeInfo node = findNode(getUiAutomation().getRootInActiveWindow(), label, kind);
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            waitForIdleSync();
            node = findNode(getUiAutomation().getRootInActiveWindow(), label, kind);
        }
        return node != null && node.isVisibleToUser() ? node : null;
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String label, NodeKind kind) {
        if (node == null) return null;
        if (label.contentEquals(node.getText() == null ? "" : node.getText())
                || label.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) {
            if (kind == NodeKind.ANY || (kind == NodeKind.EDITABLE && node.isEditable())
                    || (kind == NodeKind.SPINNER && Spinner.class.getName().contentEquals(node.getClassName()))) return node;
            if (kind == NodeKind.CLICKABLE) {
                AccessibilityNodeInfo action = node;
                while (action != null && !action.isClickable()) action = action.getParent();
                if (action != null) return action;
            }
        }
        for (int n = 0; n < node.getChildCount(); n++) {
            AccessibilityNodeInfo found = findNode(node.getChild(n), label, kind);
            if (found != null) return found;
        }
        return null;
    }

    private static View findType(View view, String label, Class<?> type) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (type.isInstance(view) && label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) {
                View found = findType(group.getChildAt(n), label, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void checkLayout(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            android.text.Layout layout = text.getLayout();
            if (layout != null && text.getEllipsize() == null && !(text instanceof EditText)) {
                int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
                // LineWidth includes invisible trailing spaces beyond the wrap edge.
                // LineMax measures the same line's visible extent, including margins.
                for (int n = 0; n < layout.getLineCount(); n++) require(layout.getLineMax(n) <= width + 3,
                        "Clipped text: " + text.getText() + "; line=" + n + "; available=" + width
                                + "; advance=" + layout.getLineWidth(n) + "; visible=" + layout.getLineMax(n));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) checkLayout(group.getChildAt(n));
        }
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
