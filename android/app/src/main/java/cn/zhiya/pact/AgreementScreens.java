package cn.zhiya.pact;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Agreement and reward journeys backed exclusively by the shared service. */
final class AgreementScreens {
    private final Activity activity;
    private final ApiClient api;
    private final Supplier<JSONObject> state;
    private final Supplier<String> relationshipFilter;
    private final Runnable refresh, expired;
    private LinearLayout surface;
    private boolean rewards;
    private String detailId = "", statusFilter = "全部";
    private AttachmentDraft picking;
    private static final int PICK_PHOTO = 4021;

    AgreementScreens(Activity activity, ApiClient api, Supplier<JSONObject> state, Supplier<String> relationshipFilter, Runnable refresh, Runnable expired) {
        this.activity=activity; this.api=api; this.state=state; this.relationshipFilter=relationshipFilter; this.refresh=refresh; this.expired=expired;
    }
    void resetDetail() { detailId=""; }
    void open(String agreementId) { detailId=agreementId==null?"":agreementId; }
    private JSONArray array(String name) { return state.get().optJSONArray(name)==null?new JSONArray():state.get().optJSONArray(name); }
    private String me() { JSONObject user=state.get().optJSONObject("user"); return user==null?"":user.optString("id"); }
    private JSONObject find(String collection,String id) { JSONArray rows=array(collection); for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))return row;}return null; }
    private JSONObject relation(JSONObject item) { return find("relationships",item.optString("relationshipId")); }
    private boolean proxy(JSONObject item) { return "proxy".equals(item.optString("confirmationMode")); }
    private boolean issuer(JSONObject item) { return me().equals(item.optString("issuerId")); }
    private boolean executor(JSONObject item) { JSONObject r=relation(item); return me().equals(item.optString("executorId"))||(r!=null&&proxy(item)&&me().equals(r.optString("ownerId"))); }
    private boolean activeRelation(JSONObject item) { JSONObject r=relation(item);return r!=null&&"active".equals(r.optString("status")); }
    private String member(JSONObject item,String id) { JSONObject r=relation(item); if(r!=null){JSONArray members=r.optJSONArray("members");for(int i=0;members!=null&&i<members.length();i++){JSONObject m=members.optJSONObject(i);if(id.equals(m.optString("id")))return m.optString("name");}}return "关系成员"; }
    private void text(LinearLayout parent,String value,int size) { parent.addView(PactUi.text(activity,value,size,PactUi.INK)); }
    private void button(LinearLayout parent,String label,boolean primary,Runnable action) { parent.addView(PactUi.button(activity,label,primary,v->action.run())); }

    void render(LinearLayout parent,boolean rewards) {
        surface=parent;this.rewards=rewards;
        if(!detailId.isEmpty()){JSONObject item=find("agreements",detailId);if(item!=null){detail(item);return;}detailId="";}
        parent.addView(PactUi.text(activity,rewards?"PROMISES, KEPT":"OUR LITTLE PACTS",11,PactUi.MUTED));
        parent.addView(PactUi.serif(activity,rewards?"把心意，\n认真交到彼此手里。":"说好的小事，\n一起慢慢做到。",28));
        parent.addView(PactUi.text(activity,rewards?"任务达成以后，还有一份期待值得兑现。":"每一次认真约定，都是对彼此的在意。",14,PactUi.MUTED));
        if(rewards)rewardSummary(parent);else button(parent,"创建约定",true,this::create);
        addFilters(parent);
        int count=0;
        JSONArray agreements=array("agreements");
        for(int i=agreements.length()-1;i>=0;i--){JSONObject item=agreements.optJSONObject(i);if(item==null||!matches(item))continue;count++;
            if(rewards)rewardCard(parent,item);else agreementCard(parent,item);
        }
        if(count==0){LinearLayout empty=PactUi.hero(activity,parent);PactUi.art(activity,empty,82);empty.addView(PactUi.serif(activity,rewards?"心意，会在这里留下。":"从一件小事开始。",23));text(empty,rewards?"符合筛选条件的已达成奖励，会在这里等待兑现与收讫。":"还没有符合条件的约定。想和重要的人，一起完成什么？",14);}
    }
    private void agreementCard(LinearLayout parent,JSONObject item){
        LinearLayout card=PactUi.card(activity,parent);JSONObject r=relation(item);
        card.addView(PactUi.text(activity,(r==null?"我们的约定":r.optString("name"))+(proxy(item)?" · 家长代记":""),12,PactUi.MUTED));
        card.addView(PactUi.badge(activity,PactLabels.task(item.optString("taskState"))));
        card.addView(PactUi.serif(activity,item.optString("title"),23));
        TextView condition=PactUi.text(activity,item.optString("conditions"),14,PactUi.MUTED);condition.setMaxLines(2);condition.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(condition);
        if(item.optInt("targetCount")>1)progress(card,item,false);
        else card.addView(PactUi.text(activity,"目标：一次完成",13,PactUi.MUTED));
        card.addView(PactUi.text(activity,"答应的奖励 · "+item.optString("reward"),14,PactUi.INK));
        card.addView(PactUi.text(activity,PactLabels.reward(item.optString("rewardState")),12,PactUi.MUTED));
        if("achieved".equals(item.optString("taskState"))&&!"received".equals(item.optString("rewardState")))text(card,"兑现期限："+PactLabels.date(item.optString("rewardDueAt"))+overdue(item.optString("rewardDueAt")),12);
        else if(!"achieved".equals(item.optString("taskState")))text(card,"完成截止："+PactLabels.date(item.optString("dueAt")),12);
        openButton(card,item);
    }
    private void rewardSummary(LinearLayout parent){
        int pending=0,offered=0;JSONArray rows=array("agreements");
        for(int i=0;i<rows.length();i++){JSONObject item=rows.optJSONObject(i);if(item==null||!"achieved".equals(item.optString("taskState"))||(!relationshipFilter.get().isEmpty()&&!relationshipFilter.get().equals(item.optString("relationshipId"))))continue;if("pending".equals(item.optString("rewardState")))pending++;if("offered".equals(item.optString("rewardState")))offered++;}
        LinearLayout hero=PactUi.hero(activity,parent);hero.addView(PactUi.badge(activity,"每份奖励，都有来处"));
        hero.addView(PactUi.serif(activity,pending+" 份待兑现 · "+offered+" 份待收讫",22));
        hero.addView(PactUi.text(activity,"给出是心意，收到后的确认让约定完整。",13,PactUi.MUTED));
    }
    private void rewardCard(LinearLayout parent,JSONObject item){
        LinearLayout card="received".equals(item.optString("rewardState"))?PactUi.card(activity,parent):PactUi.hero(activity,parent);
        card.addView(PactUi.badge(activity,PactLabels.reward(item.optString("rewardState"))));
        TextView title=PactUi.serif(activity,item.optString("reward"),24);title.setMaxLines(4);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);
        card.addView(PactUi.text(activity,"来自约定 · "+item.optString("title"),13,PactUi.MUTED));
        text(card,"任务状态："+PactLabels.task(item.optString("taskState")),13);
        text(card,member(item,item.optString("issuerId"))+"给予 · "+member(item,item.optString("executorId"))+"收讫",14);
        if(proxy(item))card.addView(PactUi.badge(activity,"家长代记 · 操作保留来源"));
        if(!"received".equals(item.optString("rewardState")))text(card,"兑现期限："+PactLabels.date(item.optString("rewardDueAt"))+overdue(item.optString("rewardDueAt")),13);
        else text(card,proxy(item)?"家长代记已结案":"双方已确认，约定全部完成",14);
        openButton(card,item);
    }
    private void openButton(LinearLayout parent,JSONObject item){
        Button open=PactUi.button(activity,"查看约定",false,v->{detailId=item.optString("id");surface.removeAllViews();detail(item);});
        open.setContentDescription("查看约定："+item.optString("title"));parent.addView(open);
    }
    private void addFilters(LinearLayout parent) {
        List<String> filters=rewards?Arrays.asList("全部","我该兑现","等对方兑现","待收讫","已兑现"):Arrays.asList("全部","草稿","待接受","进行中","待验收","已达成","已结束");
        if(!filters.contains(statusFilter))statusFilter="全部";
        Spinner status=spinner(parent,"筛选状态",filters,filters.indexOf(statusFilter));
        status.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){String next=filters.get(n);if(!next.equals(statusFilter)){statusFilter=next;surface.removeAllViews();render(surface,rewards);}}});
    }
    private boolean matches(JSONObject item) {
        String selectedRelationship=relationshipFilter.get();
        if(!selectedRelationship.isEmpty()&&!selectedRelationship.equals(item.optString("relationshipId")))return false;
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(rewards){if(!task.equals("achieved"))return false;switch(statusFilter){case "我该兑现":return issuer(item)&&reward.equals("pending");case "等对方兑现":return executor(item)&&reward.equals("pending");case "待收讫":return reward.equals("offered");case "已兑现":return reward.equals("received");default:return true;}}
        if(statusFilter.equals("全部"))return true;
        if(statusFilter.equals("已结束"))return task.equals("declined")||task.equals("cancelled")||task.equals("expired");
        return statusFilter.equals(PactLabels.task(task));
    }
    private Spinner spinner(LinearLayout parent,String label,List<String> values,int selected) {
        text(parent,label,13);Spinner spinner=new Spinner(activity);spinner.setContentDescription(label);spinner.setMinimumHeight(PactUi.dp(activity,48));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selected);parent.addView(spinner,new LinearLayout.LayoutParams(-1,-2));return spinner;
    }
    void create() {
        List<JSONObject> available=new ArrayList<>();JSONArray rows=array("relationships");
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&"active".equals(row.optString("status")))available.add(row);}
        if(available.isEmpty()){new AlertDialog.Builder(activity).setTitle("先和重要的人，建立连接").setMessage("在我们页邀请对方，或建立家长代记档案，再一起约定一件小事。").setPositiveButton("知道了",null).show();return;}
        new AgreementCreation(activity,available,me(),relationshipFilter.get(),(form,body)->write(form,"/v1/agreements",body,true));
    }
    private void edit(JSONObject item,boolean amendment) {
        PactForm form=new PactForm(activity,amendment?"一起商量修改":"编辑约定");
        boolean earned="achieved".equals(item.optString("taskState"));
        EditText title=earned?null:PactUi.input(activity,form.fields,"约定标题",value(item,"title"),false);
        EditText conditions=earned?null:PactUi.input(activity,form.fields,"达成条件",value(item,"conditions"),true);
        EditText reward=PactUi.input(activity,form.fields,"奖励内容",value(item,"reward"),true);
        EditText count=earned?null:PactUi.input(activity,form.fields,"目标次数",String.valueOf(item.optInt("targetCount")),false);
        EditText days=earned?null:PactUi.input(activity,form.fields,"兑现天数",String.valueOf(item.optInt("redeemWithinDays")),false);
        if(count!=null){count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);days.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);}
        EditText due=PactUi.input(activity,form.fields,earned?"奖励兑现日期":"完成截止日期",localDate(value(item,earned?"rewardDueAt":"dueAt")),false);due.setHint(earned?"yyyy-MM-dd":"yyyy-MM-dd，留空为不限期");
        EditText note=amendment?PactUi.input(activity,form.fields,"修改说明","",true):null;
        form.show(amendment?"提交修改提案":"保存修改",v->{try{
            JSONObject fields=new JSONObject().put("reward",reward.getText().toString());
            String dateKey=earned?"rewardDueAt":"dueAt";
            Object date=due.getText().toString().trim().equals(localDate(value(item,dateKey)))?item.opt(dateKey):dateValue(due.getText().toString(),!earned);
            fields.put(dateKey,date==null?JSONObject.NULL:date);
            if(!earned){fields.put("title",title.getText().toString());fields.put("conditions",conditions.getText().toString());fields.put("targetCount",Integer.parseInt(count.getText().toString()));fields.put("redeemWithinDays",Integer.parseInt(days.getText().toString()));}
            List<String> unchanged=new ArrayList<>();for(Iterator<String> it=fields.keys();it.hasNext();){String key=it.next();if(String.valueOf(fields.opt(key)).equals(String.valueOf(item.opt(key))))unchanged.add(key);}
            for(String key:unchanged)fields.remove(key);
            if(fields.length()==0){form.fail("尚未修改条款。");return;}
            JSONObject body=action(item,amendment?"propose_amend":"edit").put("changes",fields);
            if(note!=null)body.put("note",note.getText().toString());
            write(form,path(item),body,true);
        }catch(Exception e){form.fail("请检查文字、正整数和日期（yyyy-MM-dd）是否填写正确。");}});
    }
    private static String value(JSONObject item,String key){return item==null||item.isNull(key)?"":item.optString(key);}
    private static String localDate(String iso){try{return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toString();}catch(Exception e){return "";}}
    private static Object dateValue(String value,boolean nullable){if(value.trim().isEmpty()&&nullable)return JSONObject.NULL;return LocalDate.parse(value.trim()).atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toString();}
    private String overdue(String date){try{return Instant.parse(date).isBefore(Instant.now())?" · 已逾期":"";}catch(Exception e){return "";}}
    private static String path(JSONObject item){return "/v1/agreements/"+item.optString("id")+"/actions";}
    private static JSONObject action(JSONObject item,String action)throws Exception{return new JSONObject().put("action",action).put("expectedVersion",item.getInt("version"));}

    private void detail(JSONObject item) {
        surface.addView(PactUi.iconButton(activity,"返回列表","back",v->{detailId="";surface.removeAllViews();render(surface,rewards);}));
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        LinearLayout hero=PactUi.hero(activity,surface);
        hero.addView(PactUi.badge(activity,PactLabels.task(task)+" · "+PactLabels.reward(reward)));
        hero.addView(PactUi.serif(activity,item.optString("title"),29));
        hero.addView(PactUi.text(activity,member(item,item.optString("executorId"))+"来完成，"+member(item,item.optString("issuerId"))+"来确认",14,PactUi.MUTED));
        if(proxy(item))hero.addView(PactUi.badge(activity,"家长代记 · 所有代记操作都会保留来源"));
        if(!activeRelation(item))text(hero,"关系已解除，历史保留；已经产生的奖励仍可结清。",13);
        hero.addView(PactUi.text(activity,nextStepHint(item),14,PactUi.MUTED));
        String primary=primaryAction(hero,item);
        if(task.equals("achieved")&&reward.equals("received"))hero.addView(PactUi.serif(activity,proxy(item)?"家长代记已结案":"双方已确认，约定全部完成",22));

        LinearLayout journey=PactUi.card(activity,surface);
        PactUi.sectionTitle(activity,journey,"把每一小步，积攒起来","目标与进度");
        progress(journey,item,true);
        text(journey,"完成截止："+PactLabels.date(item.optString("dueAt")),13);
        if(task.equals("active")&&item.optInt("targetCount")>1&&item.optInt("progressCount")>=item.optInt("targetCount"))
            text(journey,"次数已经达到目标。提交后，由验收方确认是否达成。",13);

        PactUi.sectionTitle(activity,surface,"我们说好的","彼此都能看见的完整条款");
        LinearLayout terms=PactUi.card(activity,surface);
        text(terms,"达成条件",12);text(terms,item.optString("conditions"),17);
        text(terms,"完成截止："+PactLabels.date(item.optString("dueAt")),14);
        text(terms,member(item,item.optString("executorId"))+"负责完成",14);
        text(terms,member(item,item.optString("issuerId"))+"负责验收与兑现",14);

        LinearLayout promise=PactUi.hero(activity,surface);
        promise.addView(PactUi.badge(activity,"约定奖励 · "+PactLabels.reward(reward)));
        promise.addView(PactUi.serif(activity,item.optString("reward"),23));
        text(promise,"由"+member(item,item.optString("issuerId"))+"给予",14);
        text(promise,"达成后 "+item.optInt("redeemWithinDays")+" 天内兑现",14);
        if(!item.isNull("rewardDueAt"))text(promise,"兑现期限："+PactLabels.date(item.optString("rewardDueAt"))+overdue(item.optString("rewardDueAt")),14);
        text(promise,proxy(item)?"收讫由家长代记，并保留实际操作来源。":"给出之后，由"+member(item,item.optString("executorId"))+"确认收到。",13);

        JSONObject pending=item.optJSONObject("proposal");
        if(pending!=null){
            LinearLayout proposal=PactUi.card(activity,surface);
            proposal.addView(PactUi.badge(activity,"等待协商确认"));
            text(proposal,pending.optString("kind").equals("cancel")?"提出结束这条约定":"提出条款修改，确认前原条款继续有效",17);
            text(proposal,member(item,pending.optString("by"))+"提出 · "+pending.optString("note"),14);
            JSONObject changes=pending.optJSONObject("changes");
            if(changes!=null)for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();text(proposal,fieldName(key)+"："+displayValue(changes,key),15);}
            boolean settlement=task.equals("achieved")&&!reward.equals("received")&&pending.optString("kind").equals("amend")&&onlyRewardChanges(changes);
            if((activeRelation(item)||settlement)&&(!me().equals(pending.optString("by"))||proxy(item))){
                button(proposal,"同意提案",primary.isEmpty(),()->noteAction(item,"resolve_proposal","同意提案",true,false));
                button(proposal,"不同意提案",false,()->noteAction(item,"resolve_proposal","不同意提案",false,false));
            }else text(proposal,"等待另一方回应",14);
        }
        moreActions(item,primary,pending);

        JSONObject submission=item.optJSONObject("submission");
        if(submission!=null){
            PactUi.sectionTitle(activity,surface,"这次完成的证据","本次提交");
            LinearLayout card=PactUi.card(activity,surface);text(card,"本次提交",17);
            if(submission.optBoolean("proxy"))card.addView(PactUi.badge(activity,"家长代记"));
            text(card,submission.optString("note"),15);images(card,submission.optJSONArray("mediaIds"));
        }
        JSONArray steps=item.optJSONArray("progress");
        if(steps!=null&&steps.length()>0){
            PactUi.sectionTitle(activity,surface,"一起留下的片刻","每一步进展");
            for(int i=steps.length()-1;i>=0;i--){JSONObject entry=steps.optJSONObject(i);if(entry==null)continue;
                LinearLayout card=PactUi.card(activity,surface);
                card.addView(PactUi.badge(activity,"+"+entry.optInt("amount")+" 次"));
                text(card,(entry.optBoolean("proxy")?"家长代记 · ":"")+member(item,entry.optString("actorId"))+" · "+PactLabels.date(entry.optString("at")),12);
                text(card,entry.optString("note"),15);images(card,entry.optJSONArray("mediaIds"));
            }
        }
        PactUi.sectionTitle(activity,surface,"约定时间线","每一次回应，都认真留下");
        JSONArray events=item.optJSONArray("events");
        for(int i=events==null?-1:events.length()-1;i>=0;i--){JSONObject event=events.optJSONObject(i);if(event==null)continue;
            LinearLayout row=PactUi.card(activity,surface);
            text(row,(event.optBoolean("proxy")?"家长代记 · ":"")+member(item,event.optString("actorId"))+" · "+PactLabels.action(event.optString("action")),15);
            row.addView(PactUi.text(activity,PactLabels.date(event.optString("at")),12,PactUi.MUTED));
            if(!event.optString("note").isEmpty())text(row,event.optString("note"),14);
            JSONObject details=event.optJSONObject("details");
            if(details!=null){JSONObject before=details.optJSONObject("before"),after=details.optJSONObject("after");
                if(before!=null&&after!=null)for(Iterator<String> it=after.keys();it.hasNext();){String key=it.next();if(!String.valueOf(before.opt(key)).equals(String.valueOf(after.opt(key))))text(row,fieldName(key)+"："+displayValue(before,key)+" → "+displayValue(after,key),13);}
                if(details.has("approved"))text(row,details.optBoolean("approved")?"结果：同意 / 通过":"结果：需要补充 / 未同意",13);
            }
        }
    }
    private void progress(LinearLayout parent,JSONObject item,boolean prominent){
        int target=item.optInt("targetCount"),completed=item.optInt("progressCount");
        if(target<=1){text(parent,"目标：一次完成",prominent?21:14);return;}
        TextView value=prominent?PactUi.serif(activity,"进度："+completed+" / "+target+" 次",26):PactUi.text(activity,"进度："+completed+" / "+target+" 次",14,PactUi.INK);parent.addView(value);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(target);bar.setProgress(completed);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PactUi.GREEN));bar.setContentDescription("已记录 "+completed+" 次，目标 "+target+" 次");
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,PactUi.dp(activity,8));params.topMargin=PactUi.dp(activity,8);params.bottomMargin=PactUi.dp(activity,10);parent.addView(bar,params);
    }
    private String primaryAction(LinearLayout parent,JSONObject item){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(activeRelation(item)){
            if(issuer(item)&&task.equals("draft")){button(parent,"发布约定",true,()->noteAction(item,"publish","发布约定",null,false));return "发布约定";}
            if(executor(item)&&task.equals("proposed")){button(parent,"接受约定",true,()->noteAction(item,"accept","接受约定",null,false));return "接受约定";}
            if(executor(item)&&task.equals("active")){
                if(item.optInt("targetCount")>1&&item.optInt("progressCount")<item.optInt("targetCount")){button(parent,"记录进度",true,()->evidence(item,true));return "记录进度";}
                button(parent,"提交完成",true,()->evidence(item,false));return "提交完成";
            }
            if(issuer(item)&&task.equals("submitted")){button(parent,"验收通过",true,()->noteAction(item,"review","验收通过",true,false));return "验收通过";}
        }
        if(task.equals("achieved")){
            if(issuer(item)&&reward.equals("pending")){button(parent,"奖励已给出",true,()->noteAction(item,"give","奖励已给出",null,false));return "奖励已给出";}
            if(executor(item)&&reward.equals("offered")){button(parent,"确认已收到",true,()->noteAction(item,"receive","确认已收到",null,false));return "确认已收到";}
        }
        return "";
    }
    private String nextStepHint(JSONObject item){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(task.equals("achieved")){
            if(reward.equals("received"))return proxy(item)?"每一步代记都有来源，这份约定已经完整结案。":"说好的事，已经认真兑现。谢谢彼此的确认。";
            if(reward.equals("offered"))return executor(item)?"奖励已记录给出。请核对实际收到的内容，再确认收讫。":"已记录给出，正在等对方确认收到。";
            return issuer(item)?"约定已经达成。实际给出奖励后，再在这里留下记录。":"约定已经达成，正在等待答应的奖励。";
        }
        if(!activeRelation(item))return "共同记录继续保留，这段关系不再新增日常互动。";
        switch(task){
            case "draft":return "这一份仍是草稿。发出以后，等对方接受再开始。";
            case "proposed":return executor(item)?"先看清双方说好的条款。接受以后，这份约定才开始。":"期待已经说出口，正在等待对方接受。";
            case "active":return executor(item)?"记录每一小步。提交完成后，还需要对方验收确认。":"对方正在认真完成，一起期待下一次进展。";
            case "submitted":return issuer(item)?"完成说明和照片已经提交，请核对证据后验收。":"已经提交，正在等对方验收。奖励尚未进入待兑现。";
            case "expired":return "已超过完成截止。双方可以商量延期，原约定和记录会保留。";
            case "declined":return "这次没有达成一致，彼此仍然可以开始新的约定。";
            default:return "这份约定已经结束，发生过的每一步继续保留。";
        }
    }
    private void moreActions(JSONObject item,String primary,JSONObject pending){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        LinearLayout actions=PactUi.column(activity);
        if(activeRelation(item)){
            if(issuer(item)&&(task.equals("draft")||task.equals("proposed")))button(actions,"编辑约定",false,()->edit(item,false));
            if(executor(item)&&task.equals("proposed"))button(actions,"拒绝约定",false,()->noteAction(item,"decline","拒绝约定",null,false));
            if(issuer(item)&&(task.equals("draft")||task.equals("proposed")))button(actions,"撤回约定",false,()->noteAction(item,"withdraw","撤回约定",null,false));
            if(executor(item)&&task.equals("active")){
                if(!primary.equals("记录进度"))button(actions,"记录进度",false,()->evidence(item,true));
                if(!primary.equals("提交完成"))button(actions,"提交完成",false,()->evidence(item,false));
            }
            if(issuer(item)&&task.equals("submitted"))button(actions,"退回补充",false,()->noteAction(item,"review","退回补充",false,true));
            if(!task.equals("draft"))button(actions,"留下说明",false,()->noteAction(item,"comment","留下说明",null,true));
        }
        if(task.equals("achieved")&&executor(item)&&reward.equals("offered"))button(actions,"尚未收到",false,()->noteAction(item,"dispute","尚未收到",null,true));
        boolean negotiable=task.equals("active")||task.equals("submitted")||task.equals("expired")||(task.equals("achieved")&&!reward.equals("received"));
        if(pending==null&&negotiable&&(activeRelation(item)||task.equals("achieved"))){
            button(actions,"提出修改",false,()->edit(item,true));
            if(!task.equals("achieved"))button(actions,"协商取消",false,()->noteAction(item,"propose_cancel","协商取消",null,true));
        }
        if(actions.getChildCount()>0){PactUi.sectionTitle(activity,surface,"更多操作","想补充说明，或一起商量改变");surface.addView(actions);}
    }
    private static String fieldName(String key){switch(key){case"title":return"约定标题";case"conditions":return"达成条件";case"reward":return"奖励内容";case"dueAt":return"完成截止";case"targetCount":return"目标次数";case"redeemWithinDays":return"兑现天数";case"rewardDueAt":return"兑现期限";default:return"条款";}}
    private static String displayValue(JSONObject object,String key){if(object.isNull(key))return"不限期";return key.endsWith("At")?PactLabels.date(object.optString(key)):String.valueOf(object.opt(key));}
    private void noteAction(JSONObject item,String action,String title,Boolean approved,boolean required){
        PactForm form=new PactForm(activity,title);
        if(proxy(item)&&(action.equals("accept")||action.equals("receive")||action.equals("resolve_proposal")))text(form.fields,"此操作将记录为家长代记，不显示为孩子独立确认。",14);
        if(action.equals("review")&&Boolean.TRUE.equals(approved))text(form.fields,"通过后任务已达成，你需要按约定兑现奖励。",15);
        if(action.equals("give"))text(form.fields,"请在实际给出奖励后记录。对方确认收到后才会结清。",15);
        if(action.equals("receive"))text(form.fields,"请确认整份奖励已经收到。",15);
        EditText note=PactUi.input(activity,form.fields,"说明","",true);note.setHint(required?"请说明原因":"可选，留下一句话");
        form.show("确认操作",v->{if(required&&note.getText().toString().trim().isEmpty()){form.fail("请填写说明。");return;}try{JSONObject body=action(item,action);body.put("note",note.getText().toString());if(approved!=null)body.put("approved",approved);write(form,path(item),body,false);}catch(Exception e){form.fail("请检查输入后重试。");}});
    }
    private void evidence(JSONObject item,boolean progress){
        PactForm form=new PactForm(activity,progress?"记录进度":"提交完成");if(proxy(item))text(form.fields,"由家长代记，实际操作人会被保留。",14);
        EditText amount=progress?PactUi.input(activity,form.fields,"本次次数","1",false):null;if(amount!=null)amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText note=PactUi.input(activity,form.fields,progress?"进度说明":"完成说明","",true);
        AttachmentDraft draft=new AttachmentDraft(form);form.fields.addView(draft.status);
        button(form.fields,"添加照片",false,()->pick(draft));
        form.show(progress?"保存进度":"确认提交",v->{if(draft.uploading){form.fail("照片正在上传，请稍后提交。");return;}try{JSONObject body=action(item,progress?"progress":"submit");body.put("note",note.getText().toString());body.put("mediaIds",new JSONArray(draft.ids));if(progress)body.put("amount",Integer.parseInt(amount.getText().toString()));write(form,path(item),body,false);}catch(Exception e){form.fail("请检查次数与完成说明。");}});
    }
    private void write(PactForm form,String path,JSONObject body,boolean selectResult){
        if(form.busy)return;
        try{if(form.expectedVersionOverride!=null&&body.has("expectedVersion"))body.put("expectedVersion",form.expectedVersionOverride);JSONObject request=form.request.prepare(body);form.busy(true);api.async(()->api.call(path,request),new ApiClient.Callback<JSONObject>(){
            public void success(JSONObject result){if(activity.isDestroyed())return;form.dialog.dismiss();if(selectResult)detailId=result.optString("id",detailId);refresh.run();}
            public void failure(ApiClient.ApiError error){if(activity.isDestroyed())return;if(error.status==401){form.dialog.dismiss();expired.run();}else{form.fail(error.getMessage());if(error.status==409&&body.has("expectedVersion")&&!form.conflictControlShown){form.conflictControlShown=true;button(form.fields,"核对最新约定",false,()->checkConflict(form,path));}}}
        });}catch(Exception e){form.fail("无法准备请求，请检查输入。");}
    }
    private static boolean onlyRewardChanges(JSONObject changes){if(changes==null)return false;for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();if(!key.equals("reward")&&!key.equals("rewardDueAt"))return false;}return true;}
    private void checkConflict(PactForm form,String path){
        api.async(()->api.call("/v1/state",null),new ApiClient.Callback<JSONObject>(){
            public void success(JSONObject latest){if(activity.isDestroyed()||!form.dialog.isShowing())return;JSONArray rows=latest.optJSONArray("agreements");JSONObject found=null;for(int i=0;rows!=null&&i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(path.equals(path(row))){found=row;break;}}if(found==null){form.fail("此约定当前不可访问，请返回刷新。");return;}JSONObject item=found;
                String summary=PactLabels.task(item.optString("taskState"))+" · "+PactLabels.reward(item.optString("rewardState"))+"\n\n"+item.optString("conditions")+"\n\n奖励："+item.optString("reward")+"\n完成截止："+PactLabels.date(item.optString("dueAt"))+"\n\n当前输入会保留。请核对最新条款，再决定是否重试。";
                new AlertDialog.Builder(activity).setTitle("最新约定").setMessage(summary).setNegativeButton("返回核对",null).setPositiveButton("按新版继续编辑",(d,w)->{form.expectedVersionOverride=item.optInt("version");form.fail("已读取最新版本，请核对输入后再次确认操作。");}).show();}
            public void failure(ApiClient.ApiError error){if(!activity.isDestroyed())form.fail(error.getMessage());}
        });
    }

    private final class AttachmentDraft {
        final PactForm form;final ArrayList<String> ids=new ArrayList<>();final TextView status;
        final PactRequest uploadRequest=new PactRequest();boolean uploading;
        AttachmentDraft(PactForm form){this.form=form;status=PactUi.text(activity,"可添加最多 4 张照片，也可以只用文字说明。",13,PactUi.MUTED);}
    }
    private void pick(AttachmentDraft draft){
        if(draft.uploading)return;
        if(draft.ids.size()>=4){draft.form.fail("每次最多添加 4 张照片。");return;}
        picking=draft;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{activity.startActivityForResult(intent,PICK_PHOTO);}catch(android.content.ActivityNotFoundException e){draft.form.fail("此设备没有可用的图片选择器。");}
    }
    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=PICK_PHOTO)return false;
        AttachmentDraft draft=picking;picking=null;
        if(draft==null||resultCode!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        Uri uri=data.getData();draft.uploading=true;draft.status.setText("照片正在上传…");
        api.async(()->{
            byte[] original;try(InputStream input=activity.getContentResolver().openInputStream(uri)){original=read(input,16*1024*1024);}
            Bitmap bitmap=decode(original,1440);if(bitmap==null)throw new IOException("Cannot decode image");
            byte[] bytes;try(ByteArrayOutputStream output=new ByteArrayOutputStream()){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,85,output))throw new IOException("Cannot prepare image");bytes=output.toByteArray();}finally{bitmap.recycle();}
            if(bytes.length>4*1024*1024)throw new IOException("Image too large");
            JSONObject body=new JSONObject().put("mime","image/jpeg").put("data",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));
            return api.call("/v1/media",draft.uploadRequest.prepare(body));
        },new ApiClient.Callback<JSONObject>(){
            public void success(JSONObject result){draft.uploading=false;if(activity.isDestroyed()||!draft.form.dialog.isShowing())return;if(!draft.ids.contains(result.optString("id")))draft.ids.add(result.optString("id"));draft.status.setText(activity.getString(R.string.photos_added,draft.ids.size()));}
            public void failure(ApiClient.ApiError error){draft.uploading=false;if(activity.isDestroyed())return;draft.status.setText("照片尚未添加，可重新选择重试。文字已保留。");if(error.status==401){draft.form.dialog.dismiss();expired.run();}else draft.form.fail(error.getMessage());}
        });
        return true;
    }
    private void images(LinearLayout parent,JSONArray ids){if(ids==null)return;for(int i=0;i<ids.length();i++){String id=ids.optString(i);button(parent,"查看照片 "+(i+1),false,()->showImage(id));}}
    private void showImage(String id){
        api.async(()->decode(api.media(id),1440),new ApiClient.Callback<Bitmap>(){
            public void success(Bitmap image){if(activity.isDestroyed()){if(image!=null)image.recycle();return;}if(image==null){message("照片暂时无法显示。");return;}ImageView view=new ImageView(activity);view.setContentDescription("约定照片");view.setAdjustViewBounds(true);view.setImageBitmap(image);ScrollView scroll=new ScrollView(activity);scroll.addView(view);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("约定照片").setView(scroll).setPositiveButton("关闭",null).create();dialog.setOnDismissListener(d->{view.setImageDrawable(null);image.recycle();});dialog.show();}
            public void failure(ApiClient.ApiError error){if(activity.isDestroyed())return;if(error.status==401)expired.run();else message(error.getMessage());}
        });
    }
    private void message(String text){new AlertDialog.Builder(activity).setMessage(text).setPositiveButton("知道了",null).show();}
    private static Bitmap decode(byte[] bytes,int maximum){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)return null;int sample=1;while(bounds.outWidth/sample>maximum||bounds.outHeight/sample>maximum)sample*=2;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}
    private static byte[] read(InputStream input,int limit)throws IOException{if(input==null)throw new IOException("Missing image");try(ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("Original image exceeds 16 MiB");out.write(buffer,0,count);}return out.toByteArray();}}
}
