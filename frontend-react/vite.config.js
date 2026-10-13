import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Découpe du bundle principal (≈ 1,2 Mo avant) : les bibliothèques
    // lourdes ne sont plus téléchargées au premier affichage, mais à la demande
    // lorsque la page qui les utilise est atteinte (lazy loading déjà en place).
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          redux: ['@reduxjs/toolkit', 'react-redux'],
          mui: ['@mui/material', '@mui/icons-material', '@emotion/react', '@emotion/styled'],
          charts: ['recharts'],
          forms: ['formik', 'yup'],
          http: ['axios'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    css: true,
    // Les fichiers de test sont exécutés SÉQUENTIELLEMENT : en parallèle, les
    // 12 environnements jsdom (MUI + React Router) se disputent la mémoire et
    // l'un ou l'autre dépasse le délai du test — un fichier différent à chaque
    // exécution, ce qui donne une suite « verte ou rouge » au hasard et masque
    // les vraies régressions. Le coût est de quelques minutes, la stabilité
    // n'a pas de prix pour une CI.
    fileParallelism: false,
    testTimeout: 20000,
  },
})