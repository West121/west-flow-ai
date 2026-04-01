import { Pressable, StyleSheet, Text, View } from 'react-native'
import { ScreenShell } from '@/components/ScreenShell'
import { SectionCard } from '@/components/SectionCard'
import { useAuthStore } from '@/stores/auth-store'

export function MeScreen() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const signOut = useAuthStore((state) => state.signOut)

  return (
    <ScreenShell title="我的" description="当前登录态、岗位信息与移动端配置。">
      <SectionCard title={currentUser?.displayName ?? '未登录'}>
        <Row label="账号" value={currentUser?.username ?? '--'} />
        <Row label="部门" value={currentUser?.activeDepartmentName ?? '--'} />
        <Row label="岗位" value={currentUser?.activePostName ?? '--'} />
        <Row label="权限数" value={`${currentUser?.permissions.length ?? 0}`} />
      </SectionCard>

      <Pressable style={styles.signOutButton} onPress={() => void signOut()}>
        <Text style={styles.signOutLabel}>退出登录</Text>
      </Pressable>
    </ScreenShell>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 16,
  },
  rowLabel: {
    color: '#7B6F63',
  },
  rowValue: {
    color: '#171312',
    fontWeight: '600',
    flexShrink: 1,
    textAlign: 'right',
  },
  signOutButton: {
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 16,
    backgroundColor: '#171312',
    paddingVertical: 16,
  },
  signOutLabel: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
})
