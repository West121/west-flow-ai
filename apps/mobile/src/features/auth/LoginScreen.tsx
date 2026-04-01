import { useState } from 'react'
import { Redirect } from 'expo-router'
import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { getApiErrorMessage } from '@/lib/api/client'
import { useAuthStore } from '@/stores/auth-store'

export function LoginScreen() {
  const hydrated = useAuthStore((state) => state.hydrated)
  const accessToken = useAuthStore((state) => state.accessToken)
  const signIn = useAuthStore((state) => state.signIn)
  const isSigningIn = useAuthStore((state) => state.isSigningIn)
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('123456')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (hydrated && accessToken) {
    return <Redirect href="/workbench" />
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        <Text style={styles.kicker}>West Flow Mobile</Text>
        <Text style={styles.title}>登录移动端</Text>
        <Text style={styles.description}>
          先完成审批主链、AI Copilot 和流程图回顾播放器，再逐步扩展后台能力。
        </Text>

        <View style={styles.form}>
          <View style={styles.field}>
            <Text style={styles.label}>账号</Text>
            <TextInput
              value={username}
              onChangeText={setUsername}
              placeholder="请输入账号"
              style={styles.input}
              autoCapitalize="none"
            />
          </View>
          <View style={styles.field}>
            <Text style={styles.label}>密码</Text>
            <TextInput
              value={password}
              onChangeText={setPassword}
              placeholder="请输入密码"
              style={styles.input}
              secureTextEntry
            />
          </View>
          {errorMessage ? (
            <Text style={styles.errorMessage}>{errorMessage}</Text>
          ) : null}
          <Pressable
            disabled={isSigningIn}
            onPress={async () => {
              setErrorMessage(null)
              try {
                await signIn(username.trim(), password)
              } catch (error) {
                setErrorMessage(getApiErrorMessage(error, '登录失败，请稍后重试。'))
              }
            }}
            style={[styles.submitButton, isSigningIn && styles.submitButtonDisabled]}
          >
            <Text style={styles.submitLabel}>{isSigningIn ? '登录中…' : '登录'}</Text>
          </Pressable>
        </View>
      </View>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F6F4EF',
  },
  container: {
    flex: 1,
    paddingHorizontal: 24,
    paddingTop: 32,
  },
  kicker: {
    color: '#7B6F63',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  title: {
    color: '#171312',
    fontSize: 34,
    fontWeight: '800',
    marginTop: 10,
  },
  description: {
    color: '#5C524A',
    lineHeight: 22,
    marginTop: 12,
  },
  form: {
    gap: 18,
    marginTop: 32,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: '#E6DED2',
    backgroundColor: '#FFFCF7',
    padding: 20,
  },
  field: {
    gap: 8,
  },
  label: {
    color: '#433A33',
    fontSize: 14,
    fontWeight: '600',
  },
  input: {
    borderWidth: 1,
    borderColor: '#D8CEC0',
    borderRadius: 16,
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 14,
    paddingVertical: 14,
    fontSize: 16,
  },
  errorMessage: {
    color: '#B53B46',
    lineHeight: 20,
  },
  submitButton: {
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: '#171312',
    paddingVertical: 16,
  },
  submitButtonDisabled: {
    opacity: 0.7,
  },
  submitLabel: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
})
