# 古籍视界 古籍智能标注平台

基于 Spring Boot + Vue3 的古籍智能标注与知识图谱演示项目，覆盖实体/关系/句读标注、文献类型识别及多视图可视化（知识图谱、时间轴、地图、统计信息等）。





https://github.com/user-attachments/assets/e0cfead0-1392-4e88-b4b3-7a9c952bbb8d



## 目录结构

```
E:\iAnctChinese
├── backend          # Spring Boot 3 服务，JPA + MySQL
├── frontend         # Vue 3 + Vite 前端，Element Plus + ECharts + Mapbox
└── README.md
```

## 快速开始

### 1. 后端
1. 安装 JDK 17、Maven、MySQL。
2. 创建数据库：
   ```sql
   CREATE DATABASE IF NOT EXISTS ianct_chinese DEFAULT CHARACTER SET utf8mb4;
   ```
3. 进入 `backend` 目录，安装依赖并启动：
   ```bash
   mvn spring-boot:run
   ```
4. 默认配置（见 `application.yml`）会使用：
   - JDBC：`jdbc:mysql://localhost:3306/ianct_chinese`
   - 用户名：`root`
   - 密码：`QTSqts1030+`
5. 启动后，访问 `http://localhost:8080/swagger-ui/index.html` 可查看 API。

### 2. 前端
1. 进入 `frontend` 目录：
   ```bash
   npm install
   npm run dev
   ```
2. Vite 默认运行在 `http://localhost:5173`，并通过代理将 `/api` 请求转发到 8080 后端。
3. 若需显示地图，可在 `frontend/.env.local` 中配置：
   ```
   VITE_MAPBOX_TOKEN=你的Token
   ```

## 主要功能模块

| 模块 | 描述 |
| --- | --- |
| 文献上传与管理 | `/api/texts`：上传、查询、获取古籍原文，支持后续分类更新。 |
| 类型判定 / 自动标注 | `/api/analysis/{textId}/classify` 结合关键字启发式给出古文类型建议；`/api/analysis/{textId}/auto-annotate` 生成示例实体/关系，方便快速校对。 |
| 实体/关系标注 | `/api/annotations/entities`、`/api/annotations/relations`：CURD 标注信息，支持前端手动录入与模型结果混合。 |
| 句读（结构标注） | `/api/texts/{id}/sections`、`/api/texts/{id}/sections/auto`：拆分与维护段落/句读，并支持自动生成。 |
| 洞察与可视化驱动 | `/api/analysis/{textId}/insights`：产出词云、时间轴、游记轨迹、推荐视图等数据，驱动 Dashboard 多视图。 |
| LLM 作业 | `/api/model-jobs`：为后续接入真实古汉语大模型保留队列接口。 |
| 可视化配置 | `/api/visualizations`：保存按文本类型定制的可视化模板。 |
| 导航与搜索 | `/api/navigation/tree` 输出“项目-类型-文献-章节”树，`/api/texts/search` 支持标题/作者/正文关键词检索。 |
| 导出 | `/api/texts/{id}/export` 将文本+句读+实体+关系打包为 JSON 供分享/备份。 |
| Dashboard 汇总 | `/api/dashboard/overview`：聚合统计指标供右侧属性面板展示。 |

> 当前 `MockLargeLanguageModelClient` 以同步方式返回示例结果，真实接入大模型时只需在该接口中调度外部 API，并在 `ModelJobServiceImpl` 中保留状态转换/错误处理逻辑即可。

## 前端视图说明

- **DashboardView**：左侧为导航树（项目→类型→文献→章节）与过滤器，中间视图区支持知识图谱、时间轴、地图、战争对抗视图、人物亲情树与统计六种切换。顶部「文本类型推断」可一键触发模型判定/自动标注，下方推荐视图标签引导老师切换。全局搜索框触发对话框列出匹配文献，可直接跳转；导出按钮下载 JSON。
- **TextUploadDrawer**：悬浮抽屉支持上传文言文及其元数据，提交后立即进入分析与导航树。
- **TextWorkspace**：内含原文、实体/关系表单及句读面板。句读区域可自动拆分，也可逐条修改+保存，满足“句读（结构标注）”诉求。
- **可视化组件**：
  - `GraphView`：ECharts 力导向图，结合实体/关系过滤与“仅高亮实体”模式。
  - `TimelineView`：跨类型通用时间轴。
  - `MapView`：游记类地图轨迹，未配置 Mapbox Token 时自动回退为列表提示。
  - `BattleTimelineView`：战争类关键节点 + 强度柱线图。
  - `FamilyTreeView`：人物传记亲情/师承树。
  - `StatsPanel`：词云、实体/关系/句读统计与模型洞察摘要。

## 数据建模

- `TextDocument`、`TextSection`：文献及章节
- `EntityAnnotation`、`RelationAnnotation`：实体/关系标注
- `VisualizationPreset`：按类型的可视化配置
- `ModelJob`：大模型任务队列
- `Model分析 DTO`：`ClassificationResponse`、`TextInsightsResponse`、`AutoAnnotationResponse` 为前端多视图提供统一数据契约

参考 `backend/src/main/resources/schema.sql` 可查看完整表结构，`data.sql` 内含示例数据。

## 后续扩展建议

1. **大模型接入**：在 `ModelJobService` 的基础上集成真实的推理服务，并编排异步任务/状态机。
2. **句读/段落管理**：完善 `TextSection` 的编辑、句读进度追踪，结合 LLM 输出建议。
3. **更多可视化模板**：人物传记可扩展亲情树、仕途桑基图；战争类可叠加对抗方颜色区分。
4. **协作与权限**：结合 Spring Security + JWT 实现审校、评论与项目级权限控制。

欢迎根据课程需求继续完善。若遇到问题，可先运行 `mvn test`/`npm run build` 快速验证依赖是否完整，再排查具体实现。祝项目顺利！
