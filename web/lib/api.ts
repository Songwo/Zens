import axios from 'axios';
import toast from 'react-hot-toast';

// 创建 axios 实例
const api = axios.create({
    baseURL: '/api', // 通过 Next.js 代理转发
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 请求拦截器
api.interceptors.request.use(
    (config) => {
        // 从 localStorage 获取 token
        if (typeof window !== 'undefined') {
            const token = localStorage.getItem('access_token');
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 响应拦截器
api.interceptors.response.use(
    (response) => {
        // 这里可以根据后端统一的 Result 结构进行解包，例如 response.data.data
        // 假设后端返回 { code: 200, message: "ok", data:T }
        return response.data;
    },
    (error) => {
        if (error.response) {
            const { status, data } = error.response;

            // 401 未授权，跳转登录
            if (status === 401) {
                // 避免重复跳转
                if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/auth')) {
                    toast.error('登录已过期，请重新登录');
                    // 清除 token
                    localStorage.removeItem('access_token');
                    localStorage.removeItem('refresh_token');
                    window.location.href = '/auth/login';
                }
            } else {
                // 其他错误提示
                toast.error(data.message || '请求失败，请稍后重试');
            }
        } else {
            toast.error('网络连接异常');
        }
        return Promise.reject(error);
    }
);

export default api;
