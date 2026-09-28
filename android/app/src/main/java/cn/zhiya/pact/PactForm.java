package cn.zhiya.pact;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Non-dismiss-on-error form: input survives server and network failures. */
final class PactForm {
    final LinearLayout fields;
    final TextView error;
    final AlertDialog dialog;
    final PactRequest request = new PactRequest();
    Button submit;
    boolean busy;
    Integer expectedVersionOverride;
    boolean conflictControlShown;
    private final Context context;
    private final String title;
    private final ScrollView scroll;
    private Button close;
    private LinearLayout footer;
    private Runnable backAction;
    private Runnable unregisterSystemBack;
    PactForm(Context context, String title) {
        this.context=context;this.title=title;
        fields=PactUi.column(context); fields.setPadding(PactUi.dp(context,22),PactUi.dp(context,8),PactUi.dp(context,22),PactUi.dp(context,14));
        error=PactUi.text(context,"",14,PactUi.ORANGE); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        scroll=new ScrollView(context); scroll.setFillViewport(true); scroll.addView(fields);scroll.setClipToPadding(false);
        dialog=new AlertDialog.Builder(context).setTitle(title).setView(scroll).setNegativeButton("返回",null).create();
    }
    void show(String label, View.OnClickListener listener) {
        fields.addView(error); submit=PactUi.button(fields.getContext(),label,true,listener); fields.addView(submit); dialog.show();
        styleWindow(false);Button back=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);if(back!=null)back.setTextColor(PactUi.ROSE);
    }
    /** Full-screen native form with scrollable fields and a persistent action area. */
    void showFullscreen(String label,View.OnClickListener listener) {
        submit=PactUi.button(context,label,true,listener);
        // Show once before replacing the alert content so its original ScrollView can
        // be safely detached, while preserving the same AlertDialog API for callers.
        dialog.show();ViewGroup originalParent=(ViewGroup)scroll.getParent();if(originalParent!=null)originalParent.removeView(scroll);
        LinearLayout page=PactUi.column(context);page.setBackground(PactUi.pageBackground(context));
        LinearLayout header=PactUi.row(context);header.setPadding(PactUi.dp(context,16),PactUi.dp(context,13),PactUi.dp(context,20),PactUi.dp(context,12));
        close=PactUi.iconButton(context,"返回","back",v->navigateBack());header.addView(close);
        TextView heading=PactUi.serif(context,title,21);heading.setGravity(Gravity.CENTER);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        View balance=new View(context);header.addView(balance,new LinearLayout.LayoutParams(PactUi.dp(context,36),1));page.addView(header);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        footer=PactUi.column(context);footer.setPadding(PactUi.dp(context,22),PactUi.dp(context,5),PactUi.dp(context,22),PactUi.dp(context,15));
        footer.setBackgroundColor(PactUi.BACKGROUND);footer.addView(error);footer.addView(submit);page.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        page.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                int bottom=Math.max(safe.bottom,insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);view.setPadding(safe.left,safe.top,safe.right,bottom);
            }else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        installFullscreenBackHandling(page);
        dialog.setContentView(page,new ViewGroup.LayoutParams(-1,-1));styleWindow(true);page.requestApplyInsets();
    }
    void setBackAction(Runnable action) { backAction=action; }
    private void navigateBack() {
        if(busy)return;
        if(backAction!=null)backAction.run();else dialog.dismiss();
    }
    private void systemBack(View page) {
        // Normally the IME's default-priority callback handles Back first. Keep
        // this check for a keyboard that became visible before our registration.
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.view.WindowInsets insets=page.getRootWindowInsets();
            if(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime())){
                android.view.WindowInsetsController controller=page.getWindowInsetsController();
                if(controller!=null)controller.hide(android.view.WindowInsets.Type.ime());
                return;
            }
        }
        navigateBack();
    }
    private void installFullscreenBackHandling(View page) {
        if(android.os.Build.VERSION.SDK_INT>=33){
            // The dialog owns a separate dispatcher from its Activity. Attach and
            // detach the callback with this content, including programmatic dismiss.
            page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                @Override public void onViewAttachedToWindow(View view){registerSystemBack(view);}
                @Override public void onViewDetachedFromWindow(View view){
                    Runnable cleanup=unregisterSystemBack;unregisterSystemBack=null;
                    if(cleanup!=null)cleanup.run();
                }
            });
            if(page.isAttachedToWindow())registerSystemBack(page);
        }else{
            // Before API 33, the IME receives pre-IME Back before this listener.
            dialog.setOnKeyListener((current,key,event)->{
                if(key!=android.view.KeyEvent.KEYCODE_BACK)return false;
                if(event.getAction()==android.view.KeyEvent.ACTION_UP&&!event.isCanceled())systemBack(page);
                return true;
            });
        }
    }
    private void registerSystemBack(View page) {
        if(android.os.Build.VERSION.SDK_INT>=33&&unregisterSystemBack==null){
            android.window.OnBackInvokedDispatcher dispatcher=dialog.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback=()->systemBack(page);
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);
            unregisterSystemBack=()->dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
    void addFooterAction(View action) {
        if(footer==null)throw new IllegalStateException("Show the full-screen form before adding a footer action.");
        ViewGroup oldParent=(ViewGroup)action.getParent();if(oldParent!=null)oldParent.removeView(action);
        footer.addView(action,footer.indexOfChild(submit));
    }
    private void styleWindow(boolean fullscreen) {
        Window window=dialog.getWindow();if(window==null)return;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setBackgroundDrawable(PactUi.background(PactUi.BACKGROUND,fullscreen?0:PactUi.dp(context,27)));
        if(fullscreen){
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);window.setGravity(Gravity.CENTER);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(PactUi.BACKGROUND);window.setNavigationBarColor(PactUi.BACKGROUND);
            if(android.os.Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
            int flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }
    void busy(boolean value) {
        busy=value;if(submit!=null)submit.setEnabled(!value);dialog.setCancelable(!value);
        Button back=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);if(back!=null)back.setEnabled(!value);if(close!=null)close.setEnabled(!value);
        if(footer!=null)for(int i=0;i<footer.getChildCount();i++){View action=footer.getChildAt(i);if(action!=error&&action!=submit)action.setEnabled(!value);}
    }
    void fail(String message) { busy(false);error.setText(message); }
}
