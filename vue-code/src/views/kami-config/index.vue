<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKamiConfigsByAccountId,
  saveKamiConfig,
  deleteKamiConfig,
  getKamiItemsByConfigId,
  addKamiItem,
  batchImportKamiItems,
  deleteKamiItem,
  resetKamiItem,
  type KamiConfig,
  type KamiItem
} from '@/api/kami-config'
import { getAccountList } from '@/api/account'
import type { Account } from '@/types'

const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const kamiConfigs = ref<KamiConfig[]>([])
const configLoading = ref(false)

const selectedConfigId = ref<number | null>(null)
const kamiItems = ref<KamiItem[]>([])
const itemsLoading = ref(false)

const showCreateDialog = ref(false)
const createForm = ref({
  aliasName: '',
  deliveryMethod: 1,
  allowRepeat: 0
})
const createLoading = ref(false)

const showImportDialog = ref(false)
const importContent = ref('')
const importLoading = ref(false)

const showAddDialog = ref(false)
const addContent = ref('')
const addLoading = ref(false)

const isMobile = ref(false)
const rulesExpanded = ref(false)

const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

const selectedConfig = computed(() => {
  return kamiConfigs.value.find(c => c.id === selectedConfigId.value)
})

const loadAccounts = async () => {
  try {
    const res = await getAccountList()
    if (res.code === 200 && res.data) {
      accounts.value = res.data.accounts || []
      if (accounts.value.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = accounts.value[0].id
      }
    }
  } catch (e) {
    console.error('加载账号失败', e)
  }
}

const loadKamiConfigs = async () => {
  if (!selectedAccountId.value) return
  configLoading.value = true
  try {
    const res = await getKamiConfigsByAccountId(selectedAccountId.value)
    if (res.code === 200) {
      kamiConfigs.value = res.data || []
      if (kamiConfigs.value.length > 0 && !selectedConfigId.value) {
        selectedConfigId.value = kamiConfigs.value[0].id
        loadKamiItems()
      } else if (kamiConfigs.value.length === 0) {
        selectedConfigId.value = null
        kamiItems.value = []
      }
    }
  } catch (e) {
    console.error('加载卡密配置失败', e)
  } finally {
    configLoading.value = false
  }
}

const loadKamiItems = async () => {
  if (!selectedConfigId.value) return
  itemsLoading.value = true
  try {
    const res = await getKamiItemsByConfigId(selectedConfigId.value)
    if (res.code === 200) {
      kamiItems.value = res.data || []
    }
  } catch (e) {
    console.error('加载卡密列表失败', e)
  } finally {
    itemsLoading.value = false
  }
}

const handleAccountChange = () => {
  selectedConfigId.value = null
  kamiItems.value = []
  loadKamiConfigs()
}

const selectConfig = (config: KamiConfig) => {
  selectedConfigId.value = config.id
  loadKamiItems()
}

const handleCreate = async () => {
  if (!selectedAccountId.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  createLoading.value = true
  try {
    const res = await saveKamiConfig({
      xianyuAccountId: selectedAccountId.value,
      aliasName: createForm.value.aliasName || '未命名',
      deliveryMethod: createForm.value.deliveryMethod,
      allowRepeat: createForm.value.allowRepeat
    })
    if (res.code === 200) {
      ElMessage.success('创建成功')
      showCreateDialog.value = false
      createForm.value = { aliasName: '', deliveryMethod: 1, allowRepeat: 0 }
      await loadKamiConfigs()
      if (res.data?.id) {
        selectedConfigId.value = res.data.id
        loadKamiItems()
      }
    } else {
      ElMessage.error(res.msg || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    createLoading.value = false
  }
}

const handleDeleteConfig = async (config: KamiConfig) => {
  try {
    await ElMessageBox.confirm(
      `确定删除卡密配置「${config.aliasName || config.id}」及其所有卡密？`,
      '删除确认',
      { type: 'warning' }
    )
    const res = await deleteKamiConfig(config.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (selectedConfigId.value === config.id) {
        selectedConfigId.value = null
        kamiItems.value = []
      }
      loadKamiConfigs()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch {}
}

const handleAddKami = async () => {
  if (!addContent.value.trim()) {
    ElMessage.warning('请输入卡密内容')
    return
  }
  addLoading.value = true
  try {
    const res = await addKamiItem({
      kamiConfigId: selectedConfigId.value!,
      kamiContent: addContent.value.trim()
    })
    if (res.code === 200) {
      ElMessage.success('添加成功')
      showAddDialog.value = false
      addContent.value = ''
      loadKamiItems()
      loadKamiConfigs()
    } else {
      ElMessage.error(res.msg || '添加失败')
    }
  } catch (e) {
    ElMessage.error('添加失败')
  } finally {
    addLoading.value = false
  }
}

const handleBatchImport = async () => {
  if (!importContent.value.trim()) {
    ElMessage.warning('请输入卡密内容')
    return
  }
  importLoading.value = true
  try {
    const res = await batchImportKamiItems({
      kamiConfigId: selectedConfigId.value!,
      kamiContents: importContent.value
    })
    if (res.code === 200) {
      ElMessage.success(res.msg || '导入成功')
      showImportDialog.value = false
      importContent.value = ''
      loadKamiItems()
      loadKamiConfigs()
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

const handleDeleteItem = async (item: KamiItem) => {
  try {
    await ElMessageBox.confirm('确定删除该卡密？', '删除确认', { type: 'warning' })
    const res = await deleteKamiItem(item.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadKamiItems()
      loadKamiConfigs()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch {}
}

const handleResetItem = async (item: KamiItem) => {
  try {
    await ElMessageBox.confirm('确定重置该卡密为未使用状态？', '重置确认', { type: 'warning' })
    const res = await resetKamiItem(item.id)
    if (res.code === 200) {
      ElMessage.success('重置成功')
      loadKamiItems()
      loadKamiConfigs()
    } else {
      ElMessage.error(res.msg || '重置失败')
    }
  } catch {}
}

const handleUpdateConfigField = async (field: 'deliveryMethod' | 'allowRepeat', value: number) => {
  if (!selectedConfig.value) return
  const config = kamiConfigs.value.find(c => c.id === selectedConfigId.value)
  if (config) {
    (config as any)[field] = value
  }
  try {
    const res = await saveKamiConfig({
      id: selectedConfig.value.id,
      xianyuAccountId: selectedConfig.value.xianyuAccountId,
      [field]: value
    })
    if (res.code === 200 && res.data) {
      const idx = kamiConfigs.value.findIndex(c => c.id === selectedConfigId.value)
      if (idx !== -1) {
        kamiConfigs.value[idx] = res.data
      }
    }
  } catch {}
}

watch(selectedAccountId, () => {
  if (selectedAccountId.value) {
    selectedConfigId.value = null
    kamiItems.value = []
    loadKamiConfigs()
  }
})

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  loadAccounts()
})
</script>

<template>
  <div class="kami-page">
    <header class="kami-page__header">
      <h1 class="kami-page__title">🔑 卡密配置</h1>
      <div class="kami-page__actions">
        <el-select
          v-model="selectedAccountId"
          placeholder="选择账号"
          class="account-select"
          @change="handleAccountChange"
        >
          <el-option
            v-for="acc in accounts"
            :key="acc.id"
            :label="acc.accountNote || `账号${acc.id}`"
            :value="acc.id"
          />
        </el-select>
        <el-button type="primary" @click="showCreateDialog = true" :disabled="!selectedAccountId">
          新建配置
        </el-button>
      </div>
    </header>

    <div class="kami-page__body">
      <div class="kami-page__sidebar">
        <div v-if="configLoading" class="kami-page__empty">加载中...</div>
        <div v-else-if="kamiConfigs.length === 0" class="kami-page__empty">暂无配置，点击右上角新建</div>
        <div
          v-for="config in kamiConfigs"
          :key="config.id"
          class="config-card"
          :class="{ 'config-card--active': selectedConfigId === config.id }"
          @click="selectConfig(config)"
        >
          <div class="config-card__name">{{ config.aliasName || `配置#${config.id}` }}</div>
          <div class="config-card__stats">
            <span class="config-card__stat">总量 {{ config.totalCount }}</span>
            <span class="config-card__stat used">已用 {{ config.usedCount }}</span>
            <span class="config-card__stat avail">可用 {{ config.availableCount }}</span>
          </div>
          <div class="config-card__meta">
            {{ config.deliveryMethod === 1 ? '随机发货' : '顺序发货' }}
            · {{ config.allowRepeat === 1 ? '允许重复' : '不重复' }}
          </div>
          <el-button
            class="config-card__del"
            type="danger"
            text
            size="small"
            @click.stop="handleDeleteConfig(config)"
          >删除</el-button>
        </div>
      </div>

      <div class="kami-page__main">
        <div v-if="!selectedConfig" class="kami-page__empty-main">请选择左侧卡密配置</div>
        <template v-else>
          <div class="kami-detail__header">
            <h2>{{ selectedConfig.aliasName || `配置#${selectedConfig.id}` }}</h2>
            <div class="kami-detail__actions">
              <el-button @click="showAddDialog = true">添加卡密</el-button>
              <el-button type="primary" @click="showImportDialog = true">批量导入</el-button>
            </div>
          </div>

          <div class="rule-card" :class="{ 'rule-card--collapsed': !rulesExpanded }">
            <div class="rule-card__header" @click="rulesExpanded = !rulesExpanded">
              <span class="rule-card__icon">⚙️</span>
              <span class="rule-card__title">规则配置</span>
              <span class="rule-card__arrow">{{ rulesExpanded ? '▲' : '▼' }}</span>
              <span v-if="!rulesExpanded" class="rule-card__summary">
                {{ selectedConfig.deliveryMethod === 1 ? '随机' : '顺序' }}发货
                ·
                {{ selectedConfig.allowRepeat === 1 ? '允许重复' : '不重复' }}
              </span>
            </div>
            <div v-show="rulesExpanded" class="rule-card__body">
              <div class="rule-card__row">
                <span class="rule-card__label">📦 发货方式</span>
                <el-radio-group
                  :model-value="selectedConfig.deliveryMethod"
                  @change="(v: number) => handleUpdateConfigField('deliveryMethod', v)"
                >
                  <el-radio :value="1">随机发货</el-radio>
                  <el-radio :value="2">顺序发货</el-radio>
                </el-radio-group>
              </div>
              <div class="rule-card__row">
                <span class="rule-card__label">🔁 重复发货</span>
                <el-radio-group
                  :model-value="selectedConfig.allowRepeat"
                  @change="(v: number) => handleUpdateConfigField('allowRepeat', v)"
                >
                  <el-radio :value="0">不允许</el-radio>
                  <el-radio :value="1">允许</el-radio>
                </el-radio-group>
              </div>
            </div>
          </div>

          <div class="kami-detail__table">
            <div v-if="itemsLoading" class="kami-page__empty">加载中...</div>
            <el-table v-else :data="kamiItems" stripe style="width: 100%" max-height="500">
              <el-table-column prop="sortOrder" label="序号" width="70" />
              <el-table-column prop="kamiContent" label="卡密内容" min-width="200" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
                    {{ row.status === 0 ? '未使用' : '已使用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="orderId" label="订单ID" width="160" show-overflow-tooltip />
              <el-table-column prop="usedTime" label="使用时间" width="170" />
              <el-table-column prop="createTime" label="添加时间" width="170" />
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status === 1"
                    type="warning"
                    text
                    size="small"
                    @click="handleResetItem(row)"
                  >重置</el-button>
                  <el-button type="danger" text size="small" @click="handleDeleteItem(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </div>

    <el-dialog v-model="showCreateDialog" title="新建卡密配置" width="460" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="别名">
          <el-input v-model="createForm.aliasName" placeholder="请输入别名" maxlength="50" />
        </el-form-item>
        <el-form-item label="发货方式">
          <el-radio-group v-model="createForm.deliveryMethod">
            <el-radio :value="1">随机发货</el-radio>
            <el-radio :value="2">顺序发货</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重复发货">
          <el-radio-group v-model="createForm.allowRepeat">
            <el-radio :value="0">不允许</el-radio>
            <el-radio :value="1">允许</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="createLoading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAddDialog" title="添加卡密" width="460" :close-on-click-modal="false">
      <el-input v-model="addContent" type="textarea" :rows="3" placeholder="请输入卡密内容" />
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddKami" :loading="addLoading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showImportDialog" title="批量导入卡密" width="560" :close-on-click-modal="false">
      <p style="color: #909399; font-size: 13px; margin-bottom: 8px;">每行一条卡密，重复卡密将自动跳过</p>
      <el-input v-model="importContent" type="textarea" :rows="10" placeholder="卡密1&#10;卡密2&#10;卡密3" />
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBatchImport" :loading="importLoading">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.kami-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fff;
  overflow: hidden;
}
.kami-page__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.kami-page__title {
  font-size: 20px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0;
}
.kami-page__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.account-select {
  width: 180px;
}
.kami-page__body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}
.kami-page__sidebar {
  width: 260px;
  flex-shrink: 0;
  overflow-y: auto;
  border-right: 1px solid rgba(0,0,0,0.06);
  padding-right: 12px;
}
.kami-page__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.kami-page__empty,
.kami-page__empty-main {
  color: #86868b;
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}
.config-card {
  padding: 12px;
  border: 1px solid rgba(0,0,0,0.06);
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.config-card:hover {
  border-color: rgba(0,122,255,0.3);
}
.config-card--active {
  border-color: #007aff;
  background: rgba(0,122,255,0.04);
}
.config-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 6px;
}
.config-card__stats {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #6e6e73;
  margin-bottom: 4px;
}
.config-card__stat.used { color: #ff9500; }
.config-card__stat.avail { color: #34c759; }
.config-card__meta {
  font-size: 11px;
  color: #86868b;
}
.config-card__del {
  position: absolute;
  top: 8px;
  right: 8px;
}
.kami-detail__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.kami-detail__header h2 {
  font-size: 16px;
  font-weight: 600;
  color: #1d1d1f;
  margin: 0;
}
.kami-detail__actions {
  display: flex;
  gap: 8px;
}
.kami-detail__rules {
  margin-bottom: 12px;
  flex-shrink: 0;
}
.rule-card {
  padding: 10px 16px;
  background: rgba(0,0,0,0.02);
  border: 1px solid rgba(0,0,0,0.06);
  border-radius: 10px;
  margin-bottom: 12px;
}
.rule-card--collapsed {
  margin-bottom: 8px;
}
.rule-card__header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
}
.rule-card__icon {
  font-size: 14px;
}
.rule-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}
.rule-card__arrow {
  font-size: 10px;
  color: #86868b;
  margin-left: 4px;
}
.rule-card__summary {
  font-size: 12px;
  color: #86868b;
  margin-left: auto;
}
.rule-card__body {
  padding-top: 10px;
}
.rule-card__row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.rule-card__row:last-child {
  margin-bottom: 0;
}
.rule-card__label {
  font-size: 13px;
  color: #6e6e73;
  white-space: nowrap;
}
.kami-detail__table {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
@media screen and (max-width: 768px) {
  .kami-page__body {
    flex-direction: column;
  }
  .kami-page__sidebar {
    width: 100%;
    border-right: none;
    padding-right: 0;
    border-bottom: 1px solid rgba(0,0,0,0.06);
    padding-bottom: 12px;
    max-height: 200px;
  }
  .kami-page__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
