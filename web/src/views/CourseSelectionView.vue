<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { courseApi, type Course } from '@/api/course'
import { toast } from 'vue-sonner'
import { Search, Loader2, Plus, Check, Info } from 'lucide-vue-next'

const courses = ref<Course[]>([])
const loading = ref(true)
const selecting = ref<number | null>(null)
const page = ref(1)
const keyword = ref('')

const fetchCourses = async () => {
  loading.value = true
  try {
    const res = await courseApi.getList({ page: page.value, pageSize: 20, keyword: keyword.value })
    courses.value = res.data.records
  } catch (error) {
    toast.error('获取课程列表失败')
  } finally {
    loading.value = false
  }
}

const handleSelect = async (id: number) => {
  selecting.value = id
  try {
    await courseApi.select(id)
    toast.success('选课成功！')
    fetchCourses()
  } catch (error: any) {
    toast.error(error.response?.data?.message || '选课失败')
  } finally {
    selecting.value = null
  }
}

onMounted(fetchCourses)
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-10 px-6 max-w-6xl mx-auto">
      <div class="flex flex-col md:flex-row md:items-center justify-between mb-12 gap-6">
        <div>
          <h1 class="text-3xl font-black text-slate-900 tracking-tighter flex items-center gap-3">
            <Plus class="w-8 h-8" /> 选课系统
          </h1>
          <p class="text-sm text-slate-500 mt-2 font-medium uppercase tracking-widest">2025-2026 第一学期 选课开放中</p>
        </div>

        <div class="relative w-full md:w-96 group">
          <Search class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-slate-900 transition-colors" />
          <input 
            v-model="keyword"
            @keyup.enter="fetchCourses"
            type="text" 
            placeholder="搜索课程名称、编号或教师..."
            class="w-full bg-white border border-slate-200 rounded-2xl py-3.5 pl-12 pr-4 text-sm font-medium focus:ring-4 focus:ring-slate-900/5 focus:border-slate-900 outline-none transition-all"
          />
        </div>
      </div>

      <!-- Warning/Info Banner -->
      <div class="bg-amber-50 border border-amber-100 rounded-2xl p-5 mb-10 flex items-start gap-4">
        <div class="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-amber-500 shadow-sm shrink-0">
          <Info class="w-5 h-5" />
        </div>
        <div>
          <h4 class="text-sm font-bold text-amber-900">选课须知</h4>
          <p class="text-xs text-amber-700 mt-1 leading-relaxed">
            1. 请务必在规定时间内完成选课，逾期系统将自动关闭。<br/>
            2. 每个学分上限为 25.0，超过限制将无法继续提交。<br/>
            3. 热门课程人数较多，请确保网络通畅。
          </p>
        </div>
      </div>

      <div v-if="loading" class="flex justify-center py-20">
        <Loader2 class="w-10 h-10 text-slate-900 animate-spin" />
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div v-for="course in courses" :key="course.id" class="bg-white border border-slate-200 rounded-[2.5rem] p-8 flex flex-col hover:border-slate-400 hover:shadow-2xl hover:shadow-slate-200/50 transition-all group">
          <div class="mb-6">
            <div class="flex items-center justify-between mb-3">
              <span class="px-2.5 py-1 bg-slate-50 text-slate-400 text-[9px] font-black uppercase tracking-widest rounded-lg border border-slate-100">
                {{ course.courseCode }}
              </span>
              <span class="text-[10px] font-black" :class="course.currentCapacity >= course.maxCapacity ? 'text-rose-500' : 'text-emerald-500'">
                {{ course.currentCapacity }} / {{ course.maxCapacity }} 已选
              </span>
            </div>
            <h3 class="text-xl font-bold text-slate-900 group-hover:text-slate-600 transition-colors leading-tight">{{ course.name }}</h3>
          </div>

          <div class="space-y-3 mb-8 flex-1">
            <div class="flex items-center gap-2 text-xs font-medium text-slate-500">
              <span class="text-slate-300">任课教师:</span> {{ course.teacherName }}
            </div>
            <div class="flex items-center gap-2 text-xs font-medium text-slate-500">
              <span class="text-slate-300">上课时间:</span> {{ course.classTime }}
            </div>
            <div class="flex items-center gap-2 text-xs font-medium text-slate-500">
              <span class="text-slate-300">学分:</span> <span class="font-bold text-slate-900">{{ course.credits }}</span>
            </div>
          </div>

          <button 
            @click="handleSelect(course.id)"
            :disabled="course.currentCapacity >= course.maxCapacity || selecting === course.id"
            class="w-full py-4 rounded-2xl text-xs font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2"
            :class="[
              course.currentCapacity >= course.maxCapacity 
                ? 'bg-slate-50 text-slate-300 cursor-not-allowed' 
                : 'bg-slate-900 text-white hover:bg-slate-800 shadow-lg shadow-slate-200 active:scale-[0.98]'
            ]"
          >
            <Loader2 v-if="selecting === course.id" class="w-4 h-4 animate-spin" />
            <template v-else>
              {{ course.currentCapacity >= course.maxCapacity ? '人数已满' : '立即选课' }}
            </template>
          </button>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
