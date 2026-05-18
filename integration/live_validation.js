import mineflayer from 'mineflayer'

const host = process.env.MCGGRTP_PROXY_HOST ?? '127.0.0.1'
const port = Number(process.env.MCGGRTP_PROXY_PORT ?? '25575')
const username = process.env.MCGGRTP_BOT_NAME ?? 'TestBot'

const bot = mineflayer.createBot({
  host,
  port,
  username,
  auth: 'offline',
  version: '1.21.5'
})

const results = []
const chatLog = []
let finalTransferStarted = false
let finalResultPrinted = false
const SUCCESS_MESSAGE = /\[S1\].*Teleported/i
const OVERWORLD_SURVIVAL_1_SLOT = 12
const OVERWORLD_SURVIVAL_2_SLOT = 13
const OVERWORLD_SURVIVAL_3_SLOT = 14
const SINGLE_SERVER_SLOT = 13

bot.on('message', (message) => {
  chatLog.push(message.toString())
})

bot.on('end', () => {
  if (finalTransferStarted && !finalResultPrinted) {
    printResult({ ok: true, results, chatLog, note: 'Bot disconnected after final cross-server step.' })
    process.exit(0)
  }
})

bot.on('kicked', (reason) => {
  if (finalTransferStarted && !finalResultPrinted) {
    printResult({ ok: true, results, chatLog, note: `Bot was kicked after final cross-server step: ${reason}` })
    process.exit(0)
  }
})

bot.once('spawn', async () => {
  try {
    await sleep(2000)
    await runLocalTeleport('same-server-overworld', 11, OVERWORLD_SURVIVAL_1_SLOT, /overworld/i, { minDistance: 16 })
    await runCooldownCheck()
    await sleep(3500)
    await runLocalTeleport('same-server-nether', 13, SINGLE_SERVER_SLOT, /nether/i)
    await sleep(3500)
    await runLocalTeleport('same-server-end', 15, SINGLE_SERVER_SLOT, /end/i)
    await sleep(3500)
    await runFeedbackScenario('offline-target', 11, OVERWORLD_SURVIVAL_3_SLOT, /server is offline/i, 'offline-message')
    await sleep(3500)
    await runCrossServerOverworld()
    await sleep(6000)

    finishSuccessfully()
  } catch (error) {
    printResult({ ok: false, error: error.stack ?? String(error), results, chatLog })
    bot.quit()
    process.exitCode = 1
  }
})

bot.on('error', (error) => {
  printResult({ ok: false, error: error.stack ?? String(error), results, chatLog })
  process.exit(1)
})

async function runSameServerOverworld() {
  return runLocalTeleport('same-server-overworld', 11, 0, /overworld/i, { minDistance: 16 })
}

async function runCrossServerOverworld() {
  finalTransferStarted = true
  await selectRtp(11, OVERWORLD_SURVIVAL_2_SLOT)
  await waitForMessage(/Sending you to .*survival-2/i)
  recordResult('cross-server-overworld', { status: 'requested' })
}

async function runCooldownCheck() {
  await runFeedbackScenario('cooldown', 11, OVERWORLD_SURVIVAL_1_SLOT, /must wait/i, 'blocked')
}

async function runLocalTeleport(name, dimensionSlot, serverSlot, expectedDimension, options = {}) {
  const start = bot.entity.position.clone()
  await selectRtp(dimensionSlot, serverSlot)
  await waitForMessage(SUCCESS_MESSAGE)
  await waitForDimension(expectedDimension)
  if (options.minDistance) {
    await waitForPositionChange(start, options.minDistance)
  }
  recordResult(name, {
    dimension: currentDimension(),
    position: simplifyPosition()
  })
}

async function runFeedbackScenario(name, dimensionSlot, serverSlot, messagePattern, status) {
  await selectRtp(dimensionSlot, serverSlot)
  await waitForMessage(messagePattern)
  recordResult(name, { status })
}

async function selectRtp(dimensionSlot, serverSlot) {
  const firstWindow = waitForWindow()
  bot.chat('/rtp')
  await firstWindow
  await sleep(250)
  await bot.clickWindow(dimensionSlot, 0, 0)
  const secondWindow = await waitForWindow()
  await sleep(250)
  await bot.clickWindow(serverSlot, 0, 0)
  await sleep(250)
  return secondWindow
}

function waitForWindow(timeoutMs = 10000) {
  return onceWithTimeout('windowOpen', timeoutMs)
}

function waitForMessage(pattern, timeoutMs = 15000) {
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

function waitForDimension(pattern, timeoutMs = 15000) {
  return waitForCondition(() => pattern.test(currentDimension()), timeoutMs, `dimension ${pattern}`)
}

function waitForPositionChange(start, distance, timeoutMs = 15000) {
  return waitForCondition(() => bot.entity.position.distanceTo(start) >= distance, timeoutMs, `position change >= ${distance}`)
}

function waitForCondition(check, timeoutMs, label) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs
    const interval = setInterval(() => {
      if (check()) {
        clearInterval(interval)
        resolve()
        return
      }
      if (Date.now() > deadline) {
        clearInterval(interval)
        reject(new Error(`Timed out waiting for ${label}`))
      }
    }, 100)
  })
}

function onceWithTimeout(event, timeoutMs) {
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

function currentDimension() {
  return String(bot.game.dimension ?? '')
}

function simplifyPosition() {
  const { x, y, z } = bot.entity.position
  return { x: Math.round(x), y: Math.round(y), z: Math.round(z) }
}

function recordResult(name, details) {
  results.push({ name, ...details })
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function printResult(payload) {
  finalResultPrinted = true
  console.log(JSON.stringify(payload))
}

function finishSuccessfully() {
  printResult({ ok: true, results, chatLog })
  bot.quit()
}
