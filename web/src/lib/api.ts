import axios from 'axios';
import { toast } from 'vue-sonner';

const api = axios.create({
    baseURL: '/api',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('access_token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response interceptor
api.interceptors.response.use(
    (response) => {
        return response.data;
    },
    (error) => {
        if (error.response) {
            const { status, data } = error.response;

            if (status === 401 || status === 403) {
                if (!window.location.pathname.startsWith('/auth')) {
                    const msg = status === 401 ? '登录已过期，请重新登录' : '请先登录再进行操作';
                    toast.error(msg);
                    localStorage.removeItem('access_token');
                    localStorage.removeItem('refresh_token');
                    localStorage.removeItem('user_id');
                    window.location.href = '/auth/login';
                }
            } else {
                toast.error(data.message || '请求失败，请稍后重试');
            }
        } else {
            toast.error('网络连接异常');
        }
        return Promise.reject(error);
    }
);

export default api;
