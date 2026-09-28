package cn.zhiya.pact;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native entry point. Shared facts always come from the authenticated API. */
public final class PactActivity extends Activity {
    private SharedPreferences preferences;
    private ApiClient api;
    private AgreementScreens agreements;
    private RelationshipScreens relationships;
    private JSONObject state=new JSONObject();
    private String baseUrl="",token="",tab="首页",relationshipFilter="";
    private int generation;
    private boolean refreshing,debug;
    private TextView banner;
    private LinearLayout root,body;
    private final Handler handler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        debug=(getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        preferences=getSharedPreferences("pact-session",MODE_PRIVATE);
        token=preferences.getString("token","");
        String configured="";
        try{Bundle meta=getPackageManager().getApplicationInfo(getPackageName(),android.content.pm.PackageManager.GET_META_DATA).metaData;if(meta!=null)configured=meta.getString("cn.zhiya.pact.API_BASE_URL","");}catch(Exception ignored){/* Missing configuration is shown in the login flow. */}
        baseUrl=debug?preferences.getString("baseUrl","http://10.0.2.2:8765"):configured;
        if(!baseUrl.isEmpty())try{baseUrl=PactLabels.validateBaseUrl(baseUrl,debug);}catch(IllegalArgumentException e){baseUrl="";}
        api=new ApiClient(baseUrl,token);
        agreements=new AgreementScreens(this,api,()->state,()->relationshipFilter,this::refresh,this::sessionExpired);
        relationships=new RelationshipScreens(this,api,()->state,()->relationshipFilter,this::chooseRelationship,this::refresh,this::sessionExpired);
        render();if(!token.isEmpty())refresh();
    }
    @Override public void onResume(){super.onResume();handler.postDelayed(()->{if(!isDestroyed()&&!isFinishing()&&!token.isEmpty()&&getWindow().getDecorView().hasWindowFocus())refresh();},350);}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(api!=null)api.close();super.onDestroy();}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(agreements!=null)agreements.onActivityResult(code,result,data);}

    private void shell(){
        root=PactUi.column(this);root.setBackground(PactUi.pageBackground(this));
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=PactUi.column(this);body.setPadding(PactUi.dp(this,22),PactUi.dp(this,14),PactUi.dp(this,22),PactUi.dp(this,28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void render(){if(isDestroyed()||isFinishing())return;shell();if(token.isEmpty()){login(false);return;}
        LinearLayout heading=PactUi.row(this);TextView brand=PactUi.serif(this,"如约",30);brand.setContentDescription("如约");heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(PactUi.iconButton(this,"刷新","refresh",v->refresh()),new LinearLayout.LayoutParams(PactUi.dp(this,48),PactUi.dp(this,48)));
        heading.addView(PactUi.iconButton(this,"账户设置","settings",v->account()),new LinearLayout.LayoutParams(PactUi.dp(this,48),PactUi.dp(this,48)));body.addView(heading);
        banner=PactUi.text(this,refreshing?"正在同步…":"",13,PactUi.MUTED);banner.setContentDescription("同步状态");banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);banner.setVisibility(refreshing?View.VISIBLE:View.GONE);body.addView(banner);
        if(!relationshipExists(relationshipFilter))relationshipFilter="";
        if(tab.equals("首页"))home();else{LinearLayout content=PactUi.column(this);body.addView(content);if(tab.equals("关系"))relationships.render(content);else agreements.render(content,tab.equals("奖励"));}
        LinearLayout navigation=PactUi.row(this);navigation.setPadding(PactUi.dp(this,8),PactUi.dp(this,6),PactUi.dp(this,8),PactUi.dp(this,6));navigation.setBackgroundColor(Color.rgb(255,250,248));
        String[] tabs={"首页","约定","奖励","关系"},labels={"日常","约定","奖励","我们"},icons={"home","book","gift","people"};
        for(int i=0;i<tabs.length;i++){String destination=tabs[i];Button button=PactUi.navigationButton(this,labels[i],icons[i],tab.equals(destination),v->{tab=destination;agreements.resetDetail();render();});button.setContentDescription(destination);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(PactUi.dp(this,3),0,PactUi.dp(this,3),0);navigation.addView(button,params);}root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
    }
    private void login(boolean register){
        body.removeAllViews();body.addView(PactUi.serif(this,getString(R.string.app_name),34));body.addView(PactUi.text(this,getString(R.string.brand_tagline),14,PactUi.GREEN));PactUi.art(this,body,90);body.addView(PactUi.serif(this,"答应的事，\n一起做到。",30));
        body.addView(PactUi.text(this,"把期待说清楚，把每次兑现记下来。",16,PactUi.MUTED));
        LinearLayout card=PactUi.card(this,body);card.addView(PactUi.title(this,register?"创建你的账号":"欢迎回来",23));
        EditText handle=PactUi.input(this,card,"账号","",false);handle.setHint("3–32 位英文、数字或下划线");handle.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText password=PactUi.input(this,card,"密码","",false);password.setHint("至少 10 个字符");password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText name=register?PactUi.input(this,card,"昵称","",false):null;
        TextView error=PactUi.text(this,"",14,PactUi.ORANGE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);
        Button submit=PactUi.button(this,register?"创建账号":"登录",true,null);
        submit.setOnClickListener(v->{if(baseUrl.isEmpty()){error.setText("尚未配置可用的服务地址。");return;}try{JSONObject request=new JSONObject().put("handle",handle.getText().toString()).put("password",password.getText().toString());if(register)request.put("displayName",name.getText().toString());submit.setEnabled(false);int expected=generation;
            api.async(()->api.call(register?"/v1/register":"/v1/login",request),new ApiClient.Callback<JSONObject>(){
                public void success(JSONObject result){if(isDestroyed()||expected!=generation)return;token=result.optString("token");preferences.edit().putString("token",token).apply();api.configure(baseUrl,token);state=new JSONObject();render();refresh();}
                public void failure(ApiClient.ApiError problem){if(isDestroyed()||expected!=generation)return;submit.setEnabled(true);error.setText(problem.getMessage());}
            });
        }catch(Exception e){submit.setEnabled(true);error.setText("请检查账号、密码和昵称。");}});card.addView(submit);
        card.addView(PactUi.button(this,register?"已有账号":"创建账号",false,v->login(!register)));
        body.addView(PactUi.text(this,"只在彼此的关系里分享约定。家长代记会清楚标明来源。",14,PactUi.MUTED));
        if(debug)body.addView(PactUi.button(this,"开发连接设置",false,v->connection()));
    }
    private void refresh(){
        if(token.isEmpty()||refreshing)return;refreshing=true;if(banner!=null){banner.setText("正在同步…");banner.setVisibility(View.VISIBLE);}int expected=generation;
        api.async(()->api.call("/v1/state",null),new ApiClient.Callback<JSONObject>(){
            public void success(JSONObject result){if(isDestroyed()||expected!=generation)return;refreshing=false;state=result;render();}
            public void failure(ApiClient.ApiError error){if(isDestroyed()||expected!=generation)return;refreshing=false;if(error.status==401)sessionExpired();else if(banner!=null)banner.setText(error.getMessage());}
        });
    }
    private void sessionExpired(){generation++;token="";refreshing=false;state=new JSONObject();preferences.edit().remove("token").apply();api.configure(baseUrl,"");agreements.resetDetail();tab="首页";relationshipFilter="";render();}
    private void account(){
        JSONObject user=state.optJSONObject("user");String name=user==null?"当前账号":user.optString("displayName")+" · "+user.optString("handle");
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(name).setMessage("约定保存在当前服务，退出账号不会删除记录。\n首版提醒在应用内更新。").setNegativeButton("返回",null).setPositiveButton("退出登录",(d,w)->logout());
        if(debug)builder.setNeutralButton("开发连接设置",(d,w)->connection());builder.show();
    }
    private void logout(){api.async(()->api.call("/v1/logout",new JSONObject()),new ApiClient.Callback<JSONObject>(){public void success(JSONObject result){if(!isDestroyed())sessionExpired();}public void failure(ApiClient.ApiError error){if(isDestroyed())return;if(error.status==401)sessionExpired();else new AlertDialog.Builder(PactActivity.this).setMessage("退出未完成："+error.getMessage()).setPositiveButton("知道了",null).show();}});}
    private void connection(){
        PactForm form=new PactForm(this,"开发连接设置");EditText address=PactUi.input(this,form.fields,"服务地址",baseUrl,false);form.fields.addView(PactUi.text(this,"模拟器可使用 http://10.0.2.2:8765；更换服务会退出当前账号。",14,PactUi.MUTED));
        form.show("保存连接",v->{try{String next=PactLabels.validateBaseUrl(address.getText().toString(),debug);if(next.equals(baseUrl)){form.dialog.dismiss();return;}baseUrl=next;preferences.edit().putString("baseUrl",baseUrl).remove("token").apply();form.dialog.dismiss();sessionExpired();}catch(IllegalArgumentException e){form.fail(e.getMessage());}});
    }
    private boolean relationshipExists(String id){
        if(id.isEmpty())return true;
        JSONArray rows=state.optJSONArray("relationships");
        for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))return true;}
        return false;
    }
    private void chooseRelationship(){
        PactForm form=new PactForm(this,"查看范围");
        form.fields.addView(PactUi.text(this,"选择后，日常、约定、奖励和我们都会显示这段关系的内容。",14,PactUi.MUTED));
        java.util.List<String> names=new java.util.ArrayList<>(),ids=new java.util.ArrayList<>();names.add("全部关系");ids.add("");
        JSONArray rows=state.optJSONArray("relationships");
        for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null){names.add(row.optString("name"));ids.add(row.optString("id"));}}
        if(!ids.contains(relationshipFilter))relationshipFilter="";
        Spinner filter=new Spinner(this);filter.setContentDescription("筛选关系");filter.setMinimumHeight(PactUi.dp(this,48));filter.setPadding(PactUi.dp(this,12),0,PactUi.dp(this,8),0);filter.setBackground(PactUi.background(Color.argb(190,255,251,250),PactUi.dp(this,18)));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);filter.setAdapter(adapter);filter.setSelection(ids.indexOf(relationshipFilter));form.fields.addView(filter,new LinearLayout.LayoutParams(-1,-2));
        int expected=generation;
        form.show("应用范围",v->{
            if(isDestroyed()||expected!=generation){form.dialog.dismiss();return;}
            String next=ids.get(filter.getSelectedItemPosition());
            if(!relationshipExists(next)){form.fail("关系已变化，请返回后重新选择。");return;}
            relationshipFilter=next;agreements.resetDetail();form.dialog.dismiss();render();
        });
    }
    private void home(){
        JSONObject user=state.optJSONObject("user");String me=user==null?"":user.optString("id");
        body.addView(PactUi.text(this,user==null?"属于我们的日常":user.optString("displayName")+"，欢迎回到我们的日常",13,PactUi.MUTED));
        body.addView(PactUi.serif(this,"日子很长，\n我们慢慢兑现。",30));
        body.addView(PactUi.text(this,"有些小事，因为一起做而变得特别。",14,PactUi.MUTED));
        int accept=0,perform=0,review=0,give=0,receive=0,best=100;JSONArray items=state.optJSONArray("agreements");JSONObject focus=null,rewardFocus=null;
        for(int i=items==null?-1:items.length()-1;i>=0;i--){
            JSONObject item=items.optJSONObject(i);if(item==null||(!relationshipFilter.isEmpty()&&!relationshipFilter.equals(item.optString("relationshipId"))))continue;
            boolean issuer=me.equals(item.optString("issuerId")),executor=me.equals(item.optString("executorId"))||"proxy".equals(item.optString("confirmationMode")),live=activeRelationship(item);
            String task=item.optString("taskState"),reward=item.optString("rewardState");
            if(live&&executor&&task.equals("proposed"))accept++;if(live&&executor&&task.equals("active"))perform++;if(live&&issuer&&task.equals("submitted"))review++;if(issuer&&reward.equals("pending"))give++;if(executor&&(reward.equals("pending")||reward.equals("offered")))receive++;
            int rank=focusRank(item,issuer,executor,live);if(rank<best){focus=item;best=rank;}
            if(rewardFocus==null&&(reward.equals("pending")||reward.equals("offered")))rewardFocus=item;
        }
        if(focus==null){
            LinearLayout empty=PactUi.hero(this,body);empty.addView(PactUi.badge(this,"留一点期待给彼此"));
            empty.addView(PactUi.serif(this,"从一件小事，\n开始我们的约定。",25));PactUi.art(this,empty,110);
            empty.addView(PactUi.text(this,"先说清条件和奖励，对方接受之后，再一起开始。",15,PactUi.MUTED));
            empty.addView(PactUi.button(this,"创建约定",true,v->newAgreement()));
        }else{
            JSONObject chosen=focus;LinearLayout feature=PactUi.hero(this,body);
            feature.addView(PactUi.badge(this,homeStatus(chosen,me)));
            feature.addView(PactUi.serif(this,chosen.optString("title"),26));
            feature.addView(PactUi.text(this,relationName(chosen)+(chosen.optString("confirmationMode").equals("proxy")?" · 家长代记":""),13,PactUi.MUTED));
            feature.addView(PactUi.text(this,chosen.optString("conditions"),15,PactUi.INK));PactUi.art(this,feature,95);
            int target=chosen.optInt("targetCount",1),progress=chosen.optInt("progressCount",0);
            if(target>1){
                ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(target);bar.setProgress(progress);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PactUi.GREEN));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(233,213,227)));bar.setContentDescription("已记录 "+progress+" 次，目标 "+target+" 次");feature.addView(bar,new LinearLayout.LayoutParams(-1,PactUi.dp(this,8)));
                feature.addView(PactUi.text(this,"已记录 "+progress+" / "+target+" 次",14,PactUi.GREEN));
            }else feature.addView(PactUi.text(this,"目标：一次完成",14,PactUi.GREEN));
            Button open=PactUi.button(this,"看看这份约定",true,v->openAgreement(chosen));open.setContentDescription("查看约定："+chosen.optString("title"));feature.addView(open);
            if(rewardFocus==null&&!java.util.Arrays.asList("draft","declined","cancelled","expired").contains(chosen.optString("taskState")))rewardFocus=chosen;
        }
        if(rewardFocus!=null){
            JSONObject promised=rewardFocus;PactUi.sectionTitle(this,body,"还有一份期待","任务达成与奖励兑现，分别记得。");
            LinearLayout reward=PactUi.card(this,body);reward.addView(PactUi.badge(this,PactLabels.reward(promised.optString("rewardState"))));reward.addView(PactUi.serif(this,promised.optString("reward"),21));
            reward.addView(PactUi.text(this,relationName(promised)+" · "+promised.optString("title"),13,PactUi.MUTED));
            reward.addView(PactUi.button(this,"查看奖励",false,v->openAgreement(promised)));
        }
        LinearLayout upcoming=PactUi.card(this,body);upcoming.addView(PactUi.title(this,"轮到我的一步",18));
        upcoming.addView(PactUi.text(this,"待接受 "+accept+"  ·  待完成 "+perform+"  ·  待验收 "+review,14,PactUi.MUTED));
        upcoming.addView(PactUi.text(this,"我该兑现 "+give+"  ·  等对方兑现 / 待收讫 "+receive,14,PactUi.MUTED));
        upcoming.addView(PactUi.button(this,"查看全部约定",false,v->{tab="约定";agreements.resetDetail();render();}));
        if(focus!=null)body.addView(PactUi.button(this,"创建约定",false,v->newAgreement()));
    }
    private void newAgreement(){tab="约定";agreements.resetDetail();render();agreements.create();}
    private void openAgreement(JSONObject item){tab="约定";agreements.open(item.optString("id"));render();}
    private String relationName(JSONObject item){JSONArray rows=state.optJSONArray("relationships");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&row.optString("id").equals(item.optString("relationshipId")))return row.optString("name");}return "我们的约定";}
    private boolean activeRelationship(JSONObject item){JSONArray rows=state.optJSONArray("relationships");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&row.optString("id").equals(item.optString("relationshipId")))return row.optString("status").equals("active");}return false;}
    private int focusRank(JSONObject item,boolean issuer,boolean executor,boolean live){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(issuer&&reward.equals("pending"))return 0;if(executor&&reward.equals("offered"))return 1;
        if(reward.equals("pending")||reward.equals("offered"))return 9;if(!live)return 100;
        if(issuer&&task.equals("submitted"))return 2;if(executor&&task.equals("proposed"))return 3;
        if(task.equals("active"))return executor?4:5;if(task.equals("submitted"))return 6;if(task.equals("proposed"))return 7;if(task.equals("draft"))return 8;
        if(task.equals("expired"))return 10;return 100;
    }
    private String homeStatus(JSONObject item,String me){
        String reward=item.optString("rewardState"),task=item.optString("taskState");
        if(!reward.equals("unearned"))return PactLabels.reward(reward);
        if(task.equals("active")){if(item.optInt("targetCount",1)>1&&item.optInt("progressCount")>=item.optInt("targetCount"))return "次数已够 · 待提交验收";return me.equals(item.optString("executorId"))||item.optString("confirmationMode").equals("proxy")?"正在一起完成":"等待对方完成";}
        return PactLabels.task(task);
    }
}
