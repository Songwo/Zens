<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { studentApi, type StudentProfile } from '@/api/student'
import { toast } from 'vue-sonner'
import { User, School, BookOpen, GraduationCap, Phone, MapPin, Home, CreditCard } from 'lucide-vue-next'

const profile = ref<StudentProfile | null>(null)
const loading = ref(true)
const gradStatus = ref('')

const fetchProfile = async () => {
  try {
    const res = await studentApi.getProfile()
    profile.value = res.data
    const gradRes = await studentApi.checkGraduation()
    gradStatus.value = gradRes.data
  } catch (error) {
    toast.error('获取档案失败')
  } finally {
    loading.value = false
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
        <div v-if="profile" class="px-6 py-3 bg-slate-900 text-white rounded-2xl shadow-xl shadow-slate-200">
          <span class="text-xs font-black uppercase tracking-widest block opacity-50 mb-1">当前学分</span>
          <span class="text-xl font-bold tracking-tighter">{{ profile.totalCredits }} / 120.0</span>
        </div>
      </div>

      <div v-if="loading" class="animate-pulse space-y-8">
        <div class="h-64 bg-slate-100 rounded-[2rem]"></div>
        <div class="grid grid-cols-2 gap-8">
          <div class="h-40 bg-slate-100 rounded-[2rem]"></div>
          <div class="h-40 bg-slate-100 rounded-[2rem]"></div>
        </div>
      </div>

      <div v-else-if="profile" class="space-y-8">
        <!-- Main Info Card -->
        <div class="bg-white border border-slate-200 rounded-[2.5rem] p-10 shadow-sm relative overflow-hidden">
          <div class="absolute top-0 right-0 p-10 opacity-[0.03]">
            <School class="w-64 h-64" />
          </div>
          
          <div class="grid grid-cols-1 md:grid-cols-2 gap-12 relative z-10">
            <div class="space-y-8">
              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <CreditCard class="w-6 h-6" />
                </div>
                <div>
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">学号 / Student ID</label>
                  <span class="text-lg font-bold text-slate-900">{{ profile.studentNo }}</span>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <User class="w-6 h-6" />
                </div>
                <div>
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">真实姓名 / Name</label>
                  <span class="text-lg font-bold text-slate-900">{{ profile.realName }}</span>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <BookOpen class="w-6 h-6" />
                </div>
                <div>
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">学院专业 / College & Major</label>
                  <span class="text-lg font-bold text-slate-900">{{ profile.college }} · {{ profile.className }}</span>
                </div>
              </div>
            </div>

            <div class="space-y-8">
              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <GraduationCap class="w-6 h-6" />
                </div>
                <div>
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">培养状态 / Status</label>
                  <div class="flex items-center gap-3">
                    <span class="px-3 py-1 bg-emerald-50 text-emerald-600 text-xs font-black rounded-lg border border-emerald-100">{{ getGradLabel(profile.graduationStatus) }}</span>
                    <span class="text-sm font-bold text-slate-500">入学日期: {{ profile.enrollmentDate }}</span>
                  </div>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <Phone class="w-6 h-6" />
                </div>
                <div>
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">联系方式 / Contact</label>
                  <span class="text-lg font-bold text-slate-900">{{ profile.phone }}</span>
                </div>
              </div>

              <div class="flex items-start gap-6">
                <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400">
                  <MapPin class="w-6 h-6" />
                </div>
                <div>
                  <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">所属大学 / University</label>
                  <span class="text-lg font-bold text-slate-900">{{ profile.campus }} 极速校区</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Dorm & Academic Status -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div class="bg-white border border-slate-200 rounded-[2.5rem] p-8 shadow-sm">
            <div class="flex items-center gap-3 mb-8">
              <Home class="w-5 h-5 text-slate-900" />
              <h3 class="text-lg font-bold text-slate-900">住宿信息</h3>
            </div>
            <div class="space-y-6">
              <div class="flex justify-between items-center pb-4 border-b border-slate-50">
                <span class="text-sm text-slate-400 font-medium">宿舍楼</span>
                <span class="text-sm font-bold text-slate-900">{{ profile.dormBuilding }}</span>
              </div>
              <div class="flex justify-between items-center pb-4 border-b border-slate-50">
                <span class="text-sm text-slate-400 font-medium">房间号</span>
                <span class="text-sm font-bold text-slate-900">{{ profile.dormRoom }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-sm text-slate-400 font-medium">床位号</span>
                <span class="text-sm font-bold text-slate-900">{{ profile.dormBed }}</span>
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
