import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/restapis': 'http://localhost:4566',
      '/cognito': {
        target: 'http://localhost:4566',
        rewrite: (path) => path.replace(/^\/cognito/, ''),
      },
    },
  },
})
