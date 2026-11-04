#!/usr/bin/env bash
#
# 构建网关接入 SDK（client-common + client-starter），并输出可直接分发的 jar 包目录。
#
#   bash scripts/build-sdk-jar.sh
#
# 产出 dist/sdk-jar/
#   lib/api-gateway-1.0.0.pom              ← parent pom，业务方解析依赖时必须能拿到
#   lib/jv-gateway-client-common.jar/.pom  ← 模型与常量
#   lib/jv-gateway-client-starter.jar/.pom ← Starter 本体
#   install.sh                             ← 业务方一条命令装入本地仓库
#   pom-snippet.xml                        ← 直接粘进业务项目 pom 的依赖片段
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/backend"
OUT="$ROOT/dist/sdk-jar"
VERSION="1.0.0"
GROUP="com.zimo.gateway"

if ! command -v mvn >/dev/null 2>&1; then
  echo "错误：未找到 mvn，请先安装 Maven" >&2
  exit 1
fi

echo "==> 构建并安装到本地 Maven 仓库（含 parent pom）"
cd "$BACKEND"
mvn -B -q -DskipTests -pl jv-gateway-client-common,jv-gateway-client-starter -am install

echo "==> 收集分发包"
rm -rf "$OUT"
mkdir -p "$OUT/lib"

cp "$BACKEND/jv-gateway-client-starter/target/jv-gateway-client-starter.jar" "$OUT/lib/"
cp "$BACKEND/jv-gateway-client-common/target/jv-gateway-client-common.jar" "$OUT/lib/"
# 只带模块自身 pom（去掉 <modules>），避免 install-file 解析到不存在的兄弟目录
python3 - "$BACKEND" "$OUT" "$VERSION" <<'PY'
import re, sys, pathlib
backend, out, version = sys.argv[1], sys.argv[2], sys.argv[3]
out = pathlib.Path(out) / 'lib'

# parent pom：保留原样，业务方需要它来解析 starter 的依赖版本
parent = pathlib.Path(backend) / 'pom.xml'
text = parent.read_text(encoding='utf-8')
text = re.sub(r'\s*<modules>.*?</modules>', '', text, flags=re.S)
(out / f'api-gateway-{version}.pom').write_text(text, encoding='utf-8')

for mod in ('jv-gateway-client-common', 'jv-gateway-client-starter'):
    src = pathlib.Path(backend) / mod / 'pom.xml'
    body = src.read_text(encoding='utf-8')
    (out / f'{mod}.pom').write_text(body, encoding='utf-8')
PY

cat > "$OUT/install.sh" <<EOF
#!/usr/bin/env bash
# 把网关接入 SDK 装入本地 Maven 仓库（三件套缺一不可，顺序也有讲究）
#   parent pom -> client-common -> client-starter
set -euo pipefail
cd "\$(dirname "\$0")/lib"

mvn install:install-file -Dfile=api-gateway-$VERSION.pom \\
  -DgroupId=$GROUP -DartifactId=api-gateway -Dversion=$VERSION -Dpackaging=pom

mvn install:install-file -Dfile=jv-gateway-client-common.jar -DpomFile=jv-gateway-client-common.pom
mvn install:install-file -Dfile=jv-gateway-client-starter.jar -DpomFile=jv-gateway-client-starter.pom

echo "安装完成：$GROUP:jv-gateway-client-starter:$VERSION"
EOF
chmod +x "$OUT/install.sh"

cat > "$OUT/pom-snippet.xml" <<EOF
<!-- 装好上面三个文件后，业务项目直接声明依赖即可 -->
<dependency>
  <groupId>$GROUP</groupId>
  <artifactId>jv-gateway-client-starter</artifactId>
  <version>$VERSION</version>
</dependency>
<!-- 注意：starter 里的 spring-boot-starter-web 是 optional，
     业务项目本身是 Web 应用才会有；纯后台任务需要自行引入 -->
EOF

echo "==> 完成，产物目录：$OUT"
echo
ls -1 "$OUT" "$OUT/lib" | sed 's/^/    /'
echo
echo "业务方两种用法："
echo "  1) 本机已装好   → 直接引用 $GROUP:jv-gateway-client-starter:$VERSION"
echo "  2) 只有 jar 包  → 把 dist/sdk-jar 整个拷过去，执行 ./install.sh"
