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
    private int listScrollY;
    private AttachmentDraft picking;
    private static final int PICK_PHOTO = 4021;

    AgreementScreens(Activity activity, ApiClient api, Supplier<JSONObject> state, Supplier<String> relationshipFilter, Runnable refresh, Runnable expired) {
        this.activity=activity; this.api=api; this.state=state; this.relationshipFilter=relationshipFilter; this.refresh=refresh; this.expired=expired;
    }
    void resetDetail() { detailId="";listScrollY=0; }
    void showAll() { resetDetail();statusFilter="全部"; }
    void open(String agreementId) { detailId=agreementId==null?"":agreementId;listScrollY=0; }
    private ScrollView scrollHost(){
        android.view.ViewParent parent=surface.getParent();
        while(parent!=null){if(parent instanceof ScrollView)return (ScrollView)parent;parent=parent.getParent();}
        return null;
    }
    private void scrollTo(int y){
        LinearLayout target=surface;
        target.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){
                if(target.getViewTreeObserver().isAlive())target.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if(target!=surface)return;ScrollView scroll=scrollHost();if(scroll!=null)scroll.scrollTo(0,y);
            }
        });
        target.requestLayout();
    }
    private JSONArray array(String name) { return state.get().optJSONArray(name)==null?new JSONArray():state.get().optJSONArray(name); }
    private String me() { JSONObject user=state.get().optJSONObject("user"); return user==null?"":user.optString("id"); }
    private JSONObject find(String collection,String id) { JSONArray rows=array(collection); for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("id")))return row;}return null; }
    private JSONObject relation(JSONObject item) { return find("relationships",item.optString("relationshipId")); }
    private boolean proxy(JSONObject item) { return "proxy".equals(item.optString("confirmationMode")); }
    private boolean issuer(JSONObject item) { return me().equals(item.optString("issuerId")); }
    private boolean executor(JSONObject item) { JSONObject r=relation(item); return me().equals(item.optString("executorId"))||(r!=null&&proxy(item)&&me().equals(r.optString("ownerId"))); }
    private boolean activeRelation(JSONObject item) { JSONObject r=relation(item);return r!=null&&"active".equals(r.optString("status")); }
    private String member(JSONObject item,String id) { JSONObject r=relation(item); if(r!=null){JSONArray members=r.optJSONArray("members");for(int i=0;members!=null&&i<members.length();i++){JSONObject m=members.optJSONObject(i);if(id.equals(m.optString("id")))return m.optString("name");}}return "关系成员"; }
    private void text(LinearLayout parent,String value,int size) { if(parent.getChildCount()>0)PactUi.gap(activity,parent,8);parent.addView(PactUi.text(activity,value,size,PactUi.INK)); }
    private void button(LinearLayout parent,String label,boolean primary,Runnable action) { if(parent.getChildCount()>0)PactUi.gap(activity,parent,8);parent.addView(PactUi.button(activity,label,primary,v->action.run())); }

    void render(LinearLayout parent,boolean rewards) {
        surface=parent;this.rewards=rewards;
        if(!detailId.isEmpty()){JSONObject item=find("agreements",detailId);if(item!=null){detail(item);return;}detailId="";}
        List<String> filters=rewards?Arrays.asList("全部","待兑现","已兑现"):Arrays.asList("全部","待接受","待完成","待验收","待兑现","已兑现","草稿","已结束");
        if(!filters.contains(statusFilter))statusFilter="全部";
        Map<String,List<JSONObject>> groups=new LinkedHashMap<>();
        JSONArray agreements=array("agreements");
        for(int i=agreements.length()-1;i>=0;i--){JSONObject item=agreements.optJSONObject(i);if(item==null||!matches(item))continue;
            groups.computeIfAbsent(item.optString("relationshipId"),key->new ArrayList<>()).add(item);
        }
        if(rewards&&!groups.isEmpty())PactMood.banner(activity,parent,true);
        addFilters(parent,filters);
        PactUi.gap(activity,parent,16);
        boolean first=true;
        for(Map.Entry<String,List<JSONObject>> entry:groups.entrySet()){
            LinearLayout group=relationshipGroup(parent,entry.getKey(),entry.getValue().size(),first);first=false;
            boolean firstItem=true;
            for(JSONObject item:entry.getValue()){
                if(!firstItem){LinearLayout inset=PactUi.column(activity);inset.setPadding(PactUi.dp(activity,16),0,PactUi.dp(activity,16),0);PactUi.divider(activity,inset);group.addView(inset);}firstItem=false;
                if(rewards)rewardCard(group,item);else agreementCard(group,item);
            }
        }
        if(groups.isEmpty()){
            LinearLayout empty=PactUi.card(activity,parent);if("全部".equals(statusFilter)&&relationshipFilter.get().isEmpty())PactMood.emptyIllustration(activity,empty,rewards);empty.addView(PactUi.title(activity,rewards?"暂无符合条件的奖励":"暂无符合条件的约定",18));
            PactUi.gap(activity,empty,8);empty.addView(PactUi.text(activity,rewards?"任务达成后，奖励会出现在这里。可调整状态或关系范围查看其他记录。":"可调整状态或关系范围，或创建新的约定。",14,PactUi.MUTED));
        }
    }
    private LinearLayout relationshipGroup(LinearLayout parent,String relationshipId,int count,boolean first){
        if(!first)PactUi.gap(activity,parent,24);
        JSONObject relationship=find("relationships",relationshipId);
        String name=relationship==null?"关系记录":relationship.optString("name");
        LinearLayout heading=PactUi.row(activity);heading.setGravity(android.view.Gravity.TOP);TextView avatar=PactUi.avatar(activity,name,36);
        LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(PactUi.dp(activity,36),PactUi.dp(activity,36));avatarParams.rightMargin=PactUi.dp(activity,12);heading.addView(avatar,avatarParams);
        LinearLayout identity=PactUi.column(activity);heading.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=PactUi.title(activity,name,16);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setContentDescription(name);identity.addView(title);PactUi.gap(activity,identity,4);
        TextView members=PactUi.text(activity,relationshipSummary(relationship),12,PactUi.MUTED);members.setMaxLines(2);members.setEllipsize(android.text.TextUtils.TruncateAt.END);members.setContentDescription(members.getText());identity.addView(members);
        TextView amount=PactUi.badge(activity,count+(rewards?" 份":" 条"));amount.setTextColor(PactUi.MUTED);amount.setBackground(PactUi.background(android.graphics.Color.argb(160,255,255,255),PactUi.dp(activity,8)));amount.setContentDescription(count+(rewards?" 份奖励":" 条约定"));LinearLayout.LayoutParams amountParams=new LinearLayout.LayoutParams(-2,-2);amountParams.leftMargin=PactUi.dp(activity,8);heading.addView(amount,amountParams);
        parent.addView(heading,new LinearLayout.LayoutParams(-1,-2));PactUi.gap(activity,parent,12);
        LinearLayout entries=PactUi.surface(activity);if(rewards)entries.setBackground(PactUi.softSurface(activity,true,20));entries.setPadding(0,0,0,0);entries.setClipToOutline(true);parent.addView(entries,new LinearLayout.LayoutParams(-1,-2));return entries;
    }
    private String relationshipSummary(JSONObject relationship){
        if(relationship==null)return "历史关系";
        List<String> names=new ArrayList<>();JSONArray members=relationship.optJSONArray("members");
        for(int i=0;members!=null&&i<members.length();i++){JSONObject person=members.optJSONObject(i);if(person!=null)names.add(person.optString("name")+(me().equals(person.optString("id"))?"（我）":""));}
        String type=relationship.optString("type"),label="parent_child".equals(type)?"亲子":"couple".equals(type)?"情侣":"自定义关系";
        return String.join(" / ",names)+" · "+label+("proxy".equals(relationship.optString("mode"))?" · 家长代记":"")+("ended".equals(relationship.optString("status"))?" · 已解除":"");
    }
    private LinearLayout entry(LinearLayout parent,JSONObject item,String stateLabel,boolean rewardPage){
        LinearLayout content=PactUi.column(activity);content.setPadding(PactUi.dp(activity,16),PactUi.dp(activity,16),PactUi.dp(activity,16),PactUi.dp(activity,16));parent.addView(content,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout heading=PactUi.row(activity);TextView title=PactUi.title(activity,item.optString(rewardPage?"reward":"title"),17);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(rewardPage){View gift=PactUi.motif(activity,"gift",18,PactUi.PLUM);LinearLayout.LayoutParams giftParams=new LinearLayout.LayoutParams(PactUi.dp(activity,18),PactUi.dp(activity,18));giftParams.rightMargin=PactUi.dp(activity,10);heading.addView(gift,giftParams);}
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);titleParams.rightMargin=PactUi.dp(activity,12);heading.addView(title,titleParams);heading.addView(PactUi.chevron(activity));content.addView(heading);
        if(rewardPage){PactUi.gap(activity,content,8);TextView source=PactUi.text(activity,"来自约定："+item.optString("title"),14,PactUi.MUTED);source.setMaxLines(2);source.setEllipsize(android.text.TextUtils.TruncateAt.END);source.setContentDescription(source.getText());content.addView(source);}
        PactUi.gap(activity,content,8);LinearLayout statusRow=PactUi.row(activity);statusRow.setGravity(android.view.Gravity.TOP);
        TextView state=PactUi.badge(activity,stateLabel);state.setContentDescription(stateLabel);statusRow.addView(state);
        String role=roleSummary(item,rewardPage);TextView roles=PactUi.text(activity,role,12,PactUi.MUTED);roles.setContentDescription(role);
        LinearLayout.LayoutParams rolesParams=new LinearLayout.LayoutParams(0,-2,1);rolesParams.leftMargin=PactUi.dp(activity,10);rolesParams.topMargin=PactUi.dp(activity,4);statusRow.addView(roles,rolesParams);content.addView(statusRow);
        PactUi.clickableEntry(activity,content,"查看约定："+item.optString("title"),v->{ScrollView scroll=scrollHost();listScrollY=scroll==null?0:scroll.getScrollY();detailId=item.optString("id");surface.removeAllViews();detail(item);scrollTo(0);});
        if(android.os.Build.VERSION.SDK_INT>=30)content.setStateDescription(stateLabel);
        return content;
    }
    private void entryNote(LinearLayout parent,String label){
        PactUi.gap(activity,parent,8);TextView note=PactUi.text(activity,label,12,PactUi.MUTED);note.setContentDescription(label);parent.addView(note);
    }
    private String roleSummary(JSONObject item,boolean rewardPage){
        if(proxy(item))return rewardPage?"我兑现 · 家长代记":member(item,item.optString("executorId"))+"完成 · 家长代记";
        if(rewardPage)return issuer(item)?"我兑现 · 对方获得":"对方兑现 · 我获得";
        return issuer(item)?"对方完成 · 我验收":"我完成 · 对方验收";
    }
    private String shortDate(String value){String date=localDate(value);return date.isEmpty()?"不限期":date;}
    private void agreementCard(LinearLayout parent,JSONObject item){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        LinearLayout card=entry(parent,item,PactLabels.agreement(task,reward),false);
        if(task.equals("achieved")){if(!PactLabels.settled(reward))entryNote(card,"兑现截止 "+shortDate(item.optString("rewardDueAt"))+overdue(item.optString("rewardDueAt")));}
        else entryNote(card,(item.optInt("targetCount")>1?item.optInt("progressCount")+" / "+item.optInt("targetCount")+" 次":"一次完成")+" · 截止 "+shortDate(item.optString("dueAt")));
    }
    private TextView rewardSummary(){
        int pending=0;JSONArray rows=array("agreements");
        for(int i=0;i<rows.length();i++){JSONObject item=rows.optJSONObject(i);if(item==null||!"achieved".equals(item.optString("taskState"))||(!relationshipFilter.get().isEmpty()&&!relationshipFilter.get().equals(item.optString("relationshipId"))))continue;if("pending".equals(item.optString("rewardState")))pending++;}
        return PactUi.text(activity,pending+" 份待兑现",12,PactUi.MUTED);
    }
    private void rewardCard(LinearLayout parent,JSONObject item){
        LinearLayout card=entry(parent,item,PactLabels.reward(item.optString("rewardState")),true);
        if(!PactLabels.settled(item.optString("rewardState")))entryNote(card,"兑现截止 "+shortDate(item.optString("rewardDueAt"))+overdue(item.optString("rewardDueAt")));
    }
    private void addFilters(LinearLayout parent,List<String> filters) {
        LinearLayout toolbar=PactUi.row(activity);Spinner status=spinner(toolbar,"筛选状态",filters,filters.indexOf(statusFilter));
        toolbar.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
        if(!rewards){Button create=PactUi.compactButton(activity,"创建约定",true,v->create());LinearLayout.LayoutParams createParams=new LinearLayout.LayoutParams(-2,-2);createParams.leftMargin=PactUi.dp(activity,12);toolbar.addView(create,createParams);}
        else{TextView summary=rewardSummary();LinearLayout.LayoutParams summaryParams=new LinearLayout.LayoutParams(0,-2,2);summaryParams.leftMargin=PactUi.dp(activity,12);summary.setGravity(android.view.Gravity.END);toolbar.addView(summary,summaryParams);}
        parent.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        status.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){String next=filters.get(n);if(!next.equals(statusFilter)){statusFilter=next;surface.removeAllViews();render(surface,rewards);}}});
    }
    private boolean matches(JSONObject item) {
        String selectedRelationship=relationshipFilter.get();
        if(!selectedRelationship.isEmpty()&&!selectedRelationship.equals(item.optString("relationshipId")))return false;
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(rewards){if(!task.equals("achieved"))return false;return statusFilter.equals("全部")||statusFilter.equals(PactLabels.reward(reward));}
        if(statusFilter.equals("全部"))return true;
        if(statusFilter.equals("已结束"))return task.equals("declined")||task.equals("cancelled")||task.equals("expired");
        return statusFilter.equals(PactLabels.agreement(task,reward));
    }
    private Spinner spinner(LinearLayout parent,String label,List<String> values,int selected) {
        Spinner spinner=new Spinner(activity);spinner.setContentDescription(label);spinner.setMinimumHeight(PactUi.dp(activity,48));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selected);PactUi.styleSpinner(activity,spinner);
        TextView measure=PactUi.text(activity,values.get(selected),14,PactUi.INK);int width=Math.round(measure.getPaint().measureText(values.get(selected)))+PactUi.dp(activity,40);
        parent.addView(spinner,new LinearLayout.LayoutParams(width,-2));return spinner;
    }
    void create() {
        List<JSONObject> available=new ArrayList<>();JSONArray rows=array("relationships");
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&"active".equals(row.optString("status")))available.add(row);}
        if(available.isEmpty()){new AlertDialog.Builder(activity).setTitle("请先建立关系").setMessage("在我们页邀请对方，或建立家长代记档案后，再创建约定。").setPositiveButton("知道了",null).show();return;}
        new AgreementCreation(activity,available,me(),relationshipFilter.get(),(form,body)->write(form,"/v1/agreements",body,true));
    }
    private void edit(JSONObject item,boolean amendment) {
        PactForm form=new PactForm(activity,amendment?"修改提案":"编辑约定");
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
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        JSONObject relationship=relation(item);LinearLayout heading=PactUi.row(activity);
        heading.addView(PactUi.iconButton(activity,"返回列表","back",v->{detailId="";surface.removeAllViews();render(surface,rewards);scrollTo(listScrollY);}));
        TextView relationshipName=PactUi.text(activity,relationship==null?"关系记录":relationship.optString("name"),14,PactUi.MUTED);LinearLayout.LayoutParams relationParams=new LinearLayout.LayoutParams(0,-2,1);relationParams.leftMargin=PactUi.dp(activity,8);heading.addView(relationshipName,relationParams);surface.addView(heading);
        PactUi.gap(activity,surface,12);surface.addView(PactUi.title(activity,item.optString("title"),23));PactUi.gap(activity,surface,8);
        surface.addView(PactUi.badge(activity,PactLabels.agreement(task,reward)));
        PactUi.gap(activity,surface,12);surface.addView(PactUi.text(activity,"完成："+member(item,item.optString("executorId"))+" · 验收："+member(item,item.optString("issuerId")),14,PactUi.MUTED));
        if(proxy(item)){PactUi.gap(activity,surface,8);surface.addView(PactUi.text(activity,"家长代记 · 所有代记操作都会保留来源",12,PactUi.MUTED));}
        if(!activeRelation(item))text(surface,"关系已解除，历史保留；已经产生的奖励仍可结清。",13);
        PactUi.gap(activity,surface,12);
        if(task.equals("achieved")&&PactLabels.settled(reward))surface.addView(PactUi.text(activity,proxy(item)?"家长代记已结案":"兑现方已确认，约定已完成",13,PactUi.MUTED));
        else surface.addView(PactUi.text(activity,nextStepHint(item),13,PactUi.MUTED));
        LinearLayout command=PactUi.column(activity);String primary=primaryAction(command,item);
        if(command.getChildCount()>0){PactUi.gap(activity,surface,16);surface.addView(command);}

        PactUi.sectionTitle(activity,surface,"进度","");
        LinearLayout journey=PactUi.card(activity,surface);journey.setBackground(PactUi.softSurface(activity,false,20));
        progress(journey,item,true);
        text(journey,"完成截止："+PactLabels.date(item.optString("dueAt")),13);
        if(task.equals("active")&&item.optInt("targetCount")>1&&item.optInt("progressCount")>=item.optInt("targetCount"))
            text(journey,"次数已经达到目标。提交后，由验收方确认是否达成。",13);

        JSONObject submission=item.optJSONObject("submission");
        if(submission!=null){
            PactUi.sectionTitle(activity,surface,"提交证据","");
            LinearLayout card=PactUi.card(activity,surface);text(card,"本次提交",17);
            if(submission.optBoolean("proxy")){PactUi.gap(activity,card,8);card.addView(PactUi.badge(activity,"家长代记"));}
            text(card,submission.optString("note"),15);images(card,submission.optJSONArray("mediaIds"));
        }

        PactUi.sectionTitle(activity,surface,"约定条款","");
        LinearLayout terms=PactUi.card(activity,surface);
        PactUi.meta(activity,terms,"达成条件",item.optString("conditions"));
        PactUi.meta(activity,terms,"完成截止",PactLabels.date(item.optString("dueAt")));
        PactUi.meta(activity,terms,"完成者",member(item,item.optString("executorId")));
        PactUi.meta(activity,terms,"验收与兑现",member(item,item.optString("issuerId")));

        PactUi.sectionTitle(activity,surface,"奖励","");
        LinearLayout promise=PactUi.card(activity,surface);promise.setBackground(PactUi.softSurface(activity,true,20));
        LinearLayout rewardHeading=PactUi.row(activity);rewardHeading.addView(PactUi.badge(activity,"约定奖励 · "+PactLabels.reward(reward)));rewardHeading.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));rewardHeading.addView(PactUi.motif(activity,"gift",22,PactUi.PLUM));promise.addView(rewardHeading);
        PactUi.gap(activity,promise,12);promise.addView(PactUi.title(activity,item.optString("reward"),17));
        text(promise,"由"+member(item,item.optString("issuerId"))+"给予",14);
        text(promise,"达成后 "+item.optInt("redeemWithinDays")+" 天内兑现",14);
        if(!item.isNull("rewardDueAt"))text(promise,"兑现期限："+PactLabels.date(item.optString("rewardDueAt"))+(PactLabels.settled(reward)?"":overdue(item.optString("rewardDueAt"))),14);

        JSONObject pending=item.optJSONObject("proposal");
        if(pending!=null){
            LinearLayout proposal=PactUi.card(activity,surface);
            boolean settled=PactLabels.settled(reward);
            proposal.addView(PactUi.badge(activity,settled?"历史提案":"等待协商确认"));
            text(proposal,settled?"约定已兑现，此提案不再生效。":pending.optString("kind").equals("cancel")?"提出结束这条约定":"提出条款修改，确认前原条款继续有效",17);
            text(proposal,member(item,pending.optString("by"))+"提出 · "+pending.optString("note"),14);
            JSONObject changes=pending.optJSONObject("changes");
            if(changes!=null)for(Iterator<String> it=changes.keys();it.hasNext();){String key=it.next();text(proposal,fieldName(key)+"："+displayValue(changes,key),15);}
            boolean settlement=task.equals("achieved")&&!PactLabels.settled(reward)&&pending.optString("kind").equals("amend")&&onlyRewardChanges(changes);
            if(!PactLabels.settled(reward)&&(activeRelation(item)||settlement)&&(!me().equals(pending.optString("by"))||proxy(item))){
                button(proposal,"同意提案",primary.isEmpty(),()->noteAction(item,"resolve_proposal","同意提案",true,false));
                button(proposal,"不同意提案",false,()->noteAction(item,"resolve_proposal","不同意提案",false,false));
            }else if(!settled)text(proposal,"等待另一方回应",14);
        }
        moreActions(item,primary,pending);

        PactUi.sectionTitle(activity,surface,"进度记录","");
        LinearLayout eventHistory=PactUi.card(activity,surface);
        JSONArray events=item.optJSONArray("events");
        boolean firstEvent=true;
        for(int i=events==null?-1:events.length()-1;i>=0;i--){JSONObject event=events.optJSONObject(i);if(event==null)continue;
            LinearLayout row=PactUi.column(activity);PactUi.timelineRail(activity,row,firstEvent,i==0);firstEvent=false;eventHistory.addView(row,new LinearLayout.LayoutParams(-1,-2));
            text(row,(event.optBoolean("proxy")?"家长代记 · ":"")+member(item,event.optString("actorId"))+" · "+PactLabels.action(event.optString("action")),15);
            PactUi.gap(activity,row,4);row.addView(PactUi.text(activity,PactLabels.date(event.optString("at")),12,PactUi.MUTED));
            if(!event.optString("note").isEmpty())text(row,event.optString("note"),14);
            JSONObject details=event.optJSONObject("details");
            if(details!=null){JSONObject before=details.optJSONObject("before"),after=details.optJSONObject("after");
                JSONObject step=details.optJSONObject("progress");
                if(step!=null){text(row,"+"+step.optInt("amount")+" 次",14);images(row,step.optJSONArray("mediaIds"));}
                JSONObject submitted=details.optJSONObject("submission");
                if(submitted!=null)images(row,submitted.optJSONArray("mediaIds"));
                if(before!=null&&after!=null)for(Iterator<String> it=after.keys();it.hasNext();){String key=it.next();if(!String.valueOf(before.opt(key)).equals(String.valueOf(after.opt(key))))text(row,fieldName(key)+"："+displayValue(before,key)+" → "+displayValue(after,key),13);}
                if(details.has("approved"))text(row,details.optBoolean("approved")?"结果：同意 / 通过":"结果：需要补充 / 未同意",13);
            }
        }
    }
    private void progress(LinearLayout parent,JSONObject item,boolean prominent){
        int target=item.optInt("targetCount"),completed=item.optInt("progressCount");
        if(target<=1){text(parent,"目标：一次完成",prominent?18:14);return;}
        TextView value=prominent?PactUi.title(activity,"进度："+completed+" / "+target+" 次",21):PactUi.text(activity,"进度："+completed+" / "+target+" 次",14,PactUi.INK);parent.addView(value);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(target);bar.setProgress(completed);
        android.graphics.drawable.GradientDrawable track=PactUi.background(android.graphics.Color.rgb(237,218,225),PactUi.dp(activity,4));
        android.graphics.drawable.GradientDrawable fill=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{android.graphics.Color.rgb(203,127,150),PactUi.ROSE});fill.setCornerRadius(PactUi.dp(activity,4));
        android.graphics.drawable.ClipDrawable progress=new android.graphics.drawable.ClipDrawable(fill,android.view.Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{track,progress});layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);bar.setProgressDrawable(layers);bar.setProgress(completed);bar.setContentDescription("已记录 "+completed+" 次，目标 "+target+" 次");
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,PactUi.dp(activity,8));params.topMargin=PactUi.dp(activity,12);parent.addView(bar,params);
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
            if(issuer(item)&&reward.equals("pending")){button(parent,"标记已兑现",true,()->noteAction(item,"give","标记已兑现",null,false));return "标记已兑现";}
        }
        return "";
    }
    private String nextStepHint(JSONObject item){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        if(task.equals("achieved")){
            if(PactLabels.settled(reward))return "奖励已兑现。";
            return issuer(item)?"任务已达成。请在实际给出奖励后记录。":"任务已达成，等待对方兑现奖励。";
        }
        if(!activeRelation(item))return "关系已解除，保留历史记录。";
        switch(task){
            case "draft":return "草稿仅自己可见。发布后，等待对方接受。";
            case "proposed":return executor(item)?"请核对条款。接受后约定生效。":"等待对方接受。";
            case "active":return executor(item)?"完成后请提交，由对方验收确认。":"等待对方提交完成。";
            case "submitted":return issuer(item)?"完成说明和照片已经提交，请核对证据后验收。":"已经提交，正在等对方验收。奖励尚未进入待兑现。";
            case "expired":return "已超过完成截止。双方可以商量延期，原约定和记录会保留。";
            case "declined":return "约定已被拒绝。";
            default:return "约定已结束，保留历史记录。";
        }
    }
    private void moreActions(JSONObject item,String primary,JSONObject pending){
        String task=item.optString("taskState"),reward=item.optString("rewardState");
        LinearLayout actions=PactUi.surface(activity);actions.setPadding(0,0,0,0);actions.setClipToOutline(true);
        if(activeRelation(item)){
            if(issuer(item)&&(task.equals("draft")||task.equals("proposed")))secondaryAction(actions,"编辑约定",()->edit(item,false));
            if(executor(item)&&task.equals("proposed"))secondaryAction(actions,"拒绝约定",()->noteAction(item,"decline","拒绝约定",null,false));
            if(issuer(item)&&(task.equals("draft")||task.equals("proposed")))secondaryAction(actions,"撤回约定",()->noteAction(item,"withdraw","撤回约定",null,false));
            if(executor(item)&&task.equals("active")){
                if(!primary.equals("记录进度"))secondaryAction(actions,"记录进度",()->evidence(item,true));
                if(!primary.equals("提交完成"))secondaryAction(actions,"提交完成",()->evidence(item,false));
            }
            if(issuer(item)&&task.equals("submitted"))secondaryAction(actions,"退回补充",()->noteAction(item,"review","退回补充",false,true));
        }
        boolean negotiable=task.equals("active")||task.equals("submitted")||task.equals("expired")||(task.equals("achieved")&&!PactLabels.settled(reward));
        if(pending==null&&negotiable&&(activeRelation(item)||task.equals("achieved"))){
            secondaryAction(actions,"提出修改",()->edit(item,true));
            if(!task.equals("achieved"))secondaryAction(actions,"协商取消",()->noteAction(item,"propose_cancel","协商取消",null,true));
        }
        if(actions.getChildCount()>0){PactUi.sectionTitle(activity,surface,"更多操作","");surface.addView(actions,new LinearLayout.LayoutParams(-1,-2));}
    }
    private void secondaryAction(LinearLayout parent,String label,Runnable action){
        if(parent.getChildCount()>0)PactUi.divider(activity,parent);
        LinearLayout row=PactUi.row(activity);row.setMinimumHeight(PactUi.dp(activity,48));row.setPadding(PactUi.dp(activity,16),PactUi.dp(activity,12),PactUi.dp(activity,16),PactUi.dp(activity,12));
        row.addView(PactUi.text(activity,label,15,PactUi.INK),new LinearLayout.LayoutParams(0,-2,1));row.addView(PactUi.chevron(activity));
        PactUi.clickableEntry(activity,row,label,v->action.run());parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private static String fieldName(String key){switch(key){case"title":return"约定标题";case"conditions":return"达成条件";case"reward":return"奖励内容";case"dueAt":return"完成截止";case"targetCount":return"目标次数";case"redeemWithinDays":return"兑现天数";case"rewardDueAt":return"兑现期限";default:return"条款";}}
    private static String displayValue(JSONObject object,String key){if(object.isNull(key))return"不限期";return key.endsWith("At")?PactLabels.date(object.optString(key)):String.valueOf(object.opt(key));}
    private void noteAction(JSONObject item,String action,String title,Boolean approved,boolean required){
        PactForm form=new PactForm(activity,title);
        if(proxy(item)&&(action.equals("accept")||action.equals("resolve_proposal")))text(form.fields,"此操作将记录为家长代记，不显示为孩子独立确认。",14);
        if(action.equals("review")&&Boolean.TRUE.equals(approved))text(form.fields,"通过后任务已达成，你需要按约定兑现奖励。",15);
        if(action.equals("give"))text(form.fields,"请确认约定奖励已经兑现。确认后，这条约定结束。",15);
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
                String summary=PactLabels.agreement(item.optString("taskState"),item.optString("rewardState"))+"\n\n"+item.optString("conditions")+"\n\n奖励："+item.optString("reward")+"\n完成截止："+PactLabels.date(item.optString("dueAt"))+"\n\n当前输入会保留。请核对最新条款，再决定是否重试。";
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
