# 约定首版共享接口 v1

本文件是服务端与 Android 的共同契约。所有 JSON 使用 UTF-8、camelCase；除 /health、register、login 外均需 Authorization: Bearer token。时间是 UTC ISO 8601 字符串，形如 2026-09-26T08:00:00Z；null 表示不限期。服务端时间为准。

## 通用响应与写入

成功返回对应对象，列表放在具名数组字段。错误返回 HTTP 400/401/403/404/409/413/429 和 {"error":{"code":"...","message":"中文可读说明"}}，禁止堆栈或凭证。状态冲突返回 409，客户端重新读取，不自动覆盖。

所有业务 POST 都带 requestId（客户端 UUID）；修改已有对象另带 expectedVersion。服务端在单一事务内验证成员、角色、状态和版本，写入事件与回执。同一 actor、requestId、内容重试返回原结果；不同内容复用 ID 返回 409。register/login/logout 不要求 requestId。读取不会把 submitted/achieved 变为 expired；只有 active 且超过 dueAt 的约定呈现 expired，并仍允许协商延期。

## 账号

- GET /health → {"status":"ok","apiVersion":1}
- POST /v1/register {handle,password,displayName} → {token,user}
- POST /v1/login {handle,password} → {token,user}
- POST /v1/logout {} → {"ok":true}

user = {id,handle,displayName}。handle 是 3–32 位小写字母/数字/下划线；password 10–128 字符；昵称 1–40 字符。密码不能被自动 trim；handle 可以规范化为小写。独立账号不得共用身份来模拟双方确认。

## 状态与关系

GET /v1/state → {user,relationships:[],agreements:[],serverTime}。只返回当前用户所属关系与可读约定；草稿仅创建者可见。约定含完整 events，客户端可以计算待办、奖励列表和未完成数量。解除关系后的历史仍返回，但操作限制见下文。

relationship = {id,name,type,mode,status,version,ownerId,members,inviteCode,inviteExpiresAt,createdAt}。

- type: parent_child / couple / custom。
- mode: shared / proxy。shared 有两名真实账号成员；proxy 有一名家长、一名孩子。
- status: inviting / active / ended。邀请仅有效期内可使用，一次加入后失效；ended 不可重新加入。
- members: [{id,name,kind}]，kind = account / child。成人 id 等于 user.id；孩子 id 是独立稳定 ID。
- inviteCode 仅创建者在 inviting 时可见，其他情况为空。

POST /v1/relationships {requestId,name,type,mode,childName?} → relationship。shared 创建为 inviting，生成 24 小时邀请；proxy 必须 parent_child 且提供 childName，创建为 active。

POST /v1/relationships/join {requestId,inviteCode} → relationship。禁止创建者加入自身邀请，禁止第三名成员加入。

POST /v1/relationships/{id}/actions {requestId,expectedVersion,action,...} → relationship。

- action=end：任一成人成员可以解除，停止新增约定和普通互动，不删除旧记录。
- action=rename：{name} 修改本人在此关系中的称呼，不改对方姓名或协议内容。
- action=renew_invite：仅创建者、inviting 状态可更新邀请，使旧邀请立即失效。

## 约定数据

agreement = {id,relationshipId,issuerId,executorId,title,conditions,reward,dueAt,targetCount,redeemWithinDays,taskState,rewardState,version,progressCount,progress,submission,achievedAt,rewardDueAt,proposal,events,confirmationMode,createdAt,updatedAt}。

- taskState: draft / proposed / active / submitted / achieved / declined / cancelled / expired。
- rewardState: unearned / pending / offered / received。只有 achieved 可有后三种状态。
- confirmationMode: independent / proxy。
- progress: [{id,amount,note,mediaIds,actorId,proxy,at}]。
- submission: null 或 {note,mediaIds,actorId,proxy,at}，历史提交保留于 events。
- proposal: null 或 {id,kind,by,changes,note,at}，kind 为 amend / cancel。
- events: [{id,action,actorId,proxy,at,note,details}]；包含条款变更前后和重要状态变化。
- title 1–100 字，conditions/reward 1–2000 字，备注最多 2000 字。targetCount 1–10000，redeemWithinDays 1–365。首次发布时间的 dueAt 必须在未来或 null。

POST /v1/agreements {requestId,relationshipId,executorId,title,conditions,reward,dueAt,targetCount,redeemWithinDays,publish} → agreement。当前账号为 issuerId；executorId 必须是同关系另一名成员；关系 active 才可创建。publish=false 保存 draft，true 发送为 proposed。给予者与执行者不同，proxy 模式允许孩子作为执行者。

POST /v1/agreements/{id}/actions {requestId,expectedVersion,action,...} → agreement。每次成功的非重复操作 version + 1。下表“执行者”在 proxy 关系可由 owner 代记，事件必须 proxy=true；shared 关系绝不允许给予方代执行者操作。

| action | 操作人、前置 | 字段与效果 |
| --- | --- | --- |
| edit | 给予方，draft/proposed | changes：可编辑条款，proposed 改后仍待接受；记历史 |
| publish | 给予方，draft | 变 proposed，检验期限 |
| accept | 执行者，proposed | 变 active，过期约定不能接受 |
| decline | 执行者，proposed | note 可选，变 declined |
| withdraw | 给予方，draft/proposed | 变 cancelled |
| progress | 执行者，active，未过期 | amount 正整数、note、mediaIds 可选；累积次数，不能超过 targetCount |
| submit | 执行者，active，未过期 | note 或 mediaIds 至少一个；targetCount>1 时 progressCount 必须达标；变 submitted |
| review | 给予方，submitted | approved 布尔；通过变 achieved/pending，生成 rewardDueAt；退回需 note，变 active/expired，原证据保留 |
| give | 给予方，achieved/pending | note 可选，奖励变 offered，等待收讫 |
| receive | 执行者，achieved/offered | 奖励变 received；双确认或家长代记结案文案 |
| dispute | 执行者，achieved/offered | note 必填，回 pending，保留给出记录；不清账 |
| comment | 任一成员，非草稿 | note 必填，留约定内说明 |
| propose_amend | 任一成员，active/submitted/expired 或 achieved 且未收讫 | changes、note；仅一项待决 proposal。普通状态可改条款；achieved 只可改 reward/rewardDueAt，不改达成事实 |
| propose_cancel | 任一成员，active/submitted/expired | note 必填；生成取消提案，achieved 不允许取消 |
| resolve_proposal | 非提案方成员 | approved 布尔；确认 amend 才应用 changes；未来期限使 expired 回 active；确认 cancel 变 cancelled；拒绝保留旧状态；完成后清 proposal |

proxy 关系的 owner 可以代孩子发起或确认，事件始终标明代记。shared 中 proposal.by 不能自行确认。待决提案存在时可以履行原约定；若状态已变化使提案不再合法（例如取消提案等待期间已达成），确认时必须拒绝，不得消掉已产生的奖励。

已 submitted 时，确认变更若实际改变 conditions 或 targetCount，退回 active（超过新截止则 expired），保留累计进度与历史提交事件，清除当前 submission，要求执行者按新条件重新提交。只改变标题、奖励或兑现安排不重置提交。验收通过前仍须检查累计次数达到当前目标。

关系 ended 后只允许已有 achieved 的 give/receive/dispute 及涉及 reward/rewardDueAt 的 propose_amend/resolve_proposal；其他写入拒绝。已 received 不允许重新发奖或改写条款。首版无放弃领取功能，以免单方免除承诺。

## 图片

POST /v1/media {requestId,mime,data} → {id,mime,size}；data 为 base64，仅 JPEG/PNG，原始字节最大 4 MiB，校验文件签名。body 总长度上限 6 MiB。一次进度或提交最多 4 张，只能引用本人上传或该约定已有的图片；未关联图片仅上传者可读。

GET /v1/media/{id} 返回图片原始字节、正确 Content-Type、Cache-Control: no-store。仅上传者或已关联约定的有权成员可以读取；跨关系猜测 ID 返回 404。图片路径不可由请求任意指定。

## 客户端行为

Android 所有网络调用在后台，操作中按钮防重复；网络失败保留输入并提供重试。重试同一动作复用 requestId，获得确定结果后再生成新的 ID。登录令牌失效时回登录，切换账号不保留另一账号的数据视图。显示服务端错误，不把 HTTP 失败视为成功。新状态仅从成功响应或刷新更新。

四页导航共享当前关系筛选；详情中展示状态、期限、完整条件、进度、图片、奖励与时间线。奖励页区分我该给/等对方给/待收讫/已兑现。编辑服务地址属于开发连接设置，正式约定流程不要求用户理解技术字段。
