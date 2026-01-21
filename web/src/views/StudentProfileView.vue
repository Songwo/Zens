<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { studentApi, type StudentProfile } from '@/api/student'
import { toast } from 'vue-sonner'
import { User, School, BookOpen, GraduationCap, Phone, MapPin, Home, CreditCard, Edit3, Save, X } from 'lucide-vue-next'

const profile = ref<StudentProfile | null>(null)
const loading = ref(true)
const gradStatus = ref('')
const isEditing = ref(false)
const saving = ref(false)

// Form data
const form = ref<Partial<StudentProfile>>({})

const fetchProfile = async () => {
  try {
    const res = await studentApi.getProfile()
    profile.value = res.data
    // Initialize form with profile data
    if (res.data) {
      form.value = { ...res.data }
    }
    const gradRes = await studentApi.checkGraduation()
    gradStatus.value = gradRes.data
  } catch (error) {
    toast.error('获取档案失败')
  } finally {
    loading.value = false
  }
}

const handleEdit = () => {
  // Directly set editing mode
  if (profile.value) {
    form.value = { ...profile.value }
  }
  isEditing.value = true
}

const handleCancel = () => {
  isEditing.value = false
  if (profile.value) {
    form.value = { ...profile.value }
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await studentApi.updateProfile(form.value)
    toast.success('档案更新成功')
    // Update local profile
    profile.value = { ...profile.value, ...form.value } as StudentProfile
    isEditing.value = false
    // Refresh graduation status
    const gradRes = await studentApi.checkGraduation()
    gradStatus.value = gradRes.data
  } catch (error) {
    toast.error('更新失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

const getGradLabel = (status: number) => {
  switch(status) {
    case 0: return '在读'
    case 1: return '毕业'
    case 2: return '延毕'
    default: return '未知'
  }
}

onMounted(fetchProfile)
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-10 px-6 max-w-5xl mx-auto">
      <div class="flex items-center justify-between mb-10">
        <div>
          <h1 class="text-3xl font-black text-slate-900 tracking-tight flex items-center gap-3">
            <User class="w-8 h-8" /> 个人学籍档案
          </h1>
          <p class="text-sm text-slate-500 mt-2 font-medium uppercase tracking-widest">权威教务系统同步数据</p>
        </div>
        <div class="flex items-center gap-4">
          <div v-if="profile && !isEditing" class="px-6 py-3 bg-slate-900 text-white rounded-2xl shadow-xl shadow-slate-200">
            <span class="text-xs font-black uppercase tracking-widest block opacity-50 mb-1">当前学分</span>
            <span class="text-xl font-bold tracking-tighter">{{ profile.totalCredits }} / 120.0</span>
          </div>
          
          <button 
            @click.stop.prevent="handleEdit"
            type="button"
            class="flex items-center gap-2 px-6 py-3 bg-white border border-slate-200 text-slate-900 rounded-2xl font-bold hover:bg-slate-50 transition-colors z-10 relative cursor-pointer shadow-sm hover:shadow-md active:scale-95 select-none"
            :class="{ 'opacity-0 pointer-events-none absolute': loading || isEditing }"
          >
            <Edit3 class="w-4 h-4" /> 编辑资料
          </button>

          <div v-if="isEditing" class="flex gap-3">
             <button 
              @click="handleCancel"
              class="flex items-center gap-2 px-6 py-3 bg-white border border-slate-200 text-slate-500 rounded-2xl font-bold hover:bg-slate-50 transition-colors"
            >
              <X class="w-4 h-4" /> 取消
            </button>
            <button 
              @click="handleSave"
              :disabled="saving"
              class="flex items-center gap-2 px-6 py-3 bg-indigo-600 text-white rounded-2xl font-bold hover:bg-indigo-700 transition-colors disabled:opacity-50"
            >
              <Save class="w-4 h-4" /> {{ saving ? '保存中...' : '保存更改' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="animate-pulse space-y-8">
        <div class="h-64 bg-slate-100 rounded-[2rem]"></div>
        <div class="grid grid-cols-2 gap-8">
          <div class="h-40 bg-slate-100 rounded-[2rem]"></div>
          <div class="h-40 bg-slate-100 rounded-[2rem]"></div>
        </div>
      </div>

      <div v-else class="space-y-8">
        <!-- Main Info Card -->
        <div class="bg-white border border-slate-200 rounded-[2.5rem] p-10 shadow-sm relative overflow-hidden transition-all duration-300" :class="{'ring-4 ring-indigo-50 border-indigo-200': isEditing}">
          <div class="absolute top-0 right-0 p-10 opacity-[0.03]">
            <School class="w-64 h-64" />
          </div>
          
          <div class="grid grid-cols-1 md:grid-cols-2 gap-12 relative z-10">
            <!-- Left Column -->
            <div class="space-y-8">
              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <CreditCard class="w-6 h-6" />
                </div>
                <div class="flex-1">
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">学号 / Student ID</label>
                  <input 
                    v-if="isEditing" 
                    v-model="form.studentNo" 
                    type="text" 
                    class="w-full text-lg font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent placeholder-slate-300"
                    placeholder="请输入学号"
                  >
                  <span v-else class="text-lg font-bold text-slate-900">{{ profile?.studentNo || '未填写' }}</span>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <User class="w-6 h-6" />
                </div>
                <div class="flex-1">
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">真实姓名 / Name</label>
                   <input 
                    v-if="isEditing" 
                    v-model="form.realName" 
                    type="text" 
                    class="w-full text-lg font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent placeholder-slate-300"
                    placeholder="请输入姓名"
                  >
                  <span v-else class="text-lg font-bold text-slate-900">{{ profile?.realName || '未填写' }}</span>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <BookOpen class="w-6 h-6" />
                </div>
                <div class="flex-1">
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">学院专业 / College & Major</label>
                  <div v-if="isEditing" class="flex gap-4">
                    <input 
                      v-model="form.college" 
                      type="text" 
                      class="w-1/2 text-lg font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent placeholder-slate-300"
                      placeholder="学院"
                    >
                    <input 
                      v-model="form.className" 
                      type="text" 
                      class="w-1/2 text-lg font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent placeholder-slate-300"
                      placeholder="班级"
                    >
                  </div>
                  <span v-else class="text-lg font-bold text-slate-900">{{ profile?.college || '-' }} · {{ profile?.className || '-' }}</span>
                </div>
              </div>
            </div>

            <!-- Right Column -->
            <div class="space-y-8">
              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <GraduationCap class="w-6 h-6" />
                </div>
                <div class="flex-1">
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">培养状态 / Status</label>
                  <div class="flex items-center gap-3">
                    <span class="px-3 py-1 bg-emerald-50 text-emerald-600 text-xs font-black rounded-lg border border-emerald-100">{{ getGradLabel(profile?.graduationStatus || 0) }}</span>
                    <span v-if="!isEditing" class="text-sm font-bold text-slate-500">入学日期: {{ profile?.enrollmentDate || '-' }}</span>
                    <input 
                      v-else 
                      v-model="form.enrollmentDate" 
                      type="date" 
                      class="text-sm font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent"
                    >
                  </div>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <Phone class="w-6 h-6" />
                </div>
                <div class="flex-1">
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">联系方式 / Contact</label>
                   <input 
                    v-if="isEditing" 
                    v-model="form.phone" 
                    type="text" 
                    class="w-full text-lg font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent placeholder-slate-300"
                    placeholder="请输入手机号"
                  >
                  <span v-else class="text-lg font-bold text-slate-900">{{ profile?.phone || '未填写' }}</span>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <MapPin class="w-6 h-6" />
                </div>
                <div class="flex-1">
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">所属大学 / University</label>
                  <span class="text-lg font-bold text-slate-900">{{ profile?.campus || '极速' }}校区</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Dorm & Academic Status -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div class="bg-white border border-slate-200 rounded-[2.5rem] p-8 shadow-sm transition-all duration-300" :class="{'ring-4 ring-indigo-50 border-indigo-200': isEditing}">
            <div class="flex items-center gap-3 mb-8">
              <Home class="w-5 h-5 text-slate-900" />
              <h3 class="text-lg font-bold text-slate-900">住宿信息</h3>
            </div>
            <div class="space-y-6">
              <div class="flex justify-between items-center pb-4 border-b border-slate-50">
                <span class="text-sm text-slate-400 font-medium">宿舍楼</span>
                <input 
                  v-if="isEditing" 
                  v-model="form.dormBuilding" 
                  type="text" 
                  class="text-right text-sm font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent w-24 placeholder-slate-300"
                  placeholder="楼号"
                >
                <span v-else class="text-sm font-bold text-slate-900">{{ profile?.dormBuilding || '-' }}</span>
              </div>
              <div class="flex justify-between items-center pb-4 border-b border-slate-50">
                <span class="text-sm text-slate-400 font-medium">房间号</span>
                <input 
                  v-if="isEditing" 
                  v-model="form.dormRoom" 
                  type="text" 
                  class="text-right text-sm font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent w-24 placeholder-slate-300"
                  placeholder="房号"
                >
                <span v-else class="text-sm font-bold text-slate-900">{{ profile?.dormRoom || '-' }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-sm text-slate-400 font-medium">床位号</span>
                <input 
                  v-if="isEditing" 
                  v-model="form.dormBed" 
                  type="text" 
                  class="text-right text-sm font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-1 bg-transparent w-24 placeholder-slate-300"
                  placeholder="床位"
                >
                <span v-else class="text-sm font-bold text-slate-900">{{ profile?.dormBed || '-' }}</span>
              </div>
            </div>
          </div>

          <div class="bg-slate-900 rounded-[2.5rem] p-8 text-white shadow-xl shadow-slate-200 relative overflow-hidden">
            <GraduationCap class="absolute -right-4 -bottom-4 w-32 h-32 opacity-10 rotate-12" />
            <div class="flex items-center gap-3 mb-8">
              <GraduationCap class="w-5 h-5 text-white" />
              <h3 class="text-lg font-bold">毕业判定自检</h3>
            </div>
            <div class="bg-white/5 rounded-2xl p-6 border border-white/10">
              <p class="text-sm font-medium leading-relaxed opacity-90">{{ gradStatus }}</p>
            </div>
            <p class="text-[10px] text-slate-500 mt-6 font-black uppercase tracking-widest italic">注：系统自动计算，仅供参考</p>
          </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
