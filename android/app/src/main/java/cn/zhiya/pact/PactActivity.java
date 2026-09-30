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
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);body=PactUi.column(this);body.setPadding(PactUi.dp(this,24),PactUi.dp(this,8),PactUi.dp(this,24),PactUi.dp(this,24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void render(){if(isDestroyed()||isFinishing())return;shell();if(token.isEmpty()){login(false);return;}
        LinearLayout heading=PactUi.row(this);String pageName=tab.equals("首页")?"日常":tab.equals("关系")?"我们":tab;
        LinearLayout titleGroup=PactUi.row(this);heading.addView(titleGroup,new LinearLayout.LayoutParams(0,-2,1));
        TextView pageTitle=PactUi.title(this,pageName,26);pageTitle.setContentDescription(pageName+"页标题");titleGroup.addView(pageTitle);
        View signature=PactUi.motif(this,"rings",24,PactUi.ROSE);signature.setAlpha(.72f);LinearLayout.LayoutParams signatureParams=new LinearLayout.LayoutParams(PactUi.dp(this,24),PactUi.dp(this,24));signatureParams.leftMargin=PactUi.dp(this,12);titleGroup.addView(signature,signatureParams);
        heading.addView(PactUi.iconButton(this,"刷新","refresh",v->refresh()),new LinearLayout.LayoutParams(PactUi.dp(this,48),PactUi.dp(this,48)));
        heading.addView(PactUi.iconButton(this,"账户设置","settings",v->account()),new LinearLayout.LayoutParams(PactUi.dp(this,48),PactUi.dp(this,48)));
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,-2);headingParams.bottomMargin=PactUi.dp(this,16);body.addView(heading,headingParams);
        banner=PactUi.text(this,refreshing?"正在同步…":"",13,PactUi.MUTED);banner.setContentDescription("同步状态");banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);banner.setVisibility(refreshing?View.VISIBLE:View.GONE);body.addView(banner);
        if(!relationshipExists(relationshipFilter))relationshipFilter="";
        if(tab.equals("首页"))home();else{LinearLayout content=PactUi.column(this);body.addView(content);if(tab.equals("关系"))relationships.render(content);else agreements.render(content,tab.equals("奖励"));}
        PactUi.divider(this,root);LinearLayout navigation=PactUi.row(this);navigation.setPadding(PactUi.dp(this,8),PactUi.dp(this,6),PactUi.dp(this,8),PactUi.dp(this,4));navigation.setBackgroundColor(Color.rgb(255,252,250));
        String[] tabs={"首页","约定","奖励","关系"},labels={"日常","约定","奖励","我们"},icons={"home","book","gift","people"};
        for(int i=0;i<tabs.length;i++){String destination=tabs[i];Button button=PactUi.navigationButton(this,labels[i],icons[i],tab.equals(destination),v->{tab=destination;agreements.resetDetail();render();});button.setContentDescription(destination);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(PactUi.dp(this,3),0,PactUi.dp(this,3),0);navigation.addView(button,params);}root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
    }
    private void login(boolean register){
        body.removeAllViews();PactUi.gap(this,body,24);PactMood.welcome(this,body,getString(R.string.app_name),getString(R.string.brand_tagline));PactUi.gap(this,body,24);
        LinearLayout card=PactUi.card(this,body);card.setPadding(PactUi.dp(this,20),PactUi.dp(this,24),PactUi.dp(this,20),PactUi.dp(this,16));card.addView(PactUi.title(this,register?"创建你的账号":"欢迎回来",22));PactUi.gap(this,card,24);
        EditText handle=PactUi.input(this,card,"账号","",false);handle.setHint("3–32 位英文、数字或下划线");handle.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText password=PactUi.input(this,card,"密码","",false);password.setHint("至少 10 个字符");password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText name=register?PactUi.input(this,card,"昵称","",false):null;
        TextView error=PactUi.text(this,"",14,PactUi.ORANGE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);error.setPadding(0,0,0,PactUi.dp(this,8));card.addView(error);
        Button submit=PactUi.button(this,register?"创建账号":"登录",true,null);
        submit.setOnClickListener(v->{if(baseUrl.isEmpty()){error.setText("尚未配置可用的服务地址。");return;}try{JSONObject request=new JSONObject().put("handle",handle.getText().toString()).put("password",password.getText().toString());if(register)request.put("displayName",name.getText().toString());submit.setEnabled(false);int expected=generation;
            api.async(()->api.call(register?"/v1/register":"/v1/login",request),new ApiClient.Callback<JSONObject>(){
                public void success(JSONObject result){if(isDestroyed()||expected!=generation)return;token=result.optString("token");preferences.edit().putString("token",token).apply();api.configure(baseUrl,token);state=new JSONObject();render();refresh();}
                public void failure(ApiClient.ApiError problem){if(isDestroyed()||expected!=generation)return;submit.setEnabled(true);error.setText(problem.getMessage());}
            });
        }catch(Exception e){submit.setEnabled(true);error.setText("请检查账号、密码和昵称。");}});card.addView(submit);
        PactUi.gap(this,card,8);Button alternate=PactUi.textButton(this,register?"已有账号":"创建账号",v->login(!register));LinearLayout.LayoutParams alternateParams=new LinearLayout.LayoutParams(-1,-2);card.addView(alternate,alternateParams);
        PactUi.gap(this,body,8);
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
        PactUi.gap(this,form.fields,16);
        java.util.List<String> names=new java.util.ArrayList<>(),ids=new java.util.ArrayList<>();names.add("全部关系");ids.add("");
        JSONArray rows=state.optJSONArray("relationships");
        for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null){names.add(row.optString("name"));ids.add(row.optString("id"));}}
        if(!ids.contains(relationshipFilter))relationshipFilter="";
        Spinner filter=new Spinner(this);filter.setContentDescription("筛选关系");filter.setMinimumHeight(PactUi.dp(this,48));filter.setPadding(PactUi.dp(this,12),0,PactUi.dp(this,8),0);filter.setBackground(PactUi.background(Color.argb(190,255,251,250),PactUi.dp(this,18)));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);filter.setAdapter(adapter);filter.setSelection(ids.indexOf(relationshipFilter));form.fields.addView(filter,new LinearLayout.LayoutParams(-1,-2));PactUi.gap(this,form.fields,16);
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
        java.util.List<JSONObject> current=new java.util.ArrayList<>();JSONArray items=state.optJSONArray("agreements");
        for(int i=items==null?-1:items.length()-1;i>=0;i--){
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&(relationshipFilter.isEmpty()||relationshipFilter.equals(item.optString("relationshipId")))&&homeRank(item,me)<100)current.add(item);
        }
        current.sort(java.util.Comparator.comparingInt(item->homeRank(item,me)));
        if(!current.isEmpty())PactMood.banner(this,body,false);
        LinearLayout heading=PactUi.row(this);
        heading.addView(PactUi.text(this,current.size()+" 份待跟进",13,PactUi.MUTED),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(PactUi.compactButton(this,"创建约定",true,v->newAgreement()));body.addView(heading);PactUi.gap(this,body,16);
        JSONArray relations=state.optJSONArray("relationships");
        for(int i=0;relations!=null&&i<relations.length();i++){
            JSONObject relation=relations.optJSONObject(i);if(relation==null)continue;
            java.util.List<JSONObject> group=new java.util.ArrayList<>();
            for(JSONObject item:current)if(relation.optString("id").equals(item.optString("relationshipId")))group.add(item);
            if(group.isEmpty())continue;
            LinearLayout relationHeading=PactUi.row(this);relationHeading.setGravity(android.view.Gravity.TOP);TextView avatar=PactUi.avatar(this,relation.optString("name"),36);
            LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(PactUi.dp(this,36),PactUi.dp(this,36));avatarParams.rightMargin=PactUi.dp(this,12);relationHeading.addView(avatar,avatarParams);
            LinearLayout relationText=PactUi.column(this);relationHeading.addView(relationText,new LinearLayout.LayoutParams(0,-2,1));
            TextView relationName=PactUi.title(this,relation.optString("name"),16);relationName.setMaxLines(2);relationName.setEllipsize(android.text.TextUtils.TruncateAt.END);relationName.setContentDescription(relation.optString("name"));relationText.addView(relationName);PactUi.gap(this,relationText,4);
            java.util.List<String> members=new java.util.ArrayList<>();JSONArray people=relation.optJSONArray("members");
            for(int n=0;people!=null&&n<people.length();n++){JSONObject person=people.optJSONObject(n);if(person!=null)members.add(person.optString("name")+(me.equals(person.optString("id"))?"（我）":""));}
            TextView memberLine=PactUi.text(this,String.join(" / ",members)+("proxy".equals(relation.optString("mode"))?" · 家长代记":""),12,PactUi.MUTED);memberLine.setMaxLines(2);memberLine.setEllipsize(android.text.TextUtils.TruncateAt.END);memberLine.setContentDescription(memberLine.getText());relationText.addView(memberLine);
            body.addView(relationHeading);PactUi.gap(this,body,12);
            LinearLayout section=PactUi.surface(this);section.setPadding(0,0,0,0);section.setClipToOutline(true);body.addView(section,new LinearLayout.LayoutParams(-1,-2));
            boolean first=true;
            for(JSONObject item:group){
                if(!first){LinearLayout inset=PactUi.column(this);inset.setPadding(PactUi.dp(this,16),0,PactUi.dp(this,16),0);PactUi.divider(this,inset);section.addView(inset);}first=false;
                LinearLayout entry=PactUi.column(this);entry.setPadding(PactUi.dp(this,16),PactUi.dp(this,16),PactUi.dp(this,16),PactUi.dp(this,16));
                LinearLayout titleRow=PactUi.row(this);TextView title=PactUi.title(this,item.optString("title"),17);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                titleRow.addView(PactUi.chevron(this));entry.addView(titleRow);PactUi.gap(this,entry,8);
                int target=item.optInt("targetCount",1);LinearLayout stateRow=PactUi.row(this);stateRow.setGravity(android.view.Gravity.TOP);stateRow.addView(PactUi.badge(this,homeStatus(item,me)));
                if(target>1){TextView progress=PactUi.text(this,item.optInt("progressCount")+" / "+target+" 次",12,PactUi.MUTED);LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(0,-2,1);progressParams.leftMargin=PactUi.dp(this,10);progressParams.topMargin=PactUi.dp(this,4);stateRow.addView(progress,progressParams);}entry.addView(stateRow);
                boolean earned="achieved".equals(item.optString("taskState"));String deadline=item.optString(earned?"rewardDueAt":"dueAt");if(!deadline.isEmpty()&&!deadline.equals("null")){PactUi.gap(this,entry,6);entry.addView(PactUi.text(this,(earned?"兑现期限 ":"截止 ")+PactLabels.date(deadline),12,PactUi.MUTED));}
                PactUi.clickableEntry(this,entry,"查看约定："+item.optString("title"),v->openAgreement(item));section.addView(entry,new LinearLayout.LayoutParams(-1,-2));
            }
            PactUi.gap(this,body,24);
        }
        if(current.isEmpty()){
            LinearLayout empty=PactUi.card(this,body);PactMood.emptyIllustration(this,empty,false);empty.addView(PactUi.title(this,"暂无待跟进约定",19));
            PactUi.gap(this,empty,8);
            empty.addView(PactUi.text(this,"已完成和已结束的记录可在约定页查看。",14,PactUi.MUTED));
        }
        body.addView(PactUi.textButton(this,"查看全部约定",v->{tab="约定";agreements.showAll();render();}));
    }
    private int homeRank(JSONObject item,String me){
        return focusRank(item,me.equals(item.optString("issuerId")),me.equals(item.optString("executorId"))||"proxy".equals(item.optString("confirmationMode")),activeRelationship(item));
    }
    private void newAgreement(){tab="约定";agreements.resetDetail();render();agreements.create();}
    private void openAgreement(JSONObject item){tab="约定";agreements.open(item.optString("id"));render();}
    private boolean activeRelationship(JSONObject item){JSONArray rows=state.optJSONArray("relationships");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&row.optString("id").equals(item.optString("relationshipId")))return row.optString("status").equals("active");}return false;}
    private int focusRank(JSONObject item,boolean issuer,boolean executor,boolean live){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(PactLabels.settled(reward))return 100;
        if(issuer&&reward.equals("pending"))return 0;
        if(reward.equals("pending"))return 9;if(!live)return 100;
        if(issuer&&task.equals("submitted"))return 2;if(executor&&task.equals("proposed"))return 3;
        if(task.equals("active"))return executor?4:5;if(task.equals("submitted"))return 6;if(task.equals("proposed"))return 7;if(task.equals("draft"))return 8;
        if(task.equals("expired"))return 10;return 100;
    }
    private String homeStatus(JSONObject item,String me){
        String reward=item.optString("rewardState"),task=item.optString("taskState");
        boolean proxy="proxy".equals(item.optString("confirmationMode")),executor=me.equals(item.optString("executorId"))||proxy;
        if(reward.equals("pending"))return me.equals(item.optString("issuerId"))?"待我兑现奖励":"等待对方兑现";
        if(!reward.equals("unearned"))return PactLabels.reward(reward);
        if(task.equals("proposed"))return proxy?"待代记接受":executor?"待我接受":"等待对方接受";
        if(task.equals("submitted"))return me.equals(item.optString("issuerId"))?"待我验收":"等待对方验收";
        if(task.equals("active")){if(item.optInt("targetCount",1)>1&&item.optInt("progressCount")>=item.optInt("targetCount"))return proxy?"待代记提交":executor?"待我提交验收":"等待对方提交";return proxy?"待代记完成":executor?"待我完成":"等待对方完成";}
        return PactLabels.task(task);
    }
}
