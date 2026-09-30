# 如约插画素材 / Illustration provenance

如约 1.4.3 使用两幅 AI 生成的扁平插画，提示词保留于本文；通过内置图像生成工具生成，再处理背景、裁切透明边和缩放。它们是界面装饰，不是用户照片、真实物品记录或第三方产品素材。

The two bundled illustrations were generated with an AI image tool from the prompts below, then processed for background transparency, cropping and sizing. They contain no user photos or personal records. They are included with the project under its [MIT license](../../LICENSE), to the extent applicable rights can be licensed. The Gradle Wrapper retains its separate third-party license; see [NOTICE](../../NOTICE).

## 文件与应用位置

| 素材 | 项目文件 | 使用位置 |
| --- | --- | --- |
| 相伴陶杯、信封和小花枝 | [ruyue_daily_v2.png](../../android/app/src/main/res/drawable-nodpi/ruyue_daily_v2.png) | 日常引导区、登录页、关系与约定初始空态 |
| 小礼盒、绸带和卡片 | [ruyue_reward_v2.png](../../android/app/src/main/res/drawable-nodpi/ruyue_reward_v2.png) | 奖励引导区及奖励初始空态 |

日常图为 1200 × 813、奖励图为 1061 × 834，均为 RGBA PNG，随安装包离线提供。`PactMood` 在运行时以 1/2 采样解码并复用位图，避免页面重绘时反复解码；按素材实际宽高比推算展示尺寸，窄屏或大字号缩小侧边插画，文案容器随内容增高。

每页至多出现一组插画。列表条目、约定详情、创建表单沿用原有信息结构；筛选无结果时保留功能说明。新增情绪文案限日常与奖励各一句：

- 日常：把答应的事，慢慢做成日常。
- 奖励：把用心，留给值得期待的时刻。

## 最终生成提示词

### 日常

```text
Flat vector illustration in a soft healing style: two ceramic mugs side by side on a simple round wooden tray — one mug in dusty rose pink (#c98a9b), one in soft lavender (#b8a6c9) — with a small cream envelope sealed with a tiny rose wax stamp in front, and a small sprig of pale pink plum blossoms beside them. Thin warm brown-rose outline strokes (#8a5560) around all shapes, flat matte fills, minimal subtle inner shading only, rounded friendly shapes, generous whitespace, warm pastel palette on transparent background, no text, no shadows on the ground, no photorealism, clean storybook flat style.
```

### 奖励

```text
Flat vector illustration in a soft healing style: a cream gift box (#f7efe4) tied with a wide dusty rose satin ribbon (#c98a9b) in a big friendly bow on top, a small blank standing card in muted lavender (#b8a6c9) leaning beside it, and a small sprig of pale pink plum blossoms at the base. Thin warm brown-rose outline strokes (#8a5560) around all shapes, flat matte fills, minimal subtle inner shading only, rounded friendly shapes, generous whitespace, warm pastel palette on transparent background, no text, no shadows on the ground, no photorealism, clean storybook flat style.
```
