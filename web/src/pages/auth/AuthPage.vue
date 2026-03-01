<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import LoginWizard from '@/components/auth/LoginWizard.vue'
import RegisterWizard from '@/components/auth/RegisterWizard.vue'

const route = useRoute()
const router = useRouter()

const isLogin = ref(true)

const setMode = (type: string) => {
  isLogin.value = type !== 'register'
}

onMounted(() => {
  setMode(route.query.type as string)
})

watch(() => route.query.type, (newType) => {
  setMode(newType as string)
})

const switchToRegister = () => {
  router.replace({ path: '/auth', query: { ...route.query, type: 'register' } })
}

const switchToLogin = () => {
  router.replace({ path: '/auth', query: { ...route.query, type: 'login' } })
}
</script>

<template>
  <AuthLayout>
    <transition name="el-fade-in" mode="out-in">
      <LoginWizard v-if="isLogin" key="login" @switch-to-register="switchToRegister" />
      <RegisterWizard v-else key="register" @switch-to-login="switchToLogin" />
    </transition>
  </AuthLayout>
</template>
