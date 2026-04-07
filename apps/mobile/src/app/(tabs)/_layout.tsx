import { Tabs } from 'expo-router'

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#171B29',
        tabBarInactiveTintColor: '#807A90',
        tabBarShowLabel: true,
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '700',
          marginBottom: 4,
        },
        tabBarItemStyle: {
          borderRadius: 20,
          marginHorizontal: 4,
          marginVertical: 5,
        },
        tabBarActiveBackgroundColor: 'rgba(255,255,255,0.88)',
        tabBarStyle: {
          position: 'absolute',
          left: 18,
          right: 18,
          bottom: 20,
          height: 70,
          borderTopColor: 'rgba(255,255,255,0.5)',
          borderWidth: 1,
          borderColor: 'rgba(255,255,255,0.52)',
          borderRadius: 26,
          backgroundColor: 'rgba(255,255,255,0.62)',
          shadowColor: '#7680B0',
          shadowOpacity: 0.14,
          shadowRadius: 18,
          shadowOffset: { width: 0, height: 8 },
          paddingTop: 8,
          paddingBottom: 8,
        },
        tabBarIconStyle: {
          display: 'none',
        },
        sceneStyle: {
          backgroundColor: 'transparent',
        },
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
