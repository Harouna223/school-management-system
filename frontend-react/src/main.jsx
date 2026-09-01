import React from 'react'
import ReactDOM from 'react-dom/client'
import { Provider } from 'react-redux'
import { ThemeProvider, CssBaseline } from '@mui/material'
import '@fontsource-variable/inter'
import App from './App.jsx'
import { store } from './redux/store'
import { ThemeContextProvider, useThemeContext } from './context/ThemeContext'
import { lightTheme, darkTheme } from './styles/theme'
import './styles/index.css'

function Root() {
  const { mode } = useThemeContext()
  return (
    <ThemeProvider theme={mode === 'dark' ? darkTheme : lightTheme}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  )
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Provider store={store}>
      <ThemeContextProvider>
        <Root />
      </ThemeContextProvider>
    </Provider>
  </React.StrictMode>,
)