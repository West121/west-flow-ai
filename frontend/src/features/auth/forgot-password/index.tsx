import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { AuthLayout } from '../auth-layout'
import { ForgotPasswordForm } from './components/forgot-password-form'

// 找回密码页入口，展示重置申请表单和说明文案。
export function ForgotPassword() {
  return (
    <AuthLayout>
      <Card className='gap-4'>
        <CardHeader>
          <CardTitle className='text-lg tracking-tight'>找回密码</CardTitle>
          <CardDescription>
            输入注册邮箱后，系统会记录重置申请。<br />
            当前阶段由管理员统一处理密码重置。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ForgotPasswordForm />
        </CardContent>
        <CardFooter>
          <p className='mx-auto px-8 text-center text-sm text-balance text-muted-foreground'>
            如需开通账号或重置密码，请联系系统管理员。
          </p>
        </CardFooter>
      </Card>
    </AuthLayout>
  )
}
