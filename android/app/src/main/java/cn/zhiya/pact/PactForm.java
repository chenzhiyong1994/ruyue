package cn.zhiya.pact;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
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
    private LinearLayout actionBar;
    private Runnable backAction;
    private Runnable unregisterSystemBack;
    PactForm(Context context, String title) {
        this.context=context;this.title=title;
        fields=PactUi.column(context); fields.setPadding(PactUi.dp(context,24),0,PactUi.dp(context,24),PactUi.dp(context,20));
        error=PactUi.text(context,"",14,PactUi.ORANGE); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);error.setPadding(0,0,0,PactUi.dp(context,12));
        error.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){error.setVisibility(s.length()==0?View.GONE:View.VISIBLE);}
            public void afterTextChanged(android.text.Editable text){}
        });
        scroll=new ScrollView(context); scroll.setFillViewport(true); scroll.addView(fields);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout modalHeader=PactUi.row(context);modalHeader.setPadding(PactUi.dp(context,24),PactUi.dp(context,24),PactUi.dp(context,24),PactUi.dp(context,20));
        TextView modalTitle=PactUi.title(context,title,21);modalHeader.addView(modalTitle,new LinearLayout.LayoutParams(0,-2,1));
        ImageView mark=brandMark();LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(PactUi.dp(context,24),PactUi.dp(context,24));markParams.leftMargin=PactUi.dp(context,12);modalHeader.addView(mark,markParams);
        dialog=new AlertDialog.Builder(context).setCustomTitle(modalHeader).setView(scroll).setNegativeButton("返回",null).create();
    }
    void show(String label, View.OnClickListener listener) {
        int precedingMargin=0;
        if(fields.getChildCount()>0){ViewGroup.LayoutParams previous=fields.getChildAt(fields.getChildCount()-1).getLayoutParams();if(previous instanceof LinearLayout.LayoutParams)precedingMargin=((LinearLayout.LayoutParams)previous).bottomMargin;}
        int actionGap=Math.max(0,PactUi.dp(context,12)-precedingMargin);
        if(actionGap>0)fields.addView(new View(context),new LinearLayout.LayoutParams(1,actionGap));
        fields.addView(error); submit=PactUi.button(fields.getContext(),label,true,listener); fields.addView(submit); dialog.show();
        styleWindow(false);Button back=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);if(back!=null){back.setTextColor(PactUi.ROSE);back.setTextSize(14);back.setAllCaps(false);back.setIncludeFontPadding(false);back.setMinHeight(PactUi.dp(context,48));}
    }
    /** Full-screen native form with scrollable fields and a persistent action area. */
    void showFullscreen(String label,View.OnClickListener listener) {
        submit=PactUi.button(context,label,true,listener);
        // Show once before replacing the alert content so its original ScrollView can
        // be safely detached, while preserving the same AlertDialog API for callers.
        dialog.show();ViewGroup originalParent=(ViewGroup)scroll.getParent();if(originalParent!=null)originalParent.removeView(scroll);
        LinearLayout page=PactUi.column(context);page.setBackground(PactUi.pageBackground(context));
        LinearLayout header=PactUi.row(context);header.setPadding(PactUi.dp(context,12),PactUi.dp(context,4),PactUi.dp(context,12),PactUi.dp(context,16));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.argb(100,255,255,255),Color.argb(70,246,231,240)}));
        close=PactUi.iconButton(context,"返回","back",v->navigateBack());header.addView(close);
        TextView heading=PactUi.title(context,title,17);heading.setGravity(Gravity.CENTER);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView balance=brandMark();balance.setPadding(PactUi.dp(context,14),PactUi.dp(context,14),PactUi.dp(context,14),PactUi.dp(context,14));header.addView(balance,new LinearLayout.LayoutParams(PactUi.dp(context,48),PactUi.dp(context,48)));page.addView(header);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        PactUi.divider(context,page);footer=PactUi.column(context);footer.setPadding(PactUi.dp(context,24),PactUi.dp(context,12),PactUi.dp(context,24),PactUi.dp(context,12));
        footer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(255,253,251),Color.rgb(248,240,246)}));footer.addView(error);actionBar=PactUi.row(context);actionBar.setGravity(Gravity.CENTER_VERTICAL);actionBar.addView(submit,new LinearLayout.LayoutParams(0,-2,1));footer.addView(actionBar);page.addView(footer,new LinearLayout.LayoutParams(-1,-2));
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
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.rightMargin=PactUi.dp(context,16);actionBar.addView(action,0,params);
    }
    private ImageView brandMark() {
        ImageView mark=new ImageView(context);mark.setImageDrawable(new PactIcon("rings",PactUi.ROSE));mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);mark.setAlpha(.55f);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return mark;
    }
    private void styleWindow(boolean fullscreen) {
        Window window=dialog.getWindow();if(window==null)return;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        GradientDrawable surface=fullscreen?PactUi.background(PactUi.BACKGROUND,0):new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(255,253,251),Color.rgb(250,244,247)});
        if(!fullscreen){surface.setCornerRadius(PactUi.dp(context,26));surface.setStroke(PactUi.dp(context,1),PactUi.LINE);}
        window.setBackgroundDrawable(surface);
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
        if(actionBar!=null)for(int i=0;i<actionBar.getChildCount();i++)actionBar.getChildAt(i).setEnabled(!value);
    }
    void fail(String message) { busy(false);error.setText(message); }
}
