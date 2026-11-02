import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Regroupements de bundle, nom de chunk -> paquets racines.
 *
 * Déclaré hors de la configuration parce que Rolldown (moteur de build de
 * Vite 8) n'accepte `manualChunks` que sous forme de FONCTION : la forme objet
 * de Rollup, qui marchait jusqu'à Vite 5, casse le build avec
 * « Invalid type: Expected Function but received Object ». Le contenu des
 * groupes est volontairement identique à l'ancien objet, pour que la montée de
 * moteur ne change pas aussi le découpage livré.
 */
const CHUNK_GROUPS = {
  react: ['react', 'react-dom', 'react-router-dom'],
  redux: ['@reduxjs/toolkit', 'react-redux'],
  mui: ['@mui/material', '@mui/icons-material', '@emotion/react', '@emotion/styled'],
  charts: ['recharts'],
  forms: ['formik', 'yup'],
  http: ['axios'],
}

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
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          for (const [chunk, packages] of Object.entries(CHUNK_GROUPS)) {
            for (const pkg of packages) {
              // Le « / » final est indispensable : sans lui, `react` capterait
              // `react-redux` et `react-router-dom`, qui doivent rester dans
              // leurs propres chunks.
              if (id.includes(`node_modules/${pkg}/`)) return chunk
            }
          }
          return undefined
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