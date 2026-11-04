/* ============================================================
   API网关平台 Demo · K8s 可视化配置生成器
   表单 → YAML 实时渲染 + 校验 + 复制/下载/一键应用（模拟）
   ============================================================ */
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const v = (id) => ($(id) ? String($(id).value).trim() : '');
  const c = (id) => !!($(id) && $(id).checked);
  const pad = (n) => (n < 10 ? '0' + n : '' + n);

  /* ---------- YAML 标量处理 ---------- */
  function q(s) {
    return '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
  }
  function rawOrNum(s) {
    return /^-?\d+(\.\d+)?$/.test(s) ? s : q(s);
  }

  /* ---------- 读取表单 ---------- */
  function readRows(cid) {
    const box = $(cid);
    if (!box) return [];
    return Array.prototype.slice.call(box.querySelectorAll('.dyn-row')).map((r) => ({
      k: r.querySelector('.row-key').value.trim(),
      val: r.querySelector('.row-val').value
    })).filter((x) => x.k);
  }

  function readForm() {
    return {
      ns: v('f-ns'), app: v('f-app'), image: v('f-image'), tag: v('f-tag'),
      replicas: v('f-replicas'), port: v('f-port'), svcPort: v('f-svcPort'), svcType: v('f-svcType'),
      pullPolicy: v('f-pullPolicy'), pullSecret: v('f-pullSecret'),
      cpuReq: v('f-cpuReq'), cpuLimit: v('f-cpuLimit'), memReq: v('f-memReq'), memLimit: v('f-memLimit'),
      probe: c('sw-probe'), livePath: v('f-livePath'), readyPath: v('f-readyPath'),
      probeDelay: v('f-probeDelay'), probePeriod: v('f-probePeriod'),
      ingress: c('rs-ingress'), host: v('f-host'), path: v('f-path'), pathType: v('f-pathType'),
      ingressClass: v('f-ingressClass'), tls: c('sw-tls'),
      hpa: c('rs-hpa'), hpaMin: v('f-hpaMin'), hpaMax: v('f-hpaMax'), hpaCpu: v('f-hpaCpu'), hpaMem: c('sw-hpaMem'), hpaMemPct: v('f-hpaMemPct'),
      maxSurge: v('f-maxSurge'), maxUnavail: v('f-maxUnavail'), preStop: v('f-preStop'), grace: v('f-grace'),
      svc: c('rs-svc'), cm: c('rs-cm'), secret: c('rs-secret'), pdb: c('rs-pdb'), pdbMin: v('f-pdbMin'),
      metrics: c('sw-metrics'), springProfile: v('f-profile'),
      env: readRows('envRows'), cfg: readRows('cmRows'), sec: readRows('secretRows')
    };
  }

  /* ---------- 各资源 YAML 生成 ---------- */
  function genDeployment(f) {
    const L = [];
    L.push('apiVersion: apps/v1');
    L.push('kind: Deployment');
    L.push('metadata:');
    L.push('  name: ' + f.app);
    L.push('  namespace: ' + f.ns);
    L.push('  labels:');
    L.push('    app: ' + f.app);
    L.push('    app.kubernetes.io/name: ' + f.app);
    L.push('    app.kubernetes.io/version: ' + q(f.tag));
    L.push('spec:');
    L.push('  replicas: ' + (f.replicas || 1));
    L.push('  revisionHistoryLimit: 5');
    L.push('  progressDeadlineSeconds: 600');
    L.push('  strategy:');
    L.push('    type: RollingUpdate');
    L.push('    rollingUpdate:');
    L.push('      maxSurge: ' + rawOrNum(f.maxSurge || '25%'));
    L.push('      maxUnavailable: ' + rawOrNum(f.maxUnavail || '0'));
    L.push('  selector:');
    L.push('    matchLabels:');
    L.push('      app: ' + f.app);
    L.push('  template:');
    L.push('    metadata:');
    L.push('      labels:');
    L.push('        app: ' + f.app);
    L.push('        version: ' + q(f.tag));
    L.push('      annotations:');
    if (f.metrics) {
      L.push('        prometheus.io/scrape: "true"');
      L.push('        prometheus.io/port: ' + q(f.port));
      L.push('        prometheus.io/path: /actuator/prometheus');
    }
    L.push('        app.kubernetes.io/managed-by: api-gateway-console');
    L.push('    spec:');
    L.push('      terminationGracePeriodSeconds: ' + (f.grace || 60));
    if (f.pullSecret) {
      L.push('      imagePullSecrets:');
      L.push('        - name: ' + f.pullSecret);
    }
    L.push('      containers:');
    L.push('        - name: ' + f.app);
    L.push('          image: ' + f.image + ':' + f.tag);
    L.push('          imagePullPolicy: ' + f.pullPolicy);
    L.push('          ports:');
    L.push('            - name: http');
    L.push('              containerPort: ' + (f.port || 8080));
    L.push('              protocol: TCP');
    const envList = [];
    if (f.springProfile) envList.push({ k: 'SPRING_PROFILES_ACTIVE', val: f.springProfile });
    f.env.forEach((e) => envList.push(e));
    if (envList.length) {
      L.push('          env:');
      envList.forEach((e) => {
        L.push('            - name: ' + e.k);
        L.push('              value: ' + q(e.val));
      });
    }
    if ((f.cm && f.cfg.length) || (f.secret && f.sec.length)) {
      L.push('          envFrom:');
      if (f.cm && f.cfg.length) {
        L.push('            - configMapRef:');
        L.push('                name: ' + f.app + '-config');
      }
      if (f.secret && f.sec.length) {
        L.push('            - secretRef:');
        L.push('                name: ' + f.app + '-secret');
      }
    }
    L.push('          resources:');
    L.push('            requests:');
    L.push('              cpu: ' + q(f.cpuReq));
    L.push('              memory: ' + q(f.memReq));
    L.push('            limits:');
    L.push('              cpu: ' + q(f.cpuLimit));
    L.push('              memory: ' + q(f.memLimit));
    if (f.probe) {
      L.push('          readinessProbe:');
      L.push('            httpGet:');
      L.push('              path: ' + q(f.readyPath));
      L.push('              port: ' + (f.port || 8080));
      L.push('            initialDelaySeconds: ' + (f.probeDelay || 30));
      L.push('            periodSeconds: ' + (f.probePeriod || 10));
      L.push('            timeoutSeconds: 3');
      L.push('            failureThreshold: 3');
      L.push('          livenessProbe:');
      L.push('            httpGet:');
      L.push('              path: ' + q(f.livePath));
      L.push('              port: ' + (f.port || 8080));
      L.push('            initialDelaySeconds: ' + (Number(f.probeDelay || 30) + 30));
      L.push('            periodSeconds: ' + (f.probePeriod || 10));
      L.push('            timeoutSeconds: 3');
      L.push('            failureThreshold: 3');
    }
    L.push('          lifecycle:');
    L.push('            preStop:');
    L.push('              exec:');
    L.push('                command: ["sh", "-c", "sleep ' + (f.preStop || 30) + '"]');
    L.push('          securityContext:');
    L.push('            runAsNonRoot: true');
    L.push('            allowPrivilegeEscalation: false');
    L.push('            readOnlyRootFilesystem: false');
    return L.join('\n');
  }

  function genService(f) {
    const L = [];
    L.push('apiVersion: v1');
    L.push('kind: Service');
    L.push('metadata:');
    L.push('  name: ' + f.app);
    L.push('  namespace: ' + f.ns);
    L.push('  labels:');
    L.push('    app: ' + f.app);
    L.push('spec:');
    L.push('  type: ' + f.svcType);
    L.push('  selector:');
    L.push('    app: ' + f.app);
    L.push('  ports:');
    L.push('    - name: http');
    L.push('      port: ' + (f.svcPort || 80));
    L.push('      targetPort: ' + (f.port || 8080));
    L.push('      protocol: TCP');
    return L.join('\n');
  }

  function genIngress(f) {
    const L = [];
    L.push('apiVersion: networking.k8s.io/v1');
    L.push('kind: Ingress');
    L.push('metadata:');
    L.push('  name: ' + f.app + '-ingress');
    L.push('  namespace: ' + f.ns);
    L.push('  annotations:');
    L.push('    nginx.ingress.kubernetes.io/proxy-body-size: 32m');
    L.push('    nginx.ingress.kubernetes.io/proxy-read-timeout: "600"');
    L.push('    nginx.ingress.kubernetes.io/proxy-send-timeout: "600"');
    L.push('    nginx.ingress.kubernetes.io/proxy-buffering: "off"');
    L.push('spec:');
    L.push('  ingressClassName: ' + f.ingressClass);
    if (f.tls) {
      L.push('  tls:');
      L.push('    - hosts:');
      L.push('        - ' + f.host);
      L.push('      secretName: ' + f.app + '-tls');
    }
    L.push('  rules:');
    L.push('    - host: ' + f.host);
    L.push('      http:');
    L.push('        paths:');
    L.push('          - path: ' + f.path);
    L.push('            pathType: ' + f.pathType);
    L.push('            backend:');
    L.push('              service:');
    L.push('                name: ' + f.app);
    L.push('                port:');
    L.push('                  number: ' + (f.svcPort || 80));
    return L.join('\n');
  }

  function genHpa(f) {
    const L = [];
    L.push('apiVersion: autoscaling/v2');
    L.push('kind: HorizontalPodAutoscaler');
    L.push('metadata:');
    L.push('  name: ' + f.app + '-hpa');
    L.push('  namespace: ' + f.ns);
    L.push('spec:');
    L.push('  scaleTargetRef:');
    L.push('    apiVersion: apps/v1');
    L.push('    kind: Deployment');
    L.push('    name: ' + f.app);
    L.push('  minReplicas: ' + (f.hpaMin || 2));
    L.push('  maxReplicas: ' + (f.hpaMax || 10));
    L.push('  metrics:');
    L.push('    - type: Resource');
    L.push('      resource:');
    L.push('        name: cpu');
    L.push('        target:');
    L.push('          type: Utilization');
    L.push('          averageUtilization: ' + (f.hpaCpu || 70));
    if (f.hpaMem) {
      L.push('    - type: Resource');
      L.push('      resource:');
      L.push('        name: memory');
      L.push('        target:');
      L.push('          type: Utilization');
      L.push('          averageUtilization: ' + (f.hpaMemPct || 80));
    }
    L.push('  behavior:');
    L.push('    scaleUp:');
    L.push('      stabilizationWindowSeconds: 60');
    L.push('      policies:');
    L.push('        - type: Percent');
    L.push('          value: 100');
    L.push('          periodSeconds: 30');
    L.push('    scaleDown:');
    L.push('      stabilizationWindowSeconds: 300');
    L.push('      policies:');
    L.push('        - type: Pods');
    L.push('          value: 1');
    L.push('          periodSeconds: 60');
    return L.join('\n');
  }

  function genConfigMap(f) {
    const L = [];
    L.push('apiVersion: v1');
    L.push('kind: ConfigMap');
    L.push('metadata:');
    L.push('  name: ' + f.app + '-config');
    L.push('  namespace: ' + f.ns);
    L.push('data:');
    f.cfg.forEach((x) => L.push('  ' + x.k + ': ' + q(x.val)));
    return L.join('\n');
  }

  function genSecret(f) {
    const L = [];
    L.push('apiVersion: v1');
    L.push('kind: Secret');
    L.push('metadata:');
    L.push('  name: ' + f.app + '-secret');
    L.push('  namespace: ' + f.ns);
    L.push('type: Opaque');
    L.push('stringData:');
    f.sec.forEach((x) => L.push('  ' + x.k + ': ' + q(x.val)));
    return L.join('\n');
  }

  function genPdb(f) {
    const L = [];
    L.push('apiVersion: policy/v1');
    L.push('kind: PodDisruptionBudget');
    L.push('metadata:');
    L.push('  name: ' + f.app + '-pdb');
    L.push('  namespace: ' + f.ns);
    L.push('spec:');
    L.push('  minAvailable: ' + (f.pdbMin || 2));
    L.push('  selector:');
    L.push('    matchLabels:');
    L.push('      app: ' + f.app);
    return L.join('\n');
  }

  function buildFiles(f) {
    const files = [];
    files.push({ name: 'deployment.yaml', title: 'Deployment', content: genDeployment(f) });
    if (f.svc) files.push({ name: 'service.yaml', title: 'Service', content: genService(f) });
    if (f.cm && f.cfg.length) files.push({ name: 'configmap.yaml', title: 'ConfigMap', content: genConfigMap(f) });
    if (f.secret && f.sec.length) files.push({ name: 'secret.yaml', title: 'Secret', content: genSecret(f) });
    if (f.ingress) files.push({ name: 'ingress.yaml', title: 'Ingress', content: genIngress(f) });
    if (f.hpa) files.push({ name: 'hpa.yaml', title: 'HPA', content: genHpa(f) });
    if (f.pdb) files.push({ name: 'pdb.yaml', title: 'PDB', content: genPdb(f) });
    files.push({ name: 'all-in-one.yaml', title: '全部合并', content: files.map((x) => x.content).join('\n---\n') });
    return files;
  }

  /* ---------- 校验 ---------- */
  function parseCpu(s) {
    if (!s) return 0;
    if (/m$/.test(s)) return parseFloat(s) || 0;
    return (parseFloat(s) || 0) * 1000;
  }
  function parseMem(s) {
    if (!s) return 0;
    const m = String(s).match(/^(\d+(?:\.\d+)?)\s*(Ki|Mi|Gi|Ti|K|M|G|T)?$/i);
    if (!m) return 0;
    const n = parseFloat(m[1]);
    const unit = (m[2] || '').toLowerCase();
    const map = { '': 1, k: 1 / 1024, ki: 1, m: 1, mi: 1, g: 1024, gi: 1024, t: 1024 * 1024, ti: 1024 * 1024 };
    return n * (map[unit] === undefined ? 1 : map[unit]);
  }
  function validChecks(f) {
    const dns = /^[a-z]([-a-z0-9]*[a-z0-9])?$/;
    const checks = [];
    checks.push({ ok: dns.test(f.app), label: '应用名称符合 DNS-1123 规范', desc: '仅小写字母、数字与中划线，如 api-gateway' });
    checks.push({ ok: !!f.image, label: '镜像地址已填写', desc: '形如 registry.xxx.com/team/app' });
    checks.push({ ok: !!f.tag, label: '镜像 Tag 已填写', desc: '建议使用明确版本号，避免 latest' });
    const rep = Number(f.replicas);
    checks.push({ ok: rep >= 1 && rep <= 200, label: '副本数在 1 ~ 200 之间', desc: '生产建议 ≥ 2，避免单点' });
    const port = Number(f.port);
    checks.push({ ok: port >= 1 && port <= 65535, label: '容器端口合法（1-65535）', desc: 'Spring Boot 默认 8080' });
    checks.push({ ok: parseCpu(f.cpuLimit) >= parseCpu(f.cpuReq), label: 'CPU limit ≥ request', desc: 'request 为调度保障，limit 为上限' });
    checks.push({ ok: parseMem(f.memLimit) >= parseMem(f.memReq), label: '内存 limit ≥ request', desc: 'JVM 建议 -Xmx ≈ limit 的 70%' });
    if (f.hpa) {
      const mn = Number(f.hpaMin), mx = Number(f.hpaMax);
      checks.push({ ok: mn >= 1 && mx >= mn && mx >= rep, label: 'HPA 副本区间覆盖当前副本数', desc: 'min ≤ replicas ≤ max' });
    }
    if (f.ingress) {
      checks.push({ ok: !!f.host && /^[a-z0-9.-]+$/.test(f.host), label: 'Ingress 域名合法', desc: '需在 DNS 中解析到 Ingress Controller' });
    }
    if (f.secret) {
      checks.push({ ok: f.sec.every((x) => x.val), label: 'Secret 变量均已填写值', desc: '空值会在启动时注入失败' });
    }
    checks.push({ ok: Number(f.grace) >= Number(f.preStop), label: '优雅下线时间 ≥ preStop 等待时间', desc: '保证存量请求处理完成后再销毁 Pod' });
    return checks;
  }

  /* ---------- YAML 高亮 ---------- */
  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function colorValues(s) {
    return s.replace(/(&quot;[^&]*?&quot;|'[^']*')|(\b\d[\w%.\/-]*\b)|(\btrue\b|\bfalse\b|\bnull\b)/g,
      function (m, str, num, bool) {
        if (str) return '<span class="c-str">' + str + '</span>';
        if (num) return '<span class="c-num">' + num + '</span>';
        return '<span class="c-num">' + bool + '</span>';
      });
  }
  function highlightLine(line) {
    if (/^\s*#/.test(line)) return '<span class="c-cmt">' + esc(line) + '</span>';
    const e = esc(line);
    const m = e.match(/^(\s*(?:-\s+)?)([A-Za-z0-9_.\-\/]+)(:)(.*)$/);
    if (!m) return colorValues(e);
    return m[1] + '<span class="c-key">' + m[2] + '</span>' + m[3] + colorValues(m[4]);
  }
  function renderYaml(text) {
    const lines = text.split('\n');
    return '<table><tbody>' + lines.map((l, i) =>
      '<tr><td class="yl-num">' + (i + 1) + '</td><td class="yl-code">' + (highlightLine(l) || '&nbsp;') + '</td></tr>'
    ).join('') + '</tbody></table>';
  }

  /* ---------- 全局状态 ---------- */
  let FILES = [];
  let ACTIVE = 'all-in-one.yaml';

  function renderAll() {
    /* PDB 两处开关保持一致（左侧表单区 + 生成对象区） */
    if ($('rs-pdb') && $('rs-pdb2')) $('rs-pdb2').checked = $('rs-pdb').checked;

    const f = readForm();
    FILES = buildFiles(f);
    if (!FILES.some((x) => x.name === ACTIVE)) ACTIVE = FILES[FILES.length - 1].name;

    /* tab */
    const tabsBox = $('yamlTabs');
    if (tabsBox) {
      tabsBox.innerHTML = FILES.map((x) =>
        '<span class="yaml-tab ' + (x.name === ACTIVE ? 'active' : '') + '" data-file="' + x.name + '">' + x.title + '</span>'
      ).join('');
    }
    const body = $('yamlBody');
    const cur = FILES.filter((x) => x.name === ACTIVE)[0];
    if (body && cur) body.innerHTML = renderYaml(cur.content);

    /* 生成对象摘要 */
    const sum = $('resSummary');
    if (sum) {
      sum.innerHTML = FILES.filter((x) => x.name !== 'all-in-one.yaml')
        .map((x) => '<span class="tag tag-info">' + x.title + '</span>').join(' ');
    }

    /* 校验清单 */
    const checks = validChecks(f);
    const box = $('checkList');
    if (box) {
      box.innerHTML = checks.map((k) =>
        '<div class="check-item">' +
        '<span class="check-mark ' + (k.ok ? 'ok' : 'bad') + '">' + (k.ok ? '✓' : '!') + '</span>' +
        '<div><div>' + k.label + '</div><div class="check-desc">' + k.desc + '</div></div>' +
        '</div>'
      ).join('');
    }
    const bad = checks.filter((x) => !x.ok).length;
    const badge = $('checkBadge');
    if (badge) {
      badge.className = 'tag ' + (bad ? 'tag-danger' : 'tag-success');
      badge.textContent = bad ? (bad + ' 项待修正') : '全部通过';
    }
    const applyBtn = $('btnApply');
    if (applyBtn) applyBtn.disabled = bad > 0;
  }

  /* ---------- 复制 / 下载 ---------- */
  function copyText(t) {
    try {
      const ta = document.createElement('textarea');
      ta.value = t;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      return true;
    } catch (e) { return false; }
  }
  function download(name, text) {
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }
  function toast(msg) {
    const el = $('toast');
    if (!el) return;
    el.textContent = msg;
    el.style.display = 'block';
    clearTimeout(el._t);
    el._t = setTimeout(() => { el.style.display = 'none'; }, 2200);
  }

  /* ---------- 一键应用（模拟） ---------- */
  function runApply() {
    const mask = $('applyModal');
    const out = $('applyTerm');
    if (!mask || !out) return;
    mask.classList.add('show');
    const f = readForm();
    const list = FILES.filter((x) => x.name !== 'all-in-one.yaml');
    const lines = [];
    lines.push({ cls: 't-info', text: '# 目标集群：prod-prod  |  命名空间：' + f.ns });
    lines.push({ cls: 't-cmd', text: 'kubectl apply -f ' + f.app + '/ --namespace ' + f.ns });
    list.forEach((x) => {
      const kindMap = { 'deployment.yaml': 'deployment.apps', 'service.yaml': 'service', 'configmap.yaml': 'configmap', 'secret.yaml': 'secret', 'ingress.yaml': 'ingress.networking.k8s.io', 'hpa.yaml': 'horizontalpodautoscaler.autoscaling', 'pdb.yaml': 'poddisruptionbudget.policy' };
      const kind = kindMap[x.name] || 'resource';
      lines.push({ cls: 't-ok', text: kind + '/' + (x.name === 'deployment.yaml' ? f.app : resourceName(f, x.name)) + ' configured' });
    });
    lines.push({ cls: 't-cmd', text: 'kubectl rollout status deployment/' + f.app + ' -n ' + f.ns });
    lines.push({ cls: 't-info', text: 'Waiting for rollout to finish: ' + Math.min(Number(f.replicas), 3) + ' of ' + f.replicas + ' updated replicas are available...' });
    lines.push({ cls: 't-ok', text: 'deployment "' + f.app + '" successfully rolled out' });
    lines.push({ cls: 't-cmd', text: 'kubectl get pods -n ' + f.ns + ' -l app=' + f.app });
    for (let i = 1; i <= Math.min(Number(f.replicas), 3); i++) {
      lines.push({ cls: 't-info', text: f.app + '-' + Math.random().toString(36).slice(2, 7) + '-' + Math.random().toString(36).slice(2, 7) + '   1/1   Running   0   3s' });
    }
    lines.push({ cls: 't-ok', text: '✔ 全部资源已应用到集群，网关路由将在 5s 内自动发现新实例' });
    lines.push({ cls: 't-warn', text: '提示：这是静态演示环境，命令仅在页面中模拟执行，未真实访问集群。' });

    let i = 0;
    out.innerHTML = '';
    const timer = setInterval(function () {
      if (i >= lines.length) { clearInterval(timer); return; }
      const d = document.createElement('div');
      d.className = lines[i].cls;
      d.textContent = lines[i].text;
      out.appendChild(d);
      out.scrollTop = out.scrollHeight;
      i++;
    }, 260);
  }
  function resourceName(f, name) {
    const map = {
      'deployment.yaml': f.app, 'service.yaml': f.app, 'configmap.yaml': f.app + '-config',
      'secret.yaml': f.app + '-secret', 'ingress.yaml': f.app + '-ingress',
      'hpa.yaml': f.app + '-hpa', 'pdb.yaml': f.app + '-pdb'
    };
    return map[name] || f.app;
  }

  /* ---------- 模板 ---------- */
  const TEMPLATES = {
    gateway: {
      ns: 'prod', app: 'api-gateway', image: 'registry.cn-hangzhou.aliyuncs.com/example/api-gateway', tag: '2.4.0',
      replicas: '4', port: '8080', svcPort: '80', svcType: 'ClusterIP', pullPolicy: 'IfNotPresent', pullSecret: 'regcred',
      cpuReq: '1000m', cpuLimit: '2000m', memReq: '1Gi', memLimit: '2Gi',
      probe: true, livePath: '/actuator/health/liveness', readyPath: '/actuator/health/readiness', probeDelay: '30', probePeriod: '10',
      ingress: true, host: 'gw.example.com', path: '/', pathType: 'Prefix', ingressClass: 'nginx', tls: true,
      hpa: true, hpaMin: '4', hpaMax: '24', hpaCpu: '70', hpaMem: false, hpaMemPct: '80',
      maxSurge: '25%', maxUnavail: '0', preStop: '30', grace: '60', pdb: true, pdbMin: '2',
      svc: true, cm: true, secret: true, metrics: true, profile: 'prod',
      env: [{ k: 'JAVA_OPTS', val: '-Xms1g -Xmx1500m -XX:+UseG1GC' }, { k: 'TZ', val: 'Asia/Shanghai' }],
      cfg: [{ k: 'NACOS_ADDR', val: 'nacos-1:8848,nacos-2:8848,nacos-3:8848' }, { k: 'GATEWAY_ROUTE_DATAID', val: 'api-gateway-routes.yaml' }],
      sec: [{ k: 'REDIS_PASSWORD', val: '********' }]
    },
    infer: {
      ns: 'prod', app: 'order-service', image: 'registry.cn-hangzhou.aliyuncs.com/example/order-service', tag: '1.6.2',
      replicas: '6', port: '8080', svcPort: '8080', svcType: 'ClusterIP', pullPolicy: 'IfNotPresent', pullSecret: 'regcred',
      cpuReq: '2000m', cpuLimit: '4000m', memReq: '4Gi', memLimit: '8Gi',
      probe: true, livePath: '/actuator/health/liveness', readyPath: '/actuator/health/readiness', probeDelay: '30', probePeriod: '10',
      ingress: false, host: 'order.example.com', path: '/', pathType: 'Prefix', ingressClass: 'nginx', tls: false,
      hpa: true, hpaMin: '6', hpaMax: '20', hpaCpu: '70', hpaMem: false, hpaMemPct: '80',
      maxSurge: '25%', maxUnavail: '0', preStop: '25', grace: '60', pdb: true, pdbMin: '3',
      svc: true, cm: true, secret: true, metrics: true, profile: 'prod',
      env: [{ k: 'JAVA_OPTS', val: '-Xms2g -Xmx4g' }, { k: 'NACOS_GROUP', val: 'DEFAULT_GROUP' }],
      cfg: [{ k: 'MYSQL_ADDR', val: 'mysql-master:3306' }, { k: 'REDIS_ADDR', val: 'redis-cluster:6379' }],
      sec: [{ k: 'DB_PASSWORD', val: '********' }]
    },
    boot: {
      ns: 'default', app: 'demo-service', image: 'registry.cn-hangzhou.aliyuncs.com/example/demo-service', tag: '1.0.0',
      replicas: '2', port: '8080', svcPort: '80', svcType: 'ClusterIP', pullPolicy: 'IfNotPresent', pullSecret: '',
      cpuReq: '500m', cpuLimit: '1000m', memReq: '512Mi', memLimit: '1Gi',
      probe: true, livePath: '/actuator/health', readyPath: '/actuator/health', probeDelay: '20', probePeriod: '10',
      ingress: false, host: 'demo.example.com', path: '/', pathType: 'Prefix', ingressClass: 'nginx', tls: false,
      hpa: false, hpaMin: '2', hpaMax: '6', hpaCpu: '70', hpaMem: false, hpaMemPct: '80',
      maxSurge: '25%', maxUnavail: '0', preStop: '15', grace: '45', pdb: false, pdbMin: '1',
      svc: true, cm: true, secret: false, metrics: true, profile: 'prod',
      env: [{ k: 'JAVA_OPTS', val: '-Xms512m -Xmx512m' }],
      cfg: [{ k: 'APP_ENV', val: 'prod' }],
      sec: []
    }
  };

  function fillRows(cid, rows) {
    const box = $(cid);
    if (!box) return;
    box.innerHTML = '';
    (rows || []).forEach((r) => box.appendChild(rowEl(cid, r.k, r.val)));
  }
  function rowEl(cid, k, val) {
    const d = document.createElement('div');
    d.className = 'dyn-row';
    d.innerHTML = '<input class="form-input row-key" placeholder="KEY" value="' + String(k || '').replace(/"/g, '&quot;') + '" />' +
      '<input class="form-input row-val" placeholder="VALUE" value="' + String(val || '').replace(/"/g, '&quot;') + '" />' +
      '<button class="icon-btn" type="button" data-del>×</button>';
    return d;
  }
  function applyTemplate(key) {
    const t = TEMPLATES[key];
    if (!t) return;
    const setV = (id, val) => { if ($(id)) $(id).value = val; };
    const setC = (id, val) => { if ($(id)) $(id).checked = !!val; };
    setV('f-ns', t.ns); setV('f-app', t.app); setV('f-image', t.image); setV('f-tag', t.tag);
    setV('f-replicas', t.replicas); setV('f-port', t.port); setV('f-svcPort', t.svcPort); setV('f-svcType', t.svcType);
    setV('f-pullPolicy', t.pullPolicy); setV('f-pullSecret', t.pullSecret);
    setV('f-cpuReq', t.cpuReq); setV('f-cpuLimit', t.cpuLimit); setV('f-memReq', t.memReq); setV('f-memLimit', t.memLimit);
    setC('sw-probe', t.probe); setV('f-livePath', t.livePath); setV('f-readyPath', t.readyPath);
    setV('f-probeDelay', t.probeDelay); setV('f-probePeriod', t.probePeriod);
    setC('rs-ingress', t.ingress); setV('f-host', t.host); setV('f-path', t.path); setV('f-pathType', t.pathType);
    setV('f-ingressClass', t.ingressClass); setC('sw-tls', t.tls);
    setC('rs-hpa', t.hpa); setV('f-hpaMin', t.hpaMin); setV('f-hpaMax', t.hpaMax); setV('f-hpaCpu', t.hpaCpu);
    setC('sw-hpaMem', t.hpaMem); setV('f-hpaMemPct', t.hpaMemPct);
    setV('f-maxSurge', t.maxSurge); setV('f-maxUnavail', t.maxUnavail); setV('f-preStop', t.preStop); setV('f-grace', t.grace);
    setC('rs-pdb', t.pdb); setV('f-pdbMin', t.pdbMin);
    setC('rs-svc', t.svc); setC('rs-cm', t.cm); setC('rs-secret', t.secret); setC('sw-metrics', t.metrics); setV('f-profile', t.profile);
    fillRows('envRows', t.env); fillRows('cmRows', t.cfg); fillRows('secretRows', t.sec);
    document.querySelectorAll('.tpl-card').forEach((el) => el.classList.toggle('active', el.getAttribute('data-tpl') === key));
    renderAll();
    toast('已套用模板：' + t.app);
  }

  /* ---------- 事件绑定 ---------- */
  document.addEventListener('click', function (e) {
    const t = e.target;
    if (t.closest) {
      const del = t.closest('[data-del]');
      if (del) {
        const row = del.closest('.dyn-row');
        if (row && row.parentElement) row.parentElement.removeChild(row);
        renderAll();
        return;
      }
      const add = t.closest('[data-add]');
      if (add) {
        const cid = add.getAttribute('data-add');
        const box = $(cid);
        if (box) box.appendChild(rowEl(cid, '', ''));
        return;
      }
      const tpl = t.closest('[data-tpl]');
      if (tpl) { applyTemplate(tpl.getAttribute('data-tpl')); return; }
      const tab = t.closest('[data-file]');
      if (tab) { ACTIVE = tab.getAttribute('data-file'); renderAll(); return; }
    }
  });

  document.addEventListener('input', function () { renderAll(); });
  document.addEventListener('change', function () { renderAll(); });

  window.addEventListener('DOMContentLoaded', function () {
    if ($('btnCopy')) {
      $('btnCopy').addEventListener('click', function () {
        const cur = FILES.filter((x) => x.name === ACTIVE)[0];
        const txt = cur ? cur.content : '';
        toast(copyText(txt) ? '已复制到剪贴板' : '复制失败，请手动选择');
      });
    }
    if ($('btnDownload')) {
      $('btnDownload').addEventListener('click', function () {
        const f = readForm();
        const all = FILES.filter((x) => x.name === 'all-in-one.yaml')[0];
        download((f.app || 'app') + '-k8s.yaml', all ? all.content : '');
        toast('已导出 YAML 文件');
      });
    }
    if ($('btnApply')) $('btnApply').addEventListener('click', runApply);
    if ($('btnApplyClose')) $('btnApplyClose').addEventListener('click', function () { $('applyModal').classList.remove('show'); });
    if ($('btnReset')) $('btnReset').addEventListener('click', function () { applyTemplate('gateway'); });
    applyTemplate('gateway');
  });
})();
