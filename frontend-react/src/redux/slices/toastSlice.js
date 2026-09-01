import { createSlice } from '@reduxjs/toolkit'

/**
 * File de notifications toast (Snackbars Material UI).
 */
const toastSlice = createSlice({
  name: 'toast',
  initialState: { items: [] },
  reducers: {
    showToast: (state, action) => {
      const { id, message, type = 'success' } = action.payload
      state.items.push({ id: id ?? Date.now() + Math.random(), message, type })
    },
    removeToast: (state, action) => {
      state.items = state.items.filter((t) => t.id !== action.payload)
    },
  },
})

export const { showToast, removeToast } = toastSlice.actions
export default toastSlice.reducer