import { ContentSection } from '../components/content-section'
import { ProfileForm } from './profile-form'

// 个人资料设置页，只承载资料编辑表单。
export function SettingsProfile() {
  return (
    <ContentSection
      title='个人资料'
      desc='查看并更新当前登录账户的基础信息。'
    >
      <ProfileForm />
    </ContentSection>
  )
}
