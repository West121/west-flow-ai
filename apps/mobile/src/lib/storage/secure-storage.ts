import { Platform } from 'react-native'
import * as SecureStore from 'expo-secure-store'

function getWebStorage() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return null
  }
  return window.localStorage
}

export async function getSecureItem(key: string) {
  if (Platform.OS === 'web') {
    return getWebStorage()?.getItem(key) ?? null
  }
  return SecureStore.getItemAsync(key)
}

export async function setSecureItem(key: string, value: string) {
  if (Platform.OS === 'web') {
    getWebStorage()?.setItem(key, value)
    return
  }
  await SecureStore.setItemAsync(key, value)
}

export async function deleteSecureItem(key: string) {
  if (Platform.OS === 'web') {
    getWebStorage()?.removeItem(key)
    return
  }
  await SecureStore.deleteItemAsync(key)
}
