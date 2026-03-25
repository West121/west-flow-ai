import { useEffect } from 'react'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAuthStore } from '@/stores/auth-store'
import { showSubmittedData } from '@/lib/show-submitted-data'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'

const profileFormSchema = z.object({
  displayName: z
    .string('请输入姓名。')
    .min(2, '姓名至少需要 2 个字符。')
    .max(30, '姓名不能超过 30 个字符。'),
  username: z
    .string('请输入账号。')
    .min(2, '账号至少需要 2 个字符。')
    .max(30, '账号不能超过 30 个字符。'),
  mobile: z.string().regex(/^1\d{10}$/, '请输入 11 位手机号。'),
  email: z.email('请输入正确的邮箱地址。'),
})

type ProfileFormValues = z.infer<typeof profileFormSchema>

export function ProfileForm() {
  const currentUser = useAuthStore((state) => state.currentUser)

  const form = useForm<ProfileFormValues>({
    resolver: zodResolver(profileFormSchema),
    defaultValues: {
      displayName: currentUser?.displayName ?? '',
      username: currentUser?.username ?? '',
      mobile: currentUser?.mobile ?? '',
      email: currentUser?.email ?? '',
    },
    mode: 'onChange',
  })

  useEffect(() => {
    if (!currentUser) {
      return
    }

    form.reset({
      displayName: currentUser.displayName ?? '',
      username: currentUser.username ?? '',
      mobile: currentUser.mobile ?? '',
      email: currentUser.email ?? '',
    })
  }, [currentUser, form])

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit((data) => showSubmittedData(data))}
        className='space-y-8'
      >
        <div className='rounded-lg border bg-muted/20 p-4 text-sm text-muted-foreground'>
          当前页面展示的是登录用户的真实基础资料。
        </div>
        <FormField
          control={form.control}
          name='displayName'
          render={({ field }) => (
            <FormItem>
              <FormLabel>姓名</FormLabel>
              <FormControl>
                <Input placeholder='请输入姓名' {...field} />
              </FormControl>
              <FormDescription>对外展示的中文姓名。</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name='username'
          render={({ field }) => (
            <FormItem>
              <FormLabel>账号</FormLabel>
              <FormControl>
                <Input placeholder='请输入账号' {...field} />
              </FormControl>
              <FormDescription>登录系统时使用的账号标识。</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name='mobile'
          render={({ field }) => (
            <FormItem>
              <FormLabel>手机号</FormLabel>
              <FormControl>
                <Input placeholder='请输入手机号' {...field} />
              </FormControl>
              <FormDescription>用于消息通知与身份校验。</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name='email'
          render={({ field }) => (
            <FormItem>
              <FormLabel>邮箱</FormLabel>
              <FormControl>
                <Input placeholder='请输入邮箱' {...field} />
              </FormControl>
              <FormDescription>用于接收系统通知与登录提醒。</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type='submit'>保存资料</Button>
      </form>
    </Form>
  )
}
