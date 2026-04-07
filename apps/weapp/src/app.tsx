import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './lib/query-client'
import { AppProviders } from './providers/AppProviders'

type AppProps = {
  children?: React.ReactNode
}

function App(props: AppProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <AppProviders>{props.children}</AppProviders>
    </QueryClientProvider>
  )
}

export default App
