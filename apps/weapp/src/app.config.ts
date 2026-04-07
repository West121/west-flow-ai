export default defineAppConfig({
  pages: [
    'pages/sign-in/index',
    'pages/workbench/index',
    'pages/ai/index',
    'pages/me/index',
    'pages/approval/detail',
    'pages/process-player/index',
  ],
  window: {
    navigationStyle: 'custom',
    backgroundColor: '#F4F6F8',
    backgroundTextStyle: 'light',
  },
  tabBar: {
    custom: true,
    color: '#788190',
    selectedColor: '#111827',
    backgroundColor: '#FFFFFF',
    borderStyle: 'white',
    list: [
      {
        pagePath: 'pages/workbench/index',
        text: '工作台',
      },
      {
        pagePath: 'pages/ai/index',
        text: 'AI',
      },
      {
        pagePath: 'pages/me/index',
        text: '我的',
      },
    ],
  },
})
