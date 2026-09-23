import React from 'react'
import ReactDOM from 'react-dom/client'
import '@fontsource-variable/inter'
import App from './App.jsx'
import AppProviders from './AppProviders.jsx'
import './styles/index.css'

// La pile des fournisseurs (Redux, thème) est partagée avec les tests de
// câblage : voir AppProviders.jsx.
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AppProviders>
      <App />
    </AppProviders>
  </React.StrictMode>,
)
