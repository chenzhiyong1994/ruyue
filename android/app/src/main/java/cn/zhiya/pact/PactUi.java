package cn.zhiya.pact;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

final class PactUi {
    static final int BACKGROUND=Color.rgb(250,247,245), INK=Color.rgb(52,43,49),
            ROSE=Color.rgb(158,69,96), MUTED=Color.rgb(108,95,103),
            ORANGE=Color.rgb(176,74,56), LAVENDER=Color.rgb(238,229,241),
            BLUSH=Color.rgb(246,231,228), LINE=Color.rgb(234,225,228),
            PLUM=Color.rgb(115,89,134), SAGE=Color.rgb(71,111,94), AMBER=Color.rgb(137,98,48);
    private PactUi() {}
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }
    static GradientDrawable softSurface(Context c,boolean lavender,int radius) {
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(255,253,251),lavender?Color.rgb(243,237,248):Color.rgb(250,236,239)});
        shape.setCornerRadius(dp(c,radius));shape.setStroke(dp(c,1),lavender?Color.rgb(230,220,237):Color.rgb(239,217,226));return shape;
    }
    static Drawable pageBackground(Context c) { return new PageBackground(); }
    static LinearLayout column(Context c) { LinearLayout layout = new LinearLayout(c); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    static LinearLayout row(Context c) { LinearLayout layout = new LinearLayout(c); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setBaselineAligned(false);layout.setGravity(android.view.Gravity.CENTER_VERTICAL); return layout; }
    static TextView text(Context c, String content, int size, int color) {
        TextView view = new TextView(c); view.setText(content); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(false);view.setLineSpacing(dp(c,2),1);view.setPadding(0,0,0,0);return view;
    }
    static TextView title(Context c,String content,int size) { TextView view=text(c,content,size,INK);view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return view; }
    static TextView serif(Context c,String content,int size) {
        TextView view=text(c,content,size,INK);view.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        view.setLetterSpacing(.015f);view.setLineSpacing(dp(c,3),1);return view;
    }
    static TextView badge(Context c,String content) {
        String state=content.substring(content.lastIndexOf(" · ")<0?0:content.lastIndexOf(" · ")+3);
        int ink=ROSE,fill=Color.rgb(248,234,239);
        if(state.contains("已兑现")||state.contains("已建立")){ink=SAGE;fill=Color.rgb(234,242,235);}
        else if(state.contains("接受")||state.contains("加入")||state.contains("待发出")){ink=AMBER;fill=Color.rgb(247,239,225);}
        else if(state.contains("验收")||state.contains("兑现")){ink=PLUM;fill=Color.rgb(240,233,247);}
        else if(state.contains("结束")||state.contains("解除")||state.contains("草稿")||state.contains("未达成")){ink=MUTED;fill=Color.rgb(240,236,236);}
        TextView view=text(c,content,11,ink);view.setPadding(dp(c,8),dp(c,5),dp(c,8),dp(c,5));
        GradientDrawable shape=background(fill,dp(c,7));view.setBackground(shape);
        view.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));return view;
    }
    static LinearLayout sectionTitle(Context c,LinearLayout parent,String content,String subtitle) {
        LinearLayout group=column(c);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        int precedingMargin=0;if(parent.getChildCount()>0){android.view.ViewGroup.LayoutParams previous=parent.getChildAt(parent.getChildCount()-1).getLayoutParams();if(previous instanceof LinearLayout.LayoutParams)precedingMargin=((LinearLayout.LayoutParams)previous).bottomMargin;}
        params.topMargin=Math.max(0,dp(c,24)-precedingMargin);params.bottomMargin=dp(c,12);parent.addView(group,params);
        LinearLayout heading=row(c);View accent=new View(c);accent.setBackground(background(ROSE,dp(c,2)));LinearLayout.LayoutParams accentParams=new LinearLayout.LayoutParams(dp(c,3),dp(c,14));accentParams.rightMargin=dp(c,8);heading.addView(accent,accentParams);heading.addView(title(c,content,16));group.addView(heading);
        if(subtitle!=null&&!subtitle.isEmpty()){gap(c,group,6);group.addView(text(c,subtitle,13,MUTED));}return group;
    }
    static Button button(Context c, String content, boolean primary, View.OnClickListener click) {
        Button button = new Button(c); button.setText(content); button.setContentDescription(content); button.setAllCaps(false);
        button.setTextSize(15);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{primary?Color.rgb(239,219,228):MUTED,primary?Color.WHITE:ROSE}));
        button.setMinHeight(dp(c,48));button.setMinimumHeight(dp(c,48));button.setMinWidth(0);button.setMinimumWidth(0);
        button.setIncludeFontPadding(false);button.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));button.setLetterSpacing(0);
        GradientDrawable shape;
        if(primary){shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(175,82,109),Color.rgb(143,62,103)});shape.setCornerRadius(dp(c,14));shape.setStroke(dp(c,1),Color.argb(40,103,35,67));}
        else{shape=background(Color.TRANSPARENT,dp(c,10));}
        button.setBackground(ripple(shape,primary));button.setStateListAnimator(null);button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(primary?-1:-2,-2);button.setLayoutParams(params);
        return button;
    }
    static Button compactButton(Context c,String label,boolean primary,View.OnClickListener listener) {
        Button button=button(c,label,primary,listener);button.setTextSize(14);
        button.setTextColor(ROSE);
        if(primary){
            button.setBackground(ripple(new InsetDrawable(softSurface(c,false,10),0,dp(c,6),0,dp(c,6)),false));
            if(label.startsWith("创建")){PactIcon plus=new PactIcon("plus",ROSE);plus.setBounds(0,0,dp(c,15),dp(c,15));button.setCompoundDrawables(plus,null,null,null);button.setCompoundDrawablePadding(dp(c,5));}
        }
        button.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));
        button.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));return button;
    }
    static Button textButton(Context c,String label,View.OnClickListener listener) {
        Button button=compactButton(c,label,false,listener);button.setMinWidth(dp(c,48));button.setMinimumWidth(dp(c,48));button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setPadding(0,dp(c,12),0,dp(c,12));return button;
    }
    static void clickableEntry(Context c,LinearLayout entry,String label,View.OnClickListener listener) {
        entry.setContentDescription(label);entry.setFocusable(true);entry.setClickable(true);
        entry.setForeground(ripple(background(Color.TRANSPARENT,dp(c,14)),false));entry.setOnClickListener(listener);
    }
    static Button iconButton(Context c,String label,String iconKey,View.OnClickListener listener) {
        Button button=button(c,"",false,listener);button.setContentDescription(label);button.setPadding(dp(c,14),dp(c,14),dp(c,14),dp(c,14));
        button.setMinHeight(dp(c,48));button.setMinimumHeight(dp(c,48));button.setBackground(ripple(new InsetDrawable(background(Color.argb(150,255,255,255),dp(c,14)),dp(c,5),dp(c,5),dp(c,5),dp(c,5)),false));
        button.setPadding(dp(c,14),dp(c,14),dp(c,14),dp(c,14));
        PactIcon icon=new PactIcon(iconKey,INK);icon.setBounds(0,0,dp(c,20),dp(c,20));button.setCompoundDrawables(icon,null,null,null);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return button;
    }
    static Button navigationButton(Context c,String label,String iconKey,boolean selected,View.OnClickListener listener) {
        Button button=button(c,label,false,listener);button.setTextSize(11);button.setLetterSpacing(0);button.setGravity(Gravity.CENTER);
        button.setTextColor(selected?ROSE:MUTED);button.setPadding(dp(c,4),dp(c,10),dp(c,4),dp(c,8));
        button.setMinHeight(dp(c,60));button.setMinimumHeight(dp(c,60));button.setBackground(ripple(selected?new NavigationAccent(c):background(Color.TRANSPARENT,0),false));
        button.setPadding(dp(c,4),dp(c,10),dp(c,4),dp(c,8));
        PactIcon icon=new PactIcon(iconKey,selected?ROSE:MUTED);icon.setBounds(0,0,dp(c,22),dp(c,22));button.setCompoundDrawables(null,icon,null,null);button.setCompoundDrawablePadding(dp(c,4));
        button.setSelected(selected);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(c,3),0,dp(c,3),0);button.setLayoutParams(params);return button;
    }
    static EditText input(Context c, LinearLayout parent, String label, String value, boolean multiline) {
        TextView caption=title(c,label,13);LinearLayout.LayoutParams captionParams=new LinearLayout.LayoutParams(-1,-2);captionParams.bottomMargin=dp(c,8);parent.addView(caption,captionParams); EditText edit = new EditText(c); edit.setText(value); edit.setContentDescription(label);
        edit.setId(View.generateViewId());caption.setLabelFor(edit.getId());
        edit.setTextSize(15);edit.setIncludeFontPadding(false); edit.setTextColor(INK); edit.setHintTextColor(MUTED); edit.setSingleLine(!multiline);
        edit.setMinHeight(dp(c,48)); if (multiline) { edit.setMinLines(2); edit.setMaxLines(6); edit.setGravity(android.view.Gravity.TOP); }
        edit.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        GradientDrawable field=background(Color.rgb(255,253,252),dp(c,12));field.setStroke(dp(c,1),LINE);
        GradientDrawable focused=background(Color.WHITE,dp(c,12));focused.setStroke(dp(c,2),Color.rgb(194,126,151));
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focused);states.addState(new int[]{},field);edit.setBackground(states);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(c,20); parent.addView(edit,params); return edit;
    }
    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout card=surface(c);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(c,12);parent.addView(card,params);return card;
    }
    static LinearLayout surface(Context c){
        LinearLayout layout=column(c);layout.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.WHITE,Color.rgb(255,252,250)});shape.setCornerRadius(dp(c,20));shape.setStroke(dp(c,1),LINE);layout.setBackground(shape);return layout;
    }
    static void gap(Context c,LinearLayout parent,int size){parent.addView(new View(c),new LinearLayout.LayoutParams(1,dp(c,size)));}
    static void divider(Context c,LinearLayout parent){View line=new View(c);line.setBackgroundColor(LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,1));}
    static TextView avatar(Context c,String name,int size){
        String first=name==null||name.isEmpty()?"约":name.substring(0,name.offsetByCodePoints(0,1));
        int[] fills={Color.rgb(242,225,230),Color.rgb(232,229,243),Color.rgb(235,231,218)};
        int[] inks={ROSE,Color.rgb(112,94,141),Color.rgb(129,112,66)};
        int index=Math.floorMod(name==null?0:name.hashCode(),fills.length);
        TextView view=title(c,first,size>=40?17:15);view.setTextColor(inks[index]);view.setGravity(Gravity.CENTER);
        view.setBackground(new AvatarBackground(c,fills[index],inks[index]));view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return view;
    }
    static TextView chevron(Context c){TextView arrow=text(c,"›",22,MUTED);arrow.setGravity(Gravity.CENTER);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(c,20),-2));return arrow;}
    static View motif(Context c,String key,int size,int ink) {
        View icon=new View(c);PactIcon drawable=new PactIcon(key,ink);icon.setBackground(drawable);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);icon.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return icon;
    }
    static void timelineRail(Context c,LinearLayout row,boolean first,boolean last) {
        row.setBackground(new TimelineRail(c,first,last));row.setPadding(dp(c,24),0,0,last?0:dp(c,22));
    }
    static void meta(Context c,LinearLayout parent,String label,String value){
        View previous=parent.getChildCount()==0?null:parent.getChildAt(parent.getChildCount()-1);
        if(previous!=null&&"pact-meta".equals(previous.getTag()))gap(c,parent,16);
        LinearLayout group=column(c);group.addView(text(c,label,12,MUTED));gap(c,group,6);group.addView(text(c,value,15,INK));
        group.setTag("pact-meta");parent.addView(group,new LinearLayout.LayoutParams(-1,-2));
    }
    static void styleSpinner(Context c,Spinner spinner){
        spinner.setMinimumHeight(dp(c,48));spinner.setPadding(0,0,dp(c,4),0);
        spinner.setBackgroundTintList(ColorStateList.valueOf(MUTED));
        if(spinner.getAdapter()==null)return;
        int selection=spinner.getSelectedItemPosition();java.util.List<String> values=new java.util.ArrayList<>();
        for(int i=0;i<spinner.getAdapter().getCount();i++)values.add(String.valueOf(spinner.getAdapter().getItem(i)));
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<String>(c,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                TextView label=text(c,getItem(position),14,INK);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(0,dp(c,8),dp(c,20),dp(c,8));label.setMinimumHeight(dp(c,48));return label;
            }
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){
                TextView label=text(c,getItem(position),15,INK);label.setGravity(Gravity.CENTER_VERTICAL);label.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));label.setMinimumHeight(dp(c,48));return label;
            }
        };
        spinner.setAdapter(adapter);if(selection>=0)spinner.setSelection(selection);
    }
    static LinearLayout hero(Context c,LinearLayout parent) {
        LinearLayout hero=column(c);hero.setPadding(dp(c,22),dp(c,21),dp(c,22),dp(c,20));
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(248,230,223),Color.rgb(239,220,231),Color.rgb(230,221,239)});
        shape.setCornerRadius(dp(c,28));shape.setStroke(dp(c,1),Color.rgb(255,246,246));hero.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(c,13);params.bottomMargin=dp(c,13);parent.addView(hero,params);return hero;
    }
    static View art(Context c,LinearLayout parent,int heightDp) {
        PactArt art=new PactArt(c);parent.addView(art,new LinearLayout.LayoutParams(-1,dp(c,heightDp)));return art;
    }
    private static Drawable ripple(Drawable drawable,boolean primary) {
        return new RippleDrawable(ColorStateList.valueOf(primary?Color.argb(45,255,255,255):Color.argb(25,169,72,101)),drawable,null);
    }
    private static final class AvatarBackground extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int fill,ink;private final float radius;
        AvatarBackground(Context c,int fill,int ink){this.fill=fill;this.ink=ink;radius=dp(c,12);}
        @Override public void draw(Canvas canvas){RectF area=new RectF(getBounds());paint.setStyle(Paint.Style.FILL);paint.setShader(new android.graphics.LinearGradient(area.left,area.top,area.right,area.bottom,Color.rgb(255,249,244),fill,Shader.TileMode.CLAMP));canvas.drawRoundRect(area,radius,radius,paint);paint.setShader(null);canvas.save();android.graphics.Path outline=new android.graphics.Path();outline.addRoundRect(area,radius,radius,android.graphics.Path.Direction.CW);canvas.clipPath(outline);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(ink);paint.setAlpha(30);canvas.drawCircle(area.right,area.bottom,area.width()*.37f,paint);canvas.drawCircle(area.left,area.top,area.width()*.23f,paint);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);canvas.restore();}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class NavigationAccent extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float unit;
        NavigationAccent(Context c){unit=c.getResources().getDisplayMetrics().density;}
        @Override public void draw(Canvas canvas){float cx=getBounds().exactCenterX(),top=getBounds().top+5*unit;paint.setColor(Color.rgb(241,222,231));canvas.drawRoundRect(cx-24*unit,top,cx+24*unit,top+30*unit,15*unit,15*unit,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class TimelineRail extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float unit,anchor;private final boolean first,last;
        TimelineRail(Context c,boolean first,boolean last){unit=c.getResources().getDisplayMetrics().density;anchor=8*unit*c.getResources().getConfiguration().fontScale;this.first=first;this.last=last;}
        @Override public void draw(Canvas canvas){float x=getBounds().left+5*unit,y=getBounds().top+anchor;paint.setColor(LINE);paint.setStrokeWidth(unit);canvas.drawLine(x,first?y:getBounds().top,x,last?y:getBounds().bottom,paint);paint.setColor(first?Color.rgb(246,228,235):Color.rgb(255,252,250));canvas.drawCircle(x,y,5*unit,paint);paint.setColor(first?ROSE:Color.rgb(180,155,170));canvas.drawCircle(x,y,first?2.5f*unit:2*unit,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class PageBackground extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            float w=getBounds().width(),h=getBounds().height();if(w<=0||h<=0)return;
            canvas.save();canvas.translate(getBounds().left,getBounds().top);paint.setShader(null);paint.setColor(BACKGROUND);canvas.drawRect(0,0,w,h,paint);
            paint.setShader(new RadialGradient(w*.1f,0,w*.95f,new int[]{Color.argb(195,241,214,220),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);
            paint.setShader(new RadialGradient(w,0,w*.85f,new int[]{Color.argb(180,228,213,240),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,w/500));paint.setColor(Color.argb(55,255,255,255));canvas.drawOval(new RectF(w*.48f,-w*.6f,w*1.25f,w*.3f),paint);canvas.drawOval(new RectF(w*.53f,-w*.65f,w*1.3f,w*.25f),paint);paint.setStyle(Paint.Style.FILL);canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
