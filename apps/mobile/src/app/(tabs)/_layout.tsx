import { Tabs } from 'expo-router'

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#171312',
        tabBarInactiveTintColor: '#8B8177',
        tabBarStyle: {
          borderTopColor: '#E7E1D7',
          backgroundColor: '#FFFCF7'
        }
      }}
    >
      <Tabs.Screen
        name="workbench"
        options={{
          title: '工作台'
        }}
      />
      <Tabs.Screen
        name="ai"
        options={{
          title: 'AI'
        }}
      />
      <Tabs.Screen
        name="me"
        options={{
          title: '我的'
        }}
      />
    </Tabs>
  )
}
