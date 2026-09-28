package cn.zhiya.pact;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class PactUi {
    static final int BACKGROUND=Color.rgb(255,250,246), INK=Color.rgb(64,51,60),
            ROSE=Color.rgb(169,72,101), GREEN=ROSE, MUTED=Color.rgb(128,108,120),
            ORANGE=Color.rgb(158,62,68), LAVENDER=Color.rgb(238,229,241),
            BLUSH=Color.rgb(246,231,228), LINE=Color.rgb(235,223,228);
    private PactUi() {}
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }
    static Drawable pageBackground(Context c) { return new PageBackground(); }
    static LinearLayout column(Context c) { LinearLayout layout = new LinearLayout(c); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    static LinearLayout row(Context c) { LinearLayout layout = new LinearLayout(c); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(android.view.Gravity.CENTER_VERTICAL); return layout; }
    static TextView text(Context c, String content, int size, int color) {
        TextView view = new TextView(c); view.setText(content); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(c,3),1); view.setPadding(0,dp(c,3),0,dp(c,5)); return view;
    }
    static TextView title(Context c,String content,int size) { TextView view=text(c,content,size,INK);view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return view; }
    static TextView serif(Context c,String content,int size) {
        TextView view=text(c,content,size,INK);view.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        view.setLetterSpacing(.035f);view.setLineSpacing(dp(c,5),1.02f);return view;
    }
    static TextView badge(Context c,String content) {
        TextView view=text(c,content,12,ROSE);view.setPadding(dp(c,11),dp(c,5),dp(c,11),dp(c,5));
        view.setBackground(background(Color.rgb(246,232,237),dp(c,30)));
        view.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));return view;
    }
    static LinearLayout sectionTitle(Context c,LinearLayout parent,String content,String subtitle) {
        LinearLayout group=column(c);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(c,21);params.bottomMargin=dp(c,8);parent.addView(group,params);
        group.addView(title(c,content,19));if(subtitle!=null&&!subtitle.isEmpty())group.addView(text(c,subtitle,13,MUTED));return group;
    }
    static Button button(Context c, String content, boolean primary, View.OnClickListener click) {
        Button button = new Button(c); button.setText(content); button.setContentDescription(content); button.setAllCaps(false);
        button.setTextSize(15);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{primary?Color.rgb(239,219,228):MUTED,primary?Color.WHITE:ROSE}));
        button.setMinHeight(dp(c,50));button.setMinimumHeight(dp(c,50));button.setMinWidth(0);button.setMinimumWidth(0);
        button.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));button.setLetterSpacing(.025f);
        GradientDrawable shape;
        if(primary){shape=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{ROSE,Color.rgb(151,77,117)});shape.setCornerRadius(dp(c,17));}
        else{shape=background(Color.rgb(245,236,240),dp(c,17));shape.setStroke(dp(c,1),Color.rgb(240,227,234));}
        button.setBackground(ripple(shape,primary));button.setStateListAnimator(null);button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.topMargin = dp(c,8); params.bottomMargin=dp(c,4); button.setLayoutParams(params);
        return button;
    }
    static Button iconButton(Context c,String label,String iconKey,View.OnClickListener listener) {
        Button button=button(c,"",false,listener);button.setContentDescription(label);button.setPadding(dp(c,13),dp(c,13),dp(c,13),dp(c,13));
        button.setMinHeight(dp(c,48));button.setMinimumHeight(dp(c,48));button.setBackground(ripple(background(Color.argb(210,255,252,251),dp(c,24)),false));
        PactIcon icon=new PactIcon(iconKey,ROSE);icon.setBounds(0,0,dp(c,22),dp(c,22));button.setCompoundDrawables(icon,null,null,null);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return button;
    }
    static Button navigationButton(Context c,String label,String iconKey,boolean selected,View.OnClickListener listener) {
        Button button=button(c,label,false,listener);button.setTextSize(11);button.setLetterSpacing(0);button.setGravity(Gravity.CENTER);
        button.setTextColor(selected?ROSE:MUTED);button.setPadding(dp(c,4),dp(c,8),dp(c,4),dp(c,7));
        button.setMinHeight(dp(c,64));button.setMinimumHeight(dp(c,64));button.setBackground(ripple(background(selected?Color.rgb(243,228,234):Color.TRANSPARENT,dp(c,18)),false));
        PactIcon icon=new PactIcon(iconKey,selected?ROSE:MUTED);icon.setBounds(0,0,dp(c,22),dp(c,22));button.setCompoundDrawables(null,icon,null,null);button.setCompoundDrawablePadding(dp(c,4));
        button.setSelected(selected);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(c,3),0,dp(c,3),0);button.setLayoutParams(params);return button;
    }
    static EditText input(Context c, LinearLayout parent, String label, String value, boolean multiline) {
        TextView caption=text(c,label,13,MUTED);parent.addView(caption); EditText edit = new EditText(c); edit.setText(value); edit.setContentDescription(label);
        edit.setId(View.generateViewId());caption.setLabelFor(edit.getId());
        edit.setTextSize(16); edit.setTextColor(INK); edit.setHintTextColor(MUTED); edit.setSingleLine(!multiline);
        edit.setMinHeight(dp(c,48)); if (multiline) { edit.setMinLines(2); edit.setMaxLines(6); edit.setGravity(android.view.Gravity.TOP); }
        edit.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        GradientDrawable field=background(Color.rgb(255,252,251),dp(c,15));field.setStroke(dp(c,1),LINE);edit.setBackground(field);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(c,12); parent.addView(edit,params); return edit;
    }
    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout card = column(c); card.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16));
        GradientDrawable shape=background(Color.rgb(255,253,251),dp(c,24));shape.setStroke(dp(c,1),Color.rgb(245,234,238));card.setBackground(shape);card.setElevation(dp(c,1)); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(c,8); params.bottomMargin=dp(c,8); parent.addView(card,params); return card;
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
    private static final class PageBackground extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            float w=getBounds().width(),h=getBounds().height();if(w<=0||h<=0)return;
            canvas.save();canvas.translate(getBounds().left,getBounds().top);paint.setShader(null);paint.setColor(BACKGROUND);canvas.drawRect(0,0,w,h,paint);
            paint.setShader(new RadialGradient(w*.02f,0,w*.95f,new int[]{Color.rgb(247,224,222),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);
            paint.setShader(new RadialGradient(w,w*.62f,w*.8f,new int[]{Color.argb(170,230,217,237),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
