import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // host: true permite abrir la app desde el celular en la misma red wifi
    host: true,
    // En desarrollo, /api se reenvía al backend: el navegador solo habla con Vite
    proxy: {
      '/api': 'http://localhost:8080',
      // Swagger también pasa por aquí, para usar la misma sesión que la app
      '/swagger-ui': 'http://localhost:8080',
      '/v3/api-docs': 'http://localhost:8080',
    },
  },
  build: {
    // pdf.js (el visor de PDF) pesa ~500 kB, pero se descarga aparte y solo al abrir un PDF
    chunkSizeWarningLimit: 600,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
