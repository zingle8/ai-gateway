# Maven 私服（Nexus 3）部署指南 —— SDK 跨团队分发

> 目标场景：平台 SDK（`jv-api-gateway-client-starter`）要发给多个业务团队 / 多台机器 / CI 时，
> 部署一台 Maven 私服，**发布一次、所有项目永久可用**，替代逐台机器跑 `install.sh`。
> 与《平台接入指引》1.2 节（`docs/client-integration.md`）配套。

## 一、一键部署

```bash
bash scripts/init-nexus.sh        # Docker 拉起 Nexus 3，端口 28095，数据卷 dist/nexus-data/
bash scripts/init-nexus.sh --check  # 只检查
```

- 端口默认 **28095**（避开平台业务服务 28081~28090 与 Nacos 8080/8848），可用 `NEXUS_PORT=xxx` 覆盖；
- 数据落在宿主机 `dist/nexus-data/`，升级镜像、重建容器都不丢仓库数据；
- 首次启动需 1~2 分钟初始化。

## 二、首次初始化（3 分钟）

```bash
# 1. 取初始管理员密码
docker exec zimo-nexus cat /nexus-data/admin.password
```

2. 浏览器打开 `http://127.0.0.1:28095` → 右上角 Sign in（admin / 初始密码）→ 按向导改密码；
3. 按向导问是否允许匿名访问：**选 Disable anonymous**（仓库发布物含 SDK，不公开）；
4. 什么都不用建——Nexus 自带 `maven-releases`（发布仓）、`maven-snapshots`、`maven-public`（聚合组），直接用 `maven-releases`。

## 三、发布方配置（平台侧，一次性）

`~/.m2/settings.xml` 加发布凭据（`init-nexus.sh` 里改完的 admin 密码，或专门建一个发布账号）：

```xml
<servers>
  <server>
    <id>zimo-releases</id>          <!-- 与 deploy 命令的 -DrepositoryId 对应 -->
    <username>admin</username>
    <password>你的密码</password>
  </server>
</servers>
```

发布（dist/sdk-jar 五个文件，顺序不能反——parent → common → starter）：

```bash
bash scripts/build-sdk-jar.sh && cd dist/sdk-jar/lib

NEXUS=http://127.0.0.1:28095/repository/maven-releases/

mvn deploy:deploy-file -Dfile=api-gateway-1.0.0.pom \
  -DgroupId=com.zimo.gateway -DartifactId=api-gateway -Dversion=1.0.0 -Dpackaging=pom \
  -DrepositoryId=zimo-releases -Durl=$NEXUS

mvn deploy:deploy-file -Dfile=jv-api-gateway-client-common.jar \
  -DpomFile=jv-api-gateway-client-common.pom \
  -DrepositoryId=zimo-releases -Durl=$NEXUS

mvn deploy:deploy-file -Dfile=jv-api-gateway-client-starter.jar \
  -DpomFile=jv-api-gateway-client-starter.pom \
  -DrepositoryId=zimo-releases -Durl=$NEXUS
```

发布完在 Nexus 控制台 Browse → maven-releases 下能见到 `com/zimo/gateway/` 三个构件。

## 四、业务方接入（一次性）

`pom.xml` 声明仓库（或放公司统一 settings.xml）：

```xml
<repositories>
  <repository>
    <id>zimo-releases</id>
    <url>http://<私服IP>:28095/repository/maven-releases/</url>
    <releases><enabled>true</enabled></releases>
  </repository>
</repositories>
```

然后照常引坐标（与《平台接入指引》方式一完全一致）：

```xml
<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-api-gateway-client-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

- Gradle 项目：`repositories { maven { url 'http://<私服IP>:28095/repository/maven-releases/' } }`；
- 私服在内网：业务方与私服网络可达即可，SDK 运行时只连网关（`server-addr`），**不依赖私服**。

## 五、版本升级与运维

| 事项 | 做法 |
| --- | --- |
| SDK 升级 | 改版本号重新 `build-sdk-jar.sh` → deploy 新版本；业务方改依赖版本号，旧版本不受影响 |
| maven-releases 禁止重复发布 | Nexus 默认 Release 仓不允许覆盖同名版本——改版本号再发，这是正确行为 |
| 数据备份 | 停容器后打包 `dist/nexus-data/`（或只备份 `blobs/` + `db/`） |
| 升级镜像 | `docker pull` 新镜像 → `docker stop zimo-nexus && docker rm zimo-nexus` → 重跑 `init-nexus.sh`（数据卷保留） |
| 查日志 | `docker logs -f zimo-nexus` |
| 彻底移除 | `docker rm -f zimo-nexus && rm -rf dist/nexus-data` |

## 六、私服方案 vs 其他分发方式

| 方式 | 适用 | 一次投入 | 增量成本 |
| --- | --- | --- | --- |
| **Nexus 私服（本文）** | 项目多 / 跨部门 / 有 CI | 部署一次（约 10 分钟） | 业务方零成本，像引开源库 |
| 项目内 file:// 仓库 | 一两个项目 | 每个摆一次 | clone 即用，无服务器 |
| install.sh 逐台装 | 临时/单机 | 每台机器一次 | 机器多了很烦 |
| 云制品库（云效/CODING/Packages） | 有云平台不想自建 | 建仓+token | 与私服同协议 |
