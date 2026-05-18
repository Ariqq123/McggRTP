import mineflayer from 'mineflayer'

const host = process.env.MCGGRTP_PROXY_HOST ?? '127.0.0.1'
const port = Number(process.env.MCGGRTP_PROXY_PORT ?? '25575')
const botCount = Number(process.env.MCGGRTP_STRESS_BOT_COUNT ?? '50')
const mode = process.env.MCGGRTP_STRESS_MODE ?? 'local'
const connectStaggerMs = Number(process.env.MCGGRTP_STRESS_CONNECT_STAGGER_MS ?? '50')
const defaultTransferStaggerMs = mode === 'cross' ? 3000 : 0
const transferStaggerMs = Number(process.env.MCGGRTP_STRESS_STAGGER_MS ?? String(defaultTransferStaggerMs))
const localMessageTimeoutMs = Number(process.env.MCGGRTP_STRESS_LOCAL_TIMEOUT_MS ?? '90000')
const crossMessageTimeoutMs = Number(process.env.MCGGRTP_STRESS_CROSS_TIMEOUT_MS ?? '120000')
const overallTimeoutMs = Number(process.env.MCGGRTP_STRESS_OVERALL_TIMEOUT_MS ?? '150000')
const dimensionSlot = 11
const localServerSlot = 12
const crossServerSlot = 13
const localSuccess = /\[S1\].*Teleported/i
const crossSuccess = /\[S2\].*Teleported/i
const crossTransfer = /Sending you to .*survival-2/i

const startedAt = Date.now()

try {
  const results = await Promise.all([...Array(botCount).keys()].map((index) => runBot(index)))
  const successes = results.filter((result) => result.ok)
  const failures = results.filter((result) => !result.ok)
  const latencies = successes.map((result) => result.durationMs).sort((a, b) => a - b)
  printResult({
    ok: failures.length === 0,
    mode,
    botCount,
    successCount: successes.length,
    failureCount: failures.length,
    averageDurationMs: latencies.length ? Math.round(latencies.reduce((sum, value) => sum + value, 0) / latencies.length) : null,
    p95DurationMs: percentile(latencies, 0.95),
    totalDurationMs: Date.now() - startedAt,
    results
  })
} catch (error) {
  printResult({ ok: false, mode, botCount, error: error.stack ?? String(error) })
  process.exitCode = 1
}

async function runBot(index) {
  const username = `Stress${String(index + 1).padStart(2, '0')}`
  const bot = mineflayer.createBot({
    host,
    port,
    username,
    auth: 'offline',
    version: '1.21.5',
    viewDistance: 'tiny'
  })
  const flowStartedAt = Date.now()
  const chatLog = []
  let transferStarted = false
  let teleportConfirmed = false

  bot.on('message', (message) => {
    chatLog.push(message.toString())
  })

  return new Promise((resolve) => {
    const finalize = (payload) => {
      if (!bot._mcggrtpDone) {
        bot._mcggrtpDone = true
        try {
          bot.quit()
        } catch {
          // ignore
        }
        resolve({
          username,
          durationMs: Date.now() - flowStartedAt,
          ...payload
        })
      }
    }

    const timeout = setTimeout(() => {
      finalize({ ok: false, reason: 'timeout', chatTail: chatLog.slice(-10) })
    }, overallTimeoutMs)

    bot.once('spawn', async () => {
      try {
        await sleep(index * connectStaggerMs)
        if (mode === 'cross') {
          // Mineflayer can keep emitting play-state movement packets while
          // Velocity is moving the connection through configuration state.
          // Vanilla clients handle this cleanly, but under stress those bot
          // packets are decoded by Velocity as configuration packets.
          bot.physicsEnabled = false
        }
        await selectRtp(
          bot,
          dimensionSlot,
          mode === 'cross' ? crossServerSlot : localServerSlot,
          mode === 'cross' ? index * transferStaggerMs : 0
        )
        if (mode === 'cross') {
          transferStarted = true
          await waitForMessage(bot, chatLog, crossTransfer, crossMessageTimeoutMs)
          await waitForMessage(bot, chatLog, crossSuccess, crossMessageTimeoutMs)
          teleportConfirmed = true
        } else {
          await waitForMessage(bot, chatLog, localSuccess, localMessageTimeoutMs)
        }
        clearTimeout(timeout)
        finalize({ ok: true })
      } catch (error) {
        clearTimeout(timeout)
        finalize({ ok: false, reason: error.stack ?? String(error), chatTail: chatLog.slice(-10) })
      }
    })

    bot.on('error', (error) => {
      clearTimeout(timeout)
      finalize({ ok: false, reason: error.stack ?? String(error), chatTail: chatLog.slice(-10) })
    })

    bot.on('kicked', (reason) => {
      if (mode === 'cross' && transferStarted && !teleportConfirmed) {
        clearTimeout(timeout)
        finalize({ ok: false, reason: `kicked before cross RTP confirmation: ${reason}`, chatTail: chatLog.slice(-10) })
        return
      }
      clearTimeout(timeout)
      finalize({ ok: false, reason: `kicked: ${reason}`, chatTail: chatLog.slice(-10) })
    })

    bot.on('end', () => {
      clearTimeout(timeout)
      finalize({
        ok: false,
        reason: mode === 'cross' && transferStarted && !teleportConfirmed
          ? 'disconnected before cross RTP confirmation'
          : 'disconnected',
        chatTail: chatLog.slice(-10)
      })
    })
  })
}

async function selectRtp(bot, selectedDimensionSlot, selectedServerSlot, serverClickDelayMs = 0) {
  const firstWindow = waitForWindow(bot)
  bot.chat('/rtp')
  await firstWindow
  await sleep(150)
  await bot.clickWindow(selectedDimensionSlot, 0, 0)
  await waitForWindow(bot)
  await sleep(150)
  if (serverClickDelayMs > 0) {
    await sleep(serverClickDelayMs)
  }
  await bot.clickWindow(selectedServerSlot, 0, 0)
}

function waitForWindow(bot, timeoutMs = 15000) {
  return onceWithTimeout(bot, 'windowOpen', timeoutMs)
}

function waitForMessage(bot, chatLog, pattern, timeoutMs = 20000) {
  const existing = chatLog.find((line) => pattern.test(line))
  if (existing) {
    return Promise.resolve(existing)
  }

  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      bot.removeListener('message', onMessage)
      reject(new Error(`Timed out waiting for chat message ${pattern}`))
    }, timeoutMs)

    const onMessage = (message) => {
      const line = message.toString()
      if (pattern.test(line)) {
        clearTimeout(timeout)
        bot.removeListener('message', onMessage)
        resolve(line)
      }
    }

    bot.on('message', onMessage)
  })
}

function onceWithTimeout(bot, event, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      bot.removeListener(event, handler)
      reject(new Error(`Timed out waiting for ${event}`))
    }, timeoutMs)

    const handler = (...args) => {
      clearTimeout(timeout)
      resolve(...args)
    }

    bot.once(event, handler)
  })
}

function percentile(values, fraction) {
  if (!values.length) {
    return null
  }
  const index = Math.min(values.length - 1, Math.max(0, Math.ceil(values.length * fraction) - 1))
  return values[index]
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function printResult(payload) {
  console.log(JSON.stringify(payload))
}
