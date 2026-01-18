<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { courseApi, type Grade, type Course } from '@/api/course'
import { toast } from 'vue-sonner'
import { BookOpen, Trophy, Calendar, ClipboardCheck, Search, Filter } from 'lucide-vue-next'

const grades = ref<Grade[]>([])
const myCourses = ref<Course[]>([])
const loading = ref(true)
const activeTab = ref<'grades' | 'courses'>('grades')
const currentSemester = ref('2025-2026-1')

const fetchData = async () => {
  loading.value = true
  try {
    const gradeRes = await courseApi.getMyGrades(currentSemester.value)
    grades.value = gradeRes.data
    const courseRes = await courseApi.getMyCourses()
    myCourses.value = courseRes.data
  } catch (error) {
    toast.error('获取教务数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-10 px-6 max-w-6xl mx-auto">
      <div class="flex flex-col md:flex-row md:items-center justify-between mb-12 gap-6">
        <div>
          <h1 class="text-3xl font-black text-slate-900 tracking-tighter flex items-center gap-3">
            <BookOpen class="w-8 h-8" /> 学业中心
          </h1>
          <p class="text-sm text-slate-500 mt-2 font-medium">查看你的课程安排、成绩单与学分进度</p>
        </div>

        <div class="flex items-center bg-slate-100 p-1.5 rounded-2xl border border-slate-200/50">
          <button 
            @click="activeTab = 'grades'"
            :class="[
              'flex items-center gap-2 px-6 py-2.5 rounded-xl text-xs font-black transition-all tracking-widest uppercase',
              activeTab === 'grades' ? 'bg-white text-slate-900 shadow-xl shadow-slate-200' : 'text-slate-400 hover:text-slate-600'
            ]"
          >
            <Trophy class="w-4 h-4" /> 成绩查询
          </button>
          <button 
            @click="activeTab = 'courses'"
            :class="[
              'flex items-center gap-2 px-6 py-2.5 rounded-xl text-xs font-black transition-all tracking-widest uppercase',
              activeTab === 'courses' ? 'bg-white text-slate-900 shadow-xl shadow-slate-200' : 'text-slate-400 hover:text-slate-600'
            ]"
          >
            <Calendar class="w-4 h-4" /> 我的课表
          </button>
        </div>
      </div>

      <div v-if="loading" class="animate-pulse space-y-6">
        <div v-for="i in 5" :key="i" class="h-20 bg-slate-100 rounded-2xl"></div>
      </div>

      <div v-else class="space-y-8">
        <!-- Grades View -->
        <div v-if="activeTab === 'grades'" class="animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div class="bg-white border border-slate-200 rounded-[2.5rem] overflow-hidden shadow-sm">
            <div class="px-8 py-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
              <div class="flex items-center gap-4">
                <Filter class="w-4 h-4 text-slate-400" />
                <select v-model="currentSemester" @change="fetchData" class="bg-transparent border-0 text-sm font-bold text-slate-900 focus:ring-0 outline-none cursor-pointer">
                  <option value="2025-2026-1">2025-2026 学年 第一学期</option>
                  <option value="2024-2025-2">2024-2025 学年 第二学期</option>
                </select>
              </div>
              <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest">共 {{ grades.length }} 门课程</span>
            </div>

            <div class="overflow-x-auto">
              <table class="w-full text-left">
                <thead>
                  <tr class="text-[10px] font-black text-slate-400 uppercase tracking-widest border-b border-slate-50">
                    <th class="px-8 py-5">课程名称</th>
                    <th class="px-8 py-5">学分</th>
                    <th class="px-8 py-5">最终成绩</th>
                    <th class="px-8 py-5">状态</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-50">
                  <tr v-for="grade in grades" :key="grade.id" class="hover:bg-slate-50/50 transition-colors group">
                    <td class="px-8 py-6">
                      <span class="text-sm font-bold text-slate-900 group-hover:text-slate-600 transition-colors">离散数学与应用</span>
                    </td>
                    <td class="px-8 py-6">
                      <span class="text-sm font-mono font-bold text-slate-500">4.0</span>
                    </td>
                    <td class="px-8 py-6">
                      <span class="text-lg font-black tracking-tighter" :class="grade.score >= 90 ? 'text-emerald-500' : 'text-slate-900'">{{ grade.score }}</span>
                    </td>
                    <td class="px-8 py-6">
                      <span v-if="grade.isPassed" class="px-3 py-1 bg-emerald-50 text-emerald-600 text-[10px] font-black rounded-lg border border-emerald-100">PASSED</span>
                      <span v-else class="px-3 py-1 bg-rose-50 text-rose-600 text-[10px] font-black rounded-lg border border-rose-100">FAILED</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            
            <div v-if="grades.length === 0" class="py-20 text-center">
              <div class="text-slate-300 text-sm font-medium italic">该学期暂无成绩记录</div>
            </div>
          </div>
        </div>

        <!-- Courses View -->
        <div v-if="activeTab === 'courses'" class="grid grid-cols-1 md:grid-cols-2 gap-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div v-for="course in myCourses" :key="course.id" class="bg-white border border-slate-200 rounded-[2rem] p-8 hover:border-slate-400 hover:shadow-xl hover:shadow-slate-200/50 transition-all group">
            <div class="flex items-start justify-between mb-6">
              <div>
                <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest block mb-1">{{ course.courseCode }}</span>
                <h3 class="text-lg font-bold text-slate-900 group-hover:text-slate-600 transition-colors">{{ course.name }}</h3>
              </div>
              <div class="w-10 h-10 bg-slate-50 rounded-xl flex items-center justify-center text-slate-400 group-hover:bg-slate-900 group-hover:text-white transition-all">
                <BookOpen class="w-5 h-5" />
              </div>
            </div>
            
            <div class="space-y-4">
              <div class="flex items-center gap-3 text-sm font-medium text-slate-500">
                <User class="w-4 h-4" /> {{ course.teacherName }} 教授
              </div>
              <div class="flex items-center gap-3 text-sm font-medium text-slate-500">
                <Calendar class="w-4 h-4" /> {{ course.classTime }}
              </div>
              <div class="flex items-center gap-3 text-sm font-medium text-slate-500">
                <MapPin class="w-4 h-4" /> {{ course.location }}
              </div>
            </div>
            
            <div class="mt-8 pt-6 border-t border-slate-50 flex items-center justify-between">
              <span class="px-3 py-1 bg-slate-50 text-slate-400 text-[10px] font-black rounded-lg uppercase">{{ course.credits }} 学分</span>
              <button class="text-xs font-bold text-slate-900 hover:underline">查看大纲</button>
            </div>
          </div>
          
          <div v-if="myCourses.length === 0" class="col-span-2 py-20 text-center bg-white border border-slate-200 rounded-[2.5rem]">
            <p class="text-slate-400 font-medium">你还没有选择任何课程，快去选课系统看看吧！</p>
          </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
