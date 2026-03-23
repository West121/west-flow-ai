import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'

const featureChunkRules: Array<[pattern: string, chunk: string]> = [
  ['/src/features/ai-admin/registry-pages.tsx', 'feature-ai-admin-registry'],
  ['/src/features/ai-admin/record-pages.tsx', 'feature-ai-admin-records'],
  ['/src/features/ai-admin/diagnostics-page.tsx', 'feature-ai-admin-diagnostics'],
  ['/src/features/ai-admin/shared.tsx', 'feature-ai-admin-shared'],
  ['/src/features/ai-admin/shared-formatters.ts', 'feature-ai-admin-shared'],
  ['/src/features/plm/pages.tsx', 'feature-plm-pages'],
  ['/src/features/workflow/management-pages.tsx', 'feature-workflow-management'],
  ['/src/features/system/org-pages.tsx', 'feature-system-org'],
  ['/src/features/system/user-pages.tsx', 'feature-system-user'],
  ['/src/features/system/role-pages.tsx', 'feature-system-role'],
  ['/src/features/system/menu-pages.tsx', 'feature-system-menu'],
  ['/src/features/system/dict-pages.tsx', 'feature-system-dict'],
  ['/src/features/system/message-pages.tsx', 'feature-system-message'],
  ['/src/features/system/notification-pages.tsx', 'feature-system-notification'],
  ['/src/features/system/notification-channel-pages.tsx', 'feature-system-notification'],
  ['/src/features/system/trigger-pages.tsx', 'feature-system-trigger'],
  ['/src/features/system/agent-pages.tsx', 'feature-system-agent'],
  ['/src/features/system/monitor-pages.tsx', 'feature-system-monitor'],
  ['/src/features/system/log-pages.tsx', 'feature-system-monitor'],
  ['/src/features/system/file-pages.tsx', 'feature-system-monitor'],
]

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: 'react',
      autoCodeSplitting: true,
      routeFileIgnorePattern:
        '^(clerk|sign-in-2\\.tsx|sign-up\\.tsx|forgot-password\\.tsx|otp\\.tsx)$',
    }),
    react(),
    tailwindcss(),
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          for (const [pattern, chunk] of featureChunkRules) {
            if (id.includes(pattern)) {
              return chunk
            }
          }

          if (!id.includes('node_modules')) {
            return
          }

          if (id.includes('@xyflow') || id.includes('dagre')) {
            return 'vendor-flow'
          }

          if (id.includes('recharts')) {
            return 'vendor-charts'
          }

          if (
            id.includes('react-hook-form') ||
            id.includes('@hookform/resolvers') ||
            id.includes('zod')
          ) {
            return 'vendor-forms'
          }

          if (id.includes('@radix-ui') || id.includes('cmdk')) {
            return 'vendor-ui'
          }

          if (
            id.includes('@tanstack') ||
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('scheduler')
          ) {
            return 'vendor-react'
          }
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
