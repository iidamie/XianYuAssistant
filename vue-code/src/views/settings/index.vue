<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentUser, changePassword } from '@/api/system'
import { logout } from '@/api/auth'
import { getSetting, saveSetting } from '@/api/setting'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAuthToken } from '@/utils/request'

const router = useRouter()
const username = ref('')
const lastLoginTime = ref('')
const loading = ref(false)

// 修改密码
const showPasswordForm = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

// 退出登录
const loggingOut = ref(false)

// 系统提示词
const SYS_PROMPT_KEY = 'sys_prompt'
const DEFAULT_SYS_PROMPT = '你是一个闲鱼卖家，你叫肥极喵，不要回复的像AI，简短回答\n参考相关信息回答,不要乱回答,不知道就换不同语气回复提示用户详细点询问'
const sysPromptValue = ref('')
const sysPromptSaving = ref(false)
const sysPromptLoaded = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getCurrentUser()
    if (res.code === 200 && res.data) {
      username.value = res.data.username || ''
      lastLoginTime.value = res.data.lastLoginTime || ''
    }
  } catch (e) {
    console.error('获取用户信息失败:', e)
  } finally {
    loading.value = false
  }

  // 加载系统提示词配置
  try {
    const res = await getSetting({ settingKey: SYS_PROMPT_KEY })
    if (res.code === 200 && res.data) {
      sysPromptValue.value = res.data.settingValue || ''
      sysPromptLoaded.value = true
    }
  } catch (e) {
    console.error('获取系统提示词配置失败:', e)
  }
})

async function handleChangePassword() {
  if (!oldPassword.value) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('新密码长度需在6-50之间')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次密码不一致')
    return
  }
  changingPassword.value = true
  try {
    const res = await changePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功')
      showPasswordForm.value = false
      oldPassword.value = ''
      newPassword.value = ''
      confirmPassword.value = ''
    }
  } finally {
    changingPassword.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm(
      '确定要退出登录吗？',
      '退出确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loggingOut.value = true
    try {
      await logout()
      clearAuthToken()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (e) {
      console.error('退出登录失败:', e)
      // 即使接口失败，也清除本地token并跳转
      clearAuthToken()
      router.push('/login')
    } finally {
      loggingOut.value = false
    }
  } catch {
    // 用户取消
  }
}

async function handleSaveSysPrompt() {
  if (!sysPromptValue.value.trim()) {
    ElMessage.warning('系统提示词不能为空')
    return
  }
  sysPromptSaving.value = true
  try {
    const res = await saveSetting({
      settingKey: SYS_PROMPT_KEY,
      settingValue: sysPromptValue.value,
      settingDesc: 'AI智能回复的系统提示词'
    })
    if (res.code === 200) {
      ElMessage.success('系统提示词保存成功')
      sysPromptLoaded.value = true
    }
  } finally {
    sysPromptSaving.value = false
  }
}

function handleResetSysPrompt() {
  sysPromptValue.value = DEFAULT_SYS_PROMPT
}
</script>

<template>
  <div class="settings">
    <div class="s__card">
      <div class="s__card-title">系统账号</div>

      <div v-if="loading" class="s__loading">
        <div class="s__spinner"></div>
        <span>加载中...</span>
      </div>

      <div v-else class="s__info">
        <div class="s__info-row">
          <span class="s__info-label">账号</span>
          <span class="s__info-value">{{ username }}</span>
        </div>
        <div class="s__info-row">
          <span class="s__info-label">最后登录时间</span>
          <span class="s__info-value">{{ lastLoginTime || '-' }}</span>
        </div>
      </div>
    </div>

    <div class="s__card">
      <div class="s__card-title">系统提示词</div>
      <p class="s__prompt-desc">配置 AI 智能回复的系统提示词，用于设定 AI 的角色和行为规则</p>
      <div class="s__prompt-form">
        <textarea
          v-model="sysPromptValue"
          class="s__textarea"
          placeholder="请输入系统提示词"
          :disabled="sysPromptSaving"
          rows="5"
        ></textarea>
        <div class="s__prompt-actions">
          <button
            class="s__btn s__btn--secondary"
            :disabled="sysPromptSaving"
            @click="handleResetSysPrompt"
          >
            恢复默认
          </button>
          <button
            class="s__btn s__btn--primary"
            :disabled="sysPromptSaving"
            @click="handleSaveSysPrompt"
          >
            {{ sysPromptSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <div class="s__card">
      <div class="s__card-title">退出登录</div>
      <div class="s__logout-content">
        <p class="s__logout-text">退出当前系统账号，退出后需要重新登录</p>
        <button
          class="s__btn s__btn--danger"
          :disabled="loggingOut"
          @click="handleLogout"
        >
          {{ loggingOut ? '退出中...' : '退出登录' }}
        </button>
      </div>
    </div>

    <div class="s__card">
      <div class="s__card-header">
        <div class="s__card-title">修改密码</div>
        <button
          v-if="!showPasswordForm"
          class="s__toggle-btn"
          @click="showPasswordForm = true"
        >
          修改密码
        </button>
      </div>

      <div v-if="showPasswordForm" class="s__password-form">
        <div class="s__field">
          <label class="s__label">原密码</label>
          <div class="s__input-wrap">
            <input
              v-model="oldPassword"
              :type="showOldPassword ? 'text' : 'password'"
              class="s__input"
              placeholder="请输入原密码"
              :disabled="changingPassword"
            />
            <button class="s__eye-btn" @click="showOldPassword = !showOldPassword" tabindex="-1">
              {{ showOldPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div class="s__field">
          <label class="s__label">新密码</label>
          <div class="s__input-wrap">
            <input
              v-model="newPassword"
              :type="showNewPassword ? 'text' : 'password'"
              class="s__input"
              placeholder="请输入新密码（6-50位）"
              :disabled="changingPassword"
            />
            <button class="s__eye-btn" @click="showNewPassword = !showNewPassword" tabindex="-1">
              {{ showNewPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div class="s__field">
          <label class="s__label">确认新密码</label>
          <div class="s__input-wrap">
            <input
              v-model="confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              class="s__input"
              placeholder="请再次输入新密码"
              :disabled="changingPassword"
              @keydown.enter="handleChangePassword"
            />
            <button class="s__eye-btn" @click="showConfirmPassword = !showConfirmPassword" tabindex="-1">
              {{ showConfirmPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div class="s__btn-row">
          <button class="s__btn s__btn--secondary" :disabled="changingPassword" @click="showPasswordForm = false">
            取消
          </button>
          <button class="s__btn s__btn--primary" :disabled="changingPassword" @click="handleChangePassword">
            {{ changingPassword ? '请稍候...' : '确认修改' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings {
  --d-border: rgba(0, 0, 0, 0.06);
  --d-border-strong: rgba(0, 0, 0, 0.12);
  --d-text-primary: #1d1d1f;
  --d-text-secondary: #6e6e73;
  --d-text-tertiary: #86868b;
  --d-radius-sm: 8px;
  --d-radius-md: 12px;
  --d-space-2: 8px;
  --d-space-3: 12px;
  --d-space-4: 16px;
  --d-space-5: 20px;
  --d-transition: 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);

  max-width: 600px;
  display: flex;
  flex-direction: column;
  gap: var(--d-space-4);
}

.s__card {
  background: #fff;
  border-radius: var(--d-radius-md);
  border: 1px solid var(--d-border);
  padding: var(--d-space-5);
}

.s__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--d-space-3);
}

.s__card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--d-text-primary);
  margin-bottom: var(--d-space-3);
}

.s__card-header .s__card-title {
  margin-bottom: 0;
}

/* Loading */
.s__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--d-space-2);
  padding: var(--d-space-4);
  color: var(--d-text-tertiary);
  font-size: 13px;
}

.s__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--d-border-strong);
  border-top-color: var(--d-text-primary);
  border-radius: 50%;
  animation: s-spin 0.6s linear infinite;
}

@keyframes s-spin {
  to { transform: rotate(360deg); }
}

/* Info */
.s__info {
  display: flex;
  flex-direction: column;
  gap: var(--d-space-3);
}

.s__info-row {
  display: flex;
  align-items: center;
  gap: var(--d-space-3);
}

.s__info-label {
  font-size: 13px;
  color: var(--d-text-tertiary);
  min-width: 100px;
  flex-shrink: 0;
}

.s__info-value {
  font-size: 13px;
  color: var(--d-text-primary);
  font-weight: 500;
}

/* Toggle Button */
.s__toggle-btn {
  height: 30px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 500;
  color: var(--d-text-primary);
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid var(--d-border-strong);
  border-radius: var(--d-radius-sm);
  cursor: pointer;
  transition: all var(--d-transition);
}

.s__toggle-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

/* Password Form */
.s__password-form {
  display: flex;
  flex-direction: column;
  gap: var(--d-space-4);
}

.s__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.s__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--d-text-primary);
}

.s__input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.s__input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  font-size: 14px;
  color: var(--d-text-primary);
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid var(--d-border-strong);
  border-radius: var(--d-radius-sm);
  outline: none;
  transition: all var(--d-transition);
  box-sizing: border-box;
}

.s__input:focus {
  border-color: var(--d-text-primary);
  background: #fff;
}

.s__input::placeholder {
  color: var(--d-text-tertiary);
}

.s__input:disabled {
  opacity: 0.5;
}

.s__eye-btn {
  position: absolute;
  right: 10px;
  background: none;
  border: none;
  font-size: 12px;
  color: var(--d-text-tertiary);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: color var(--d-transition);
}

.s__eye-btn:hover {
  color: var(--d-text-primary);
}

/* Button Row */
.s__btn-row {
  display: flex;
  gap: var(--d-space-3);
  justify-content: flex-end;
  margin-top: var(--d-space-2);
}

.s__btn {
  height: 36px;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--d-radius-sm);
  cursor: pointer;
  transition: all var(--d-transition);
  border: none;
}

.s__btn--primary {
  background: var(--d-text-primary);
  color: #fff;
}

.s__btn--primary:hover {
  background: #2a2a2a;
}

.s__btn--primary:active {
  transform: scale(0.97);
}

.s__btn--secondary {
  background: rgba(0, 0, 0, 0.04);
  color: var(--d-text-primary);
  border: 1px solid var(--d-border-strong);
}

.s__btn--secondary:hover {
  background: rgba(0, 0, 0, 0.06);
}

.s__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.s__btn--danger {
  background: #ff4d4f;
  color: #fff;
}

.s__btn--danger:hover {
  background: #ff7875;
}

.s__btn--danger:active {
  transform: scale(0.97);
}

/* Logout */
.s__logout-content {
  display: flex;
  flex-direction: column;
  gap: var(--d-space-3);
}

.s__logout-text {
  font-size: 13px;
  color: var(--d-text-secondary);
  margin: 0;
}

.s__logout-content .s__btn--danger {
  align-self: flex-start;
  min-width: auto;
}

/* Sys Prompt */
.s__prompt-desc {
  font-size: 13px;
  color: var(--d-text-secondary);
  margin: 0 0 var(--d-space-3) 0;
}

.s__prompt-form {
  display: flex;
  flex-direction: column;
  gap: var(--d-space-3);
}

.s__textarea {
  width: 100%;
  min-height: 120px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--d-text-primary);
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid var(--d-border-strong);
  border-radius: var(--d-radius-sm);
  outline: none;
  transition: all var(--d-transition);
  box-sizing: border-box;
  resize: vertical;
  font-family: inherit;
}

.s__textarea:focus {
  border-color: var(--d-text-primary);
  background: #fff;
}

.s__textarea::placeholder {
  color: var(--d-text-tertiary);
}

.s__textarea:disabled {
  opacity: 0.5;
}

.s__prompt-actions {
  display: flex;
  gap: var(--d-space-3);
  justify-content: flex-end;
}

/* Responsive */
@media (max-width: 768px) {
  .settings {
    max-width: 100%;
  }

  .s__card {
    padding: var(--d-space-4);
  }
}

@media (max-width: 480px) {
  .s__info-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .s__btn-row {
    flex-direction: column;
  }

  .s__btn {
    width: 100%;
  }
}
</style>
