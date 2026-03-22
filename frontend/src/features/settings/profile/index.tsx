import { ContentSection } from '../components/content-section'
import { ProfileForm } from './profile-form'

// 个人资料设置页，只承载资料编辑表单。
export function SettingsProfile() {
  return (
    <ContentSection
      title='Profile'
      desc='This is how others will see you on the site.'
    >
      <ProfileForm />
    </ContentSection>
  )
}
