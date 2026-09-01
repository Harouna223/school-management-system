import { createSlice } from '@reduxjs/toolkit'

const storedMode = localStorage.getItem('themeMode') || 'light'

const themeSlice = createSlice({
  name: 'theme',
  initialState: { mode: storedMode },
  reducers: {
    toggleTheme: (state) => {
      state.mode = state.mode === 'light' ? 'dark' : 'light'
      localStorage.setItem('themeMode', state.mode)
      if (state.mode === 'dark') {
        document.documentElement.classList.add('dark')
      } else {
        document.documentElement.classList.remove('dark')
      }
    },
  },
})

export const { toggleTheme } = themeSlice.actions
export default themeSlice.reducer