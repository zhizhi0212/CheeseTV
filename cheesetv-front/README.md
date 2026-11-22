# CheeseTV 前端

Vue 3 视频平台前端，与仓库根目录的业务后端协作，提供视频浏览、上传、播放、弹幕和互动功能。

## 启动

使用 Node.js 20+ 和 npm：

```bash
cd cheesetv-front
npm ci
npm run serve
```

已登录用户每天上线可获得 1 枚硬币，按北京时间自然日计算，无需重新登录。根组件在可见页面打开、登录态变化、回到前台、恢复联网及每分钟检查时调用 `POST /api/user/online`；访客与隐藏页面不主动请求，后台失败后重试。领取次数以服务端数据库为准，多标签页不会重复发放。客户端检查不重叠，退出或切换账号后丢弃旧响应；日期切换无需刷新页面。旧库发布前的字段迁移见 [网站部署说明](../deploy/site/README.md)。

## 联调配置

将 `.env.example` 复制为 `.env.local` 按需修改，并重启开发服务器：

| 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `TIANMU_API_PROXY_TARGET` | `http://127.0.0.1:8101` | `/api` 转发至业务后端，保留 `/api` 前缀 |
| `VUE_APP_DANMU_WS_BASE_URL` | 开发时为当前主机 `9101`；生产时使用网站同源地址 | 完整弹幕 WebSocket 基址，需带 `/ws/bulletScreen/` 尾路径 |

## 构建与检查

```bash
npm test
npm run build
```

产物输出到 `dist/`。`node_modules/` 和 `dist/` 不提交到 Git。

## 生产接入

Vue 开发代理不进入生产产物。生产 Web 服务器需代理 `/api/` 并配置 SPA 回退；将构建产物复制到静态目录。生产 WebSocket 默认使用网站同源的 `/ws/bulletScreen/`，跟随页面选择 `ws` 或 `wss`。连接独立 WebSocket 域名时，可在构建前设置 `VUE_APP_DANMU_WS_BASE_URL`。后端代理地址改变不需要重新编译；`VUE_APP_` 变量在构建时写入产物。

弹幕连接允许游客接收，发送必须在私有 JSON 帧中带 `authorization`（当前网站 Token）、`videoId`、`content`、`playbackTime`，不在 URL 中携带凭据。后端逐条校验 JWT 与 Redis 当前会话，发送者从凭证取得，视频从连接房间取得；只向未删除、已发布的视频入队。消息 type=3 表示登录失效，type=4 表示参数或发送失败，不清除正常登录态。消息队列和广播不包含 Token。此协议需要前后端一起发布，旧页面需刷新后才能发送；不支持仅凭客户端 userId 发弹幕。浏览器自动响应标准 Ping/Pong 心跳。

房间增量广播使用 `type=5, data=[...]` 批次，每条记录保留原 `bulletId`、`userId`、`text` 和 `playbackTime`。前端一次追加批次中的列表数据，将他人弹幕交给播放器批量追加；同批包含自己的弹幕时继续使用现有发送回显标记。前端仍可接收旧 `type=2` 单条消息。历史弹幕获取、去重合并及一秒等待的发送确认机制在本次房间合批中没有重做。

默认窗口 100 ms、最多 50 条、最多 16384 字节（整个 JSON 帧的 UTF-8 大小），任一条件达到即发送；字节上限不足以放入下一条时先发出当前批次。第一条启动计时，后续消息不会不断延长等待。在线人数、登录失效和错误通知不等待合批。对应业务后端环境变量为 `TIANMU_DANMAKU_BATCH_WINDOW_MS`（1–500）、`TIANMU_DANMAKU_BATCH_MAX_ITEMS`（1–1000）、`TIANMU_DANMAKU_BATCH_MAX_BYTES`（4096–65536），无效配置拒绝启动。新前端兼容旧后端；旧前端不认识批次协议，因此发布时先更新前端，再更新业务后端，并提示已打开旧页面的用户刷新。
