# CheeseTV｜视频社区后端项目

以 Java 17、Spring Boot 和 MySQL 为核心的视频社区后端，覆盖视频上传与投稿、播放详情、评论、弹幕、搜索和用户登录。仓库保留 Vue 前端用于展示与接口联调；项目重点是后端业务链路、数据一致性和身份校验。

## 项目技术栈

| 领域 | 技术 | 在项目中的用途 |
| --- | --- | --- |
| Web 与持久化 | Spring Boot、MyBatis-Plus、MySQL | 接口、业务逻辑和视频/互动数据 |
| 文件与上传 | MinIO | 视频分片、合并及封面存储 |
| 实时互动 | Netty WebSocket、RocketMQ | 弹幕房间广播与异步入库 |
| 查询与会话 | Redis、Elasticsearch、Canal | 在线会话、热门详情缓存、视频搜索与数据同步 |
| 前端 | Vue 3 | 视频页面与接口联调 |

## 后端关键链路

- **上传与投稿**：前端按 5 MB 分片，后端提供各分片的上传地址并调用 MinIO 合并；文件 URL 记录入库后，投稿接口创建视频及统计数据。
- **弹幕**：浏览器通过 WebSocket 发送弹幕；服务端校验身份和视频房间，经 RocketMQ 异步入库，同时向房间广播。房间可见与 MySQL 持久化发生在不同时间点。
- **视频详情与搜索**：详情由数据库数据组装，热门详情可写入 Redis；搜索使用 Elasticsearch，Canal 负责同步数据库变更。缓存命中不代表整个接口完全不访问数据库。
- **登录身份**：登录后签发 JWT，并在 Redis 保存在线会话；后续请求通过拦截器校验身份，业务代码从已验证的请求上下文取用户 ID。

## 本仓库的重点改造

| 改造点 | 解决的问题与实现 | 主要代码 |
| --- | --- | --- |
| 评论创建鉴权 | 不直接信任请求 JSON 中的 userId；核对登录身份，并将可信 ID 传给评论业务 | [VideoController.java](src/main/java/com/orangecode/tianmu/controller/VideoController.java) |
| 评论及回复删除 | 核对评论作者和视频归属；在事务内锁定视频统计行，按层查找同一视频的后代回复，分批删除并按实际删除数扣减评论计数 | [CommentServiceImpl.java](src/main/java/com/orangecode/tianmu/service/impl/CommentServiceImpl.java) |
| 视频投稿鉴权 | 投稿作者使用已验证的用户身份，避免客户端仅修改 userId 就代替他人投稿 | [VideoServiceImpl.java](src/main/java/com/orangecode/tianmu/service/impl/VideoServiceImpl.java) |
| 上传恢复 | 分片已传完但合并中断时，重试继续执行合并；合并成功前上传进度最多显示 99% | [VideoUpload.vue](cheesetv-front/src/views/platform/children/uploadChildren/VideoUpload.vue) |

评论查询索引迁移位于 [20261119_comment_video_parent.sql](deploy/site/migrations/20261119_comment_video_parent.sql)。相关后端测试位于 [src/test/java/com/orangecode/tianmu/](src/test/java/com/orangecode/tianmu/)。

## 技术取舍与验证

面试时可以结合代码说明这些问题：

1. **为什么评论和投稿不能只信请求里的 userId？** 客户端能自己填写这个字段，身份应以 JWT 与 Redis 会话验证后的用户 ID 为准。
2. **如何避免删除评论后计数不一致？** 删除与计数更新位于同一事务；按实际删除条数更新，更新失败时回滚。
3. **为什么弹幕已经出现在房间里，数据库却可能还没有记录？** 房间广播与消息队列消费入库是两个不同阶段。
4. **为什么上传进度不能在所有分片完成时直接显示 100%？** 分片完成后还需要合并，合并失败应允许重试。

2026-11-20 本地验证：后端 Maven 测试共 85 项，81 项通过、4 项因需要额外环境而跳过，失败 0；后端 JAR 打包成功。前端 11 项现有测试通过，生产构建成功；这些前端测试未覆盖上传恢复。仓库的 [CI](.github/workflows/ci.yml) 配置了前端测试/构建和带隔离 MySQL、Redis 的后端验证。**完整中间件联调与线上部署仍需在实际环境验证。**

## 本地构建

后端需要 JDK 17 与 Maven：

~~~bash
mvn -B -ntp test
mvn -B -ntp package -DskipTests
~~~

前端位于 [cheesetv-front/](cheesetv-front/)；需要 Node.js 22 与 npm：

~~~bash
cd cheesetv-front
npm ci
npm test
npm run build
~~~

运行完整业务需要 MySQL、Redis、MinIO、RocketMQ、Canal 和 Elasticsearch。环境变量参考 [.env.example](.env.example)，部署及数据库脚本见 [deploy/site/README.md](deploy/site/README.md)。部分 Java 包名、数据库名和环境变量仍保留上游的 tianmu 标识，以兼容现有代码与数据。

## 来源与许可

本项目基于 [EchoFrame（原 Tianmu）](https://github.com/blackirlsama/EchoFrame) 的开源代码进行功能改造。仓库保留原项目的 [MIT LICENSE](LICENSE) 与 [NOTICE](NOTICE)；第三方组件说明见 [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md)。
