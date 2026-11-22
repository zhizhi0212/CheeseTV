# CheeseTV 视频后端容器与数据库

这里保留视频平台后端的镜像构建文件、空库建表脚本和已有数据库升级脚本。当前目录不提供一键启动整套依赖的 Compose 配置；运行后端前需要自行准备 MySQL、Redis、MinIO、Elasticsearch、RocketMQ 和 Canal。

## 构建后端镜像

在仓库根目录执行：

```sh
docker build -f deploy/Dockerfile.business -t cheesetv-backend:local .
```

镜像构建会运行 Maven 的默认测试，随后将 `cheesetv-0.0.1-SNAPSHOT.jar` 放入 `/opt/cheesetv/business.jar`。容器的 HTTP 端口为 `8101`，弹幕 Netty 端口为 `9101`。运行时配置请参考仓库根目录的 `.env.example` 和 `src/main/resources/application.yml`；其中保留的 `TIANMU_*` 环境变量名属于旧配置兼容标识。密码和签名密钥应在运行环境中提供，不要提交到仓库。

`Dockerfile.elasticsearch` 和 `Dockerfile.minio` 是可选的依赖镜像构建文件；`broker.conf` 是 RocketMQ Broker 配置示例。它们本身不会启动服务。

## 数据库脚本

- `business-schema.sql`：用于新建的空业务库，不能当作已有数据库的升级脚本。
- `migrations/20261031_daily_online_coin.sql`：为已有库增加每日上线奖励字段。执行前先确认字段尚不存在。
- `migrations/20261119_comment_video_parent.sql`：为评论回复查询增加索引。执行前先确认同名索引尚不存在。

已有库的迁移应由部署人员在备份和核对当前结构后执行；本仓库的镜像构建不会自动执行上述 SQL。

## 独立数据库验证

每日上线奖励的集成测试需要专用测试库；可设置 `ONLINE_REWARD_IT=true`、`BUSINESS_JDBC_URL`、`BUSINESS_DB_USER` 和 `BUSINESS_DB_PASSWORD` 后运行：

```sh
mvn -B -ntp -Dtest=DailyOnlineRewardIntegrationTest test
```

不要将测试连接指向正在使用的业务库。
