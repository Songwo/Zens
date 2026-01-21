import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(),
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition
        } else {
            return { top: 0 }
        }
    },
    routes: [
        {
            path: '/',
            name: 'home',
            component: () => import('@/views/HomeView.vue'),
        },
        {
            path: '/trends',
            name: 'trends',
            component: () => import('@/views/TrendsView.vue'),
        },
        {
            path: '/post/:id',
            name: 'post-detail',
            component: () => import('@/views/PostDetailView.vue'),
        },
        {
            path: '/auth/login',
            name: 'login',
            component: () => import('@/views/LoginView.vue'),
        },
        {
            path: '/auth/register',
            name: 'register',
            component: () => import('@/views/RegisterView.vue'),
        },
        {
            path: '/profile',
            name: 'profile',
            component: () => import('@/views/ProfileView.vue'),
        },
        {
            path: '/settings',
            name: 'settings',
            component: () => import('@/views/SettingsView.vue'),
        },
        {
            path: '/my/likes',
            name: 'my-likes',
            component: () => import('@/views/MyLikesView.vue'),
        },
        {
            path: '/my/collections',
            name: 'my-collections',
            component: () => import('@/views/MyCollectionsView.vue'),
        },
        {
            path: '/student/profile',
            name: 'student-profile',
            component: () => import('@/views/StudentProfileView.vue'),
        },
        {
            path: '/academic',
            name: 'academic',
            component: () => import('@/views/AcademicView.vue'),
        },
        {
            path: '/course-selection',
            name: 'course-selection',
            component: () => import('@/views/CourseSelectionView.vue'),
        },
        {
            path: '/leave',
            name: 'leave',
            component: () => import('@/views/LeaveManagementView.vue'),
        }
    ],
})

export default router
