import os from 'node:os'

function resolveLanHost() {
  const interfaces = os.networkInterfaces()
  const priorities = ['en0', 'en1', 'Ethernet', 'Wi-Fi']

  for (const name of [...priorities, ...Object.keys(interfaces)]) {
    const entries = interfaces[name] ?? []
    const match = entries.find(
      (entry) =>
        entry &&
        entry.family === 'IPv4' &&
        !entry.internal &&
        (/^192\.168\./.test(entry.address) || /^10\./.test(entry.address) || /^172\.(1[6-9]|2\d|3[0-1])\./.test(entry.address))
    )
    if (match) {
      return match.address
    }
  }

  return '127.0.0.1'
}

const lanHost = resolveLanHost()
const apiBaseUrl = process.env.TARO_APP_API_BASE_URL || `http://${lanHost}:8080/api/v1`
const webBaseUrl = process.env.TARO_APP_WEB_BASE_URL || `http://${lanHost}:5173`
const processPlayerBaseUrl = process.env.TARO_APP_PROCESS_PLAYER_BASE_URL || webBaseUrl

const config = {
  projectName: 'west-flow-ai-weapp',
  date: '2026-04-02',
  designWidth: 375,
  deviceRatio: {
    375: 2,
    750: 1,
    828: 1.81,
  },
  sourceRoot: 'src',
  outputRoot: 'dist',
  framework: 'react',
  compiler: 'webpack5',
  plugins: ['@tarojs/plugin-platform-weapp'],
  defineConstants: {
    __API_BASE_URL__: JSON.stringify(apiBaseUrl),
    __WEB_BASE_URL__: JSON.stringify(webBaseUrl),
    __PROCESS_PLAYER_BASE_URL__: JSON.stringify(processPlayerBaseUrl),
  },
  mini: {
    postcss: {
      pxtransform: {
        enable: true,
        config: {},
      },
      url: {
        enable: true,
        config: {
          limit: 1024,
        },
      },
      cssModules: {
        enable: false,
        config: {
          namingPattern: 'module',
          generateScopedName: '[name]__[local]___[hash:base64:5]',
        },
      },
    },
  },
}

export default config
