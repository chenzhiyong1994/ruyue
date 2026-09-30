package cn.zhiya.pact;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One quiet illustration per page; business records remain text-first. */
final class PactMood {
    private static final Bitmap[] IMAGES = new Bitmap[2];
    private static final float RATIO_DAILY = 1200f / 813f, RATIO_REWARD = 1061f / 834f;
    private PactMood() { }

    private static boolean compact(Context context) {
        return context.getResources().getConfiguration().screenWidthDp < 360
                || context.getResources().getConfiguration().fontScale >= 1.3f;
    }

    private static ImageView image(Context context, boolean reward, int maxWidth, int height) {
        int index = reward ? 1 : 0;
        if (IMAGES[index] == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inSampleSize = 2;
            IMAGES[index] = BitmapFactory.decodeResource(context.getResources(),
                    reward ? R.drawable.ruyue_reward_v2 : R.drawable.ruyue_daily_v2, options);
        }
        float ratio = reward ? RATIO_REWARD : RATIO_DAILY;
        int width = Math.min(maxWidth, Math.round(height * ratio));
        height = Math.round(width / ratio);
        ImageView image = new ImageView(context);
        image.setImageBitmap(IMAGES[index]);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setContentDescription(null);
        image.setLayoutParams(new LinearLayout.LayoutParams(PactUi.dp(context, width), PactUi.dp(context, height)));
        return image;
    }

    static void banner(Context context, LinearLayout parent, boolean reward) {
        boolean small = compact(context);
        LinearLayout banner = PactUi.row(context);
        GradientDrawable paper = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                reward ? new int[]{Color.rgb(245,238,248), Color.rgb(232,218,239)}
                        : new int[]{Color.rgb(247,234,220), Color.rgb(242,218,225)});
        paper.setCornerRadius(PactUi.dp(context,24));
        paper.setStroke(PactUi.dp(context,1),Color.argb(180,255,255,255));
        banner.setBackground(paper);
        banner.setPadding(PactUi.dp(context,small?14:18),PactUi.dp(context,12),PactUi.dp(context,small?4:6),PactUi.dp(context,12));
        banner.setMinimumHeight(PactUi.dp(context,small?112:128));
        TextView copy = PactUi.title(context, reward ? "把用心，留给\n值得期待的时刻。" : "把答应的事，\n慢慢做成日常。", 18);
        copy.setTextColor(reward ? PactUi.PLUM : Color.rgb(113,71,81));
        copy.setLineSpacing(PactUi.dp(context,5),1);
        banner.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        banner.addView(image(context,reward,small?84:160,small?72:112));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin = PactUi.dp(context,16);
        parent.addView(banner,params);
    }

    static void emptyIllustration(Context context, LinearLayout parent, boolean reward) {
        ImageView illustration = image(context,reward,168,112);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)illustration.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = PactUi.dp(context,16);
        parent.addView(illustration,params);
    }

    static void welcome(Context context, LinearLayout parent, String name, String tagline) {
        LinearLayout heading = PactUi.row(context);
        LinearLayout words = PactUi.column(context);
        words.addView(PactUi.serif(context,name,32));
        PactUi.gap(context,words,8);
        words.addView(PactUi.text(context,compact(context)?tagline.replace("，","，\n"):tagline,14,PactUi.ROSE));
        heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(image(context,false,compact(context)?84:136,104));
        parent.addView(heading,new LinearLayout.LayoutParams(-1,-2));
    }
}
