'use strict'

/**
 * 主机运行指标上报（v0.2 第 10 项）。
 *
 * 每 60 秒采集一次，POST 到 `${serverAddr}/discovery/metrics/batch`：
 * - CPU：使用率（os.cpus 累计 tick 差）、核数、1 分钟负载
 * - 内存：总量 / 使用率、进程 RSS
 * - 磁盘：总量 / 使用率（fs.statfs，Node 18+）
 * - 网络：收发速率（/proc/net/dev 两次采样差）、TCP 已建立连接数
 * - GPU：有 nvidia-smi 时附带利用率与显存，无 GPU 自动跳过
 *
 * 用法：
 *   const reporter = createMetricsReporter({ serverAddr, appId, serviceId, clientToken })
 *   reporter.start()
 *   reporter.stop()
 *
 * 非 Linux 平台缺失指标留空（平台展示为「-」），不猜测、不补点。
 */

const os = require('os')
const fs = require('fs')
const { execFile } = require('child_process')

function readProcNetDev () {
  try {
    const text = fs.readFileSync('/proc/net/dev', 'utf8')
    let rx = 0
    let tx = 0
    for (const line of text.split('\n')) {
      if (!line.includes(':')) continue
      const [name, data] = line.split(':')
      if (!name || name.trim() === 'lo' || name.includes('Inter-')) continue
      const fields = data.trim().split(/\s+/)
      if (fields.length >= 9) {
        rx += Number(fields[0]) || 0
        tx += Number(fields[8]) || 0
      }
    }
    return { rx, tx }
  } catch (e) {
    return null
  }
}

function readEstablishedTcp () {
  try {
    const lines = fs.readFileSync('/proc/net/tcp', 'utf8').split('\n').slice(1)
    return lines.filter((line) => {
      const parts = line.trim().split(/\s+/)
      return parts.length > 3 && parts[3] === '01'
    }).length
  } catch (e) {
    return null
  }
}

function readProcRss () {
  try {
    const text = fs.readFileSync('/proc/self/status', 'utf8')
    const match = text.match(/^VmRSS:\s+(\d+)\s+kB/m)
    return match ? Number(match[1]) * 1024 : null
  } catch (e) {
    return null
  }
}

function collectGpu () {
  return new Promise((resolve) => {
    execFile(
      'nvidia-smi',
      ['--query-gpu=utilization.gpu,memory.used,memory.total', '--format=csv,noheader,nounits'],
      { timeout: 2000 },
      (error, stdout) => {
        if (error || !stdout) return resolve({})
        const lines = stdout.split('\n').map((l) => l.trim()).filter(Boolean)
        if (!lines.length) return resolve({})
        let util = 0
        let used = 0
        let total = 0
        for (const line of lines) {
          const [u, m, t] = line.split(',').map((v) => Number(v.trim()))
          util += u || 0
          used += m || 0
          total += t || 0
        }
        const out = { gpuCount: lines.length, gpuUtil: Math.round((util / lines.length) * 100) / 100 }
        if (total > 0) out.gpuMemPct = Math.round((used * 100 / total) * 100) / 100
        resolve(out)
      }
    )
  })
}

function localTime (ms) {
  const d = new Date(ms)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function cpuSnapshot () {
  let idle = 0
  let total = 0
  for (const cpu of os.cpus()) {
    for (const value of Object.values(cpu.times)) total += value
    idle += cpu.times.idle
  }
  return { idle, total }
}

function createMetricsReporter (options = {}) {
  const serverAddr = (options.serverAddr || 'http://127.0.0.1:28090/api').replace(/\/$/, '')
  const appId = options.appId || 'app-demo'
  const serviceId = options.serviceId || 'demo-service'
  const clientToken = options.clientToken || ''
  const interval = Math.max(10, options.interval || 60)

  let timer = null
  let lastCpu = cpuSnapshot()
  let lastNet = null
  let lastAt = 0

  async function collect () {
    const now = Date.now()
    const data = {
      hostname: os.hostname(),
      appId,
      serviceName: serviceId,
      osName: os.type(),
      osVersion: os.release(),
      cpuCores: os.cpus().length,
      reportTime: localTime(now)
    }

    // CPU：tick 差
    const snap = cpuSnapshot()
    const totalDelta = snap.total - lastCpu.total
    const idleDelta = snap.idle - lastCpu.idle
    if (totalDelta > 0) {
      data.cpuUsage = Math.round((1 - idleDelta / totalDelta) * 10000) / 100
    }
    lastCpu = snap
    const load = os.loadavg()[0]
    if (load >= 0) data.loadAvg = Math.round(load * 100) / 100

    // 内存
    const totalMem = os.totalmem()
    const freeMem = os.freemem()
    data.memTotal = totalMem
    data.memUsedPct = Math.round(((totalMem - freeMem) * 100 / totalMem) * 100) / 100
    const rss = readProcRss()
    if (rss) data.procRss = rss
    const heap = process.memoryUsage()
    if (heap.heapTotal > 0) {
      // Node 用 heapUsed / heapTotal 近似「堆使用率」，字段沿用 jvmHeapPct
      data.jvmHeapPct = Math.round((heap.heapUsed * 100 / heap.heapTotal) * 100) / 100
    }

    // 磁盘（Node 18+ fs.statfs）
    try {
      if (typeof fs.statfsSync === 'function') {
        const stat = fs.statfsSync('/')
        const total = stat.blocks * stat.bsize
        const free = stat.bavail * stat.bsize
        data.diskTotal = total
        data.diskUsedPct = Math.round(((total - free) * 100 / total) * 100) / 100
      }
    } catch (e) {
      // 忽略
    }

    // 网络
    const net = readProcNetDev()
    if (net) {
      if (lastNet && lastAt > 0) {
        const seconds = Math.max(1, (now - lastAt) / 1000)
        data.netRxBps = Math.round((net.rx - lastNet.rx) / seconds)
        data.netTxBps = Math.round((net.tx - lastNet.tx) / seconds)
      }
      lastNet = net
      lastAt = now
    }
    const conn = readEstablishedTcp()
    if (conn !== null) data.netConn = conn

    // GPU
    const gpu = await collectGpu()
    Object.assign(data, gpu)
    if (data.gpuCount === undefined) data.gpuCount = 0
    return data
  }

  async function reportOnce () {
    try {
      const payload = JSON.stringify([await collect()])
      const response = await fetch(`${serverAddr}/discovery/metrics/batch`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Gateway-Client-Token': clientToken
        },
        body: payload
      })
      return response.ok
    } catch (e) {
      return false
    }
  }

  function start () {
    if (timer) return
    collect() // 基线
    timer = setInterval(() => { reportOnce() }, interval * 1000)
    timer.unref && timer.unref()
  }

  function stop () {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  return { collect, reportOnce, start, stop }
}

module.exports = { createMetricsReporter }

/* 直接运行：node src/metrics.js --dry-run */
if (require.main === module) {
  const dry = process.argv.includes('--dry-run')
  const reporter = createMetricsReporter({
    serverAddr: process.env.GATEWAY_SERVER_ADDR || 'http://127.0.0.1:28090/api',
    appId: process.env.GATEWAY_APP_ID || 'app-demo',
    serviceId: process.env.GATEWAY_SERVICE_ID || 'demo-service',
    clientToken: process.env.GATEWAY_CLIENT_TOKEN || ''
  })
  reporter.collect().then(async (data) => {
    if (dry) {
      console.log(JSON.stringify(data, null, 2))
    } else {
      console.log('reported:', await reporter.reportOnce())
    }
  })
}
