<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { leaveApi, type LeaveRequest } from '@/api/leave'
import { toast } from 'vue-sonner'
import { ClipboardList, Plus, Loader2, Calendar, FileText, CheckCircle2, Clock, XCircle } from 'lucide-vue-next'

const requests = ref<LeaveRequest[]>([])
const loading = ref(true)
const showForm = ref(false)
const submitting = ref(false)

const formData = ref<LeaveRequest>({
  type: 1,
  startTime: '',
  endTime: '',
  reason: '',
  status: 0
})

const fetchRequests = async () => {
  loading.value = true
  try {
    const res = await leaveApi.getMyList()
    requests.value = res.data.records
  } catch (error) {
    toast.error('获取请假记录失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formData.value.startTime || !formData.value.endTime || !formData.value.reason) {
    toast.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await leaveApi.submit(formData.value)
    toast.success('申请提交成功，请等待审批')
    showForm.value = false
    fetchRequests()
  } catch (error) {
    toast.error('提交失败')
  } finally {
    submitting.value = false
  }
}

const getStatusBadge = (status: number) => {
  switch(status) {
    case 0: return { label: '待审批', class: 'bg-amber-50 text-amber-600 border-amber-100', icon: Clock }
    case 1: return { label: '已通过', class: 'bg-emerald-50 text-emerald-600 border-emerald-100', icon: CheckCircle2 }
    case 2: return { label: '已驳回', class: 'bg-rose-50 text-rose-600 border-rose-100', icon: XCircle }
    default: return { label: '未知', class: 'bg-slate-50 text-slate-400 border-slate-100', icon: Clock }
  }
}

onMounted(fetchRequests)
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-10 px-6 max-w-5xl mx-auto">
      <div class="flex items-center justify-between mb-12">
        <div>
          <h1 class="text-3xl font-black text-slate-900 tracking-tighter flex items-center gap-3">
            <ClipboardList class="w-8 h-8" /> 请假申请
          </h1>
          <p class="text-sm text-slate-500 mt-2 font-medium">线上申请，极速审批 · 实时查看进度</p>
        </div>
        <button 
          @click="showForm = true"
          class="flex items-center gap-2 px-6 py-3 bg-slate-900 text-white rounded-2xl text-sm font-bold shadow-xl shadow-slate-200 hover:bg-slate-800 transition-all active:scale-95"
        >
          <Plus class="w-4 h-4" /> 提交申请
        </button>
      </div>

      <!-- Apply Form Modal -->
      <div v-if="showForm" class="fixed inset-0 z-[100] flex items-center justify-center p-6">
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showForm = false"></div>
        <div class="bg-white w-full max-w-lg rounded-[2.5rem] p-10 relative z-10 shadow-2xl animate-in zoom-in-95 duration-300">
          <h2 class="text-2xl font-black text-slate-900 mb-8">新建请假申请</h2>
          
          <div class="space-y-6">
            <div class="space-y-2">
              <label class="text-xs font-black text-slate-400 uppercase tracking-widest">请假类型</label>
              <div class="flex gap-4">
                <button 
                  v-for="t in [{id:1, n:'事假'}, {id:2, n:'病假'}]" 
                  :key="t.id"
                  @click="formData.type = t.id"
                  :class="[
                    'flex-1 py-3 rounded-xl text-sm font-bold border transition-all',
                    formData.type === t.id ? 'bg-slate-900 text-white border-slate-900' : 'bg-slate-50 text-slate-500 border-slate-100 hover:border-slate-300'
                  ]"
                >
                  {{ t.n }}
                </button>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-2">
                <label class="text-xs font-black text-slate-400 uppercase tracking-widest">开始时间</label>
                <input v-model="formData.startTime" type="datetime-local" class="w-full bg-slate-50 border border-slate-200 rounded-xl py-3 px-4 text-sm font-medium focus:ring-4 focus:ring-slate-900/5 outline-none" />
              </div>
              <div class="space-y-2">
                <label class="text-xs font-black text-slate-400 uppercase tracking-widest">结束时间</label>
                <input v-model="formData.endTime" type="datetime-local" class="w-full bg-slate-50 border border-slate-200 rounded-xl py-3 px-4 text-sm font-medium focus:ring-4 focus:ring-slate-900/5 outline-none" />
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-xs font-black text-slate-400 uppercase tracking-widest">请假理由</label>
              <textarea 
                v-model="formData.reason"
                placeholder="详细说明请假原因..."
                class="w-full bg-slate-50 border border-slate-200 rounded-xl p-4 text-sm font-medium focus:ring-4 focus:ring-slate-900/5 outline-none h-32 resize-none"
              ></textarea>
            </div>

            <div class="flex gap-4 pt-4">
              <button @click="showForm = false" class="flex-1 py-4 text-sm font-bold text-slate-400 hover:text-slate-600 transition-colors">取消</button>
              <button 
                @click="handleSubmit"
                :disabled="submitting"
                class="flex-1 py-4 bg-slate-900 text-white rounded-2xl text-sm font-bold shadow-lg hover:bg-slate-800 transition-all flex items-center justify-center gap-2"
              >
                <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" />
                提交申请
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading" class="flex justify-center py-20">
        <Loader2 class="w-10 h-10 text-slate-900 animate-spin" />
      </div>

      <div v-else class="space-y-6">
        <div v-for="req in requests" :key="req.id" class="bg-white border border-slate-200 rounded-[2rem] p-8 hover:border-slate-400 transition-all group">
          <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div class="flex items-start gap-6">
              <div class="w-14 h-14 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400 group-hover:bg-slate-900 group-hover:text-white transition-all">
                <FileText class="w-7 h-7" />
              </div>
              <div>
                <div class="flex items-center gap-3 mb-2">
                  <span class="text-lg font-bold text-slate-900">{{ req.type === 1 ? '事假' : '病假' }}申请</span>
                  <span 
                    :class="['px-3 py-1 rounded-lg text-[10px] font-black uppercase tracking-widest border flex items-center gap-1.5', getStatusBadge(req.status).class]"
                  >
                    <component :is="getStatusBadge(req.status).icon" class="w-3 h-3" />
                    {{ getStatusBadge(req.status).label }}
                  </span>
                </div>
                <p class="text-sm text-slate-500 font-medium line-clamp-1 mb-3">{{ req.reason }}</p>
                <div class="flex items-center gap-4 text-[10px] font-black text-slate-300 uppercase tracking-widest">
                  <div class="flex items-center gap-1.5"><Calendar class="w-3 h-3" /> {{ new Date(req.startTime).toLocaleString() }} - {{ new Date(req.endTime).toLocaleString() }}</div>
                </div>
              </div>
            </div>
            
            <div v-if="req.status !== 0" class="md:text-right">
              <label class="text-[9px] font-black text-slate-300 uppercase tracking-widest block mb-1">审批意见</label>
              <p class="text-xs font-bold text-slate-600">{{ req.status === 1 ? '准予请假' : '理由不充分，予以驳回' }}</p>
            </div>
          </div>
        </div>

        <div v-if="requests.length === 0" class="py-20 text-center bg-slate-50/50 rounded-[2.5rem] border-2 border-dashed border-slate-200">
          <p class="text-slate-400 font-medium">暂无请假记录</p>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
