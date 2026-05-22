const page = window.MEDIA_PANEL_PAGE;
const basePath = window.MEDIA_PANEL_BASE || '/panel';

const jsonFetch = async (url, options = {}) => {
  const response = await fetch(url, {
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });
  const payload = await response.json();
  if (payload.code !== 0) {
    throw new Error(payload.message || 'request failed');
  }
  return payload.data;
};

const badgeClass = (status) => {
  switch (status) {
    case 'success': return 'badge badge-success';
    case 'failed': return 'badge badge-failed';
    case 'uploading':
    case 'merging': return 'badge badge-uploading';
    case 'canceled': return 'badge badge-canceled';
    default: return 'badge';
  }
};

const formatBytes = (value = 0) => {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = Number(value);
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size.toFixed(size >= 10 || unit === 0 ? 0 : 2)} ${units[unit]}`;
};

const formatPercent = (value = 0) => `${(Number(value) * 100).toFixed(2)}%`;
const formatDate = (value) => value ? new Date(value).toLocaleString() : '-';

const renderTable = (columns, items) => {
  const head = columns.map((item) => `<th>${item.title}</th>`).join('');
  const rows = items.map((row) => `<tr>${columns.map((column) => `<td>${column.render(row)}</td>`).join('')}</tr>`).join('');
  return `<table><thead><tr>${head}</tr></thead><tbody>${rows || '<tr><td colspan="' + columns.length + '">暂无数据</td></tr>'}</tbody></table>`;
};

const loadDashboard = async () => {
  const data = await jsonFetch('/api/admin/dashboard');
  const cards = [
    ['当前上传任务', data.currentUploadingTasks],
    ['图片数量', data.imageCount],
    ['视频数量', data.videoCount],
    ['总文件大小', formatBytes(data.fileTotalSizeBytes)],
    ['QPS', Number(data.qps || 0).toFixed(2)],
    ['平均响应(ms)', Number(data.averageResponseMs || 0).toFixed(2)],
    ['错误率', formatPercent(data.errorRate || 0)],
    ['活跃连接数', data.activeConnections]
  ];
  document.getElementById('dashboard-cards').innerHTML = cards.map(([title, value]) => `
    <article class="metric-card">
      <h3>${title}</h3>
      <strong>${value}</strong>
    </article>
  `).join('');

  const recent = document.getElementById('recent-uploads');
  recent.innerHTML = renderTable([
    { title: '任务ID', render: (row) => row.id },
    { title: '类型', render: (row) => row.mediaType },
    { title: '状态', render: (row) => `<span class="${badgeClass(row.status)}">${row.status}</span>` },
    { title: '原始文件名', render: (row) => row.originalName },
    { title: '上传大小', render: (row) => formatBytes(row.uploadedSizeBytes) },
    { title: '时间', render: (row) => formatDate(row.createdAt) }
  ], data.recentUploads || []);
};

const loadUploads = async () => {
  const keyword = document.getElementById('uploads-keyword').value;
  const status = document.getElementById('uploads-status').value;
  const data = await jsonFetch(`/api/admin/uploads?page=1&pageSize=50&keyword=${encodeURIComponent(keyword)}&status=${encodeURIComponent(status)}`);
  document.getElementById('uploads-table').innerHTML = renderTable([
    { title: '任务ID', render: (row) => row.id },
    { title: '媒体类型', render: (row) => row.mediaType },
    { title: '状态', render: (row) => `<span class="${badgeClass(row.status)}">${row.status}</span>` },
    { title: '文件名', render: (row) => row.originalName },
    { title: '总大小', render: (row) => formatBytes(row.totalSizeBytes) },
    { title: '已上传', render: (row) => formatBytes(row.uploadedSizeBytes) },
    { title: '分片', render: (row) => `${row.uploadedChunks}/${row.totalChunks}` },
    { title: '上传人', render: (row) => row.uploaderId || '-' },
    { title: '时间', render: (row) => formatDate(row.createdAt) }
  ], data.items || []);
};

const loadFiles = async () => {
  const keyword = document.getElementById('files-keyword').value;
  const mediaType = document.getElementById('files-media-type').value;
  const data = await jsonFetch(`/api/admin/files?page=1&pageSize=50&keyword=${encodeURIComponent(keyword)}&mediaType=${encodeURIComponent(mediaType)}`);
  document.getElementById('files-table').innerHTML = renderTable([
    { title: '文件ID', render: (row) => row.id },
    { title: '类型', render: (row) => row.mediaType },
    { title: '状态', render: (row) => `<span class="${badgeClass(row.status)}">${row.status}</span>` },
    { title: '原始文件名', render: (row) => row.originalName },
    { title: '大小', render: (row) => formatBytes(row.sizeBytes) },
    { title: '上传人', render: (row) => row.uploaderId || '-' },
    { title: '访问', render: (row) => row.accessUrl ? `<a href="${row.accessUrl}" target="_blank">打开</a>` : '-' },
    { title: '时间', render: (row) => formatDate(row.createdAt) }
  ], data.items || []);
};

const loadSystem = async () => {
  const system = await jsonFetch('/api/admin/system');
  const stats = await jsonFetch('/api/admin/stats');
  document.getElementById('system-panel').innerHTML = `
    <div class="cards-grid">
      <article class="metric-card"><h3>主机名</h3><strong>${system.hostname}</strong></article>
      <article class="metric-card"><h3>运行时长</h3><strong>${Math.floor(system.uptimeSeconds / 60)} 分钟</strong></article>
      <article class="metric-card"><h3>系统 CPU</h3><strong>${Number(system.systemCpuPercent || 0).toFixed(2)}%</strong></article>
      <article class="metric-card"><h3>进程内存</h3><strong>${formatBytes(system.processMemoryBytes)}</strong></article>
      <article class="metric-card"><h3>系统内存使用</h3><strong>${Number(system.systemMemoryPercent || 0).toFixed(2)}%</strong></article>
      <article class="metric-card"><h3>协程数</h3><strong>${system.goroutines}</strong></article>
      <article class="metric-card"><h3>上传成功</h3><strong>${stats.uploadSuccessCount}</strong></article>
      <article class="metric-card"><h3>上传失败</h3><strong>${stats.uploadFailureCount}</strong></article>
    </div>
    <div class="json-box">${JSON.stringify({ system, stats }, null, 2)}</div>
  `;
};

const loadConfig = async () => {
  const data = await jsonFetch('/api/admin/config');
  document.getElementById('config-view').innerHTML = `<div class="json-box">${JSON.stringify(data, null, 2)}</div>`;
};

const submitConfig = async (event) => {
  event.preventDefault();
  const formData = new FormData(event.currentTarget);
  const payload = {};
  for (const [key, value] of formData.entries()) {
    if (String(value).trim() !== '') {
      payload[key] = String(value).trim();
    }
  }
  await jsonFetch('/api/admin/config', {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
  await loadConfig();
  alert('运行配置已更新');
};

document.addEventListener('DOMContentLoaded', () => {
  if (page === 'dashboard') {
    loadDashboard().catch((err) => alert(err.message));
  }
  if (page === 'uploads') {
    document.getElementById('uploads-refresh').addEventListener('click', () => loadUploads().catch((err) => alert(err.message)));
    loadUploads().catch((err) => alert(err.message));
  }
  if (page === 'files') {
    document.getElementById('files-refresh').addEventListener('click', () => loadFiles().catch((err) => alert(err.message)));
    loadFiles().catch((err) => alert(err.message));
  }
  if (page === 'system') {
    loadSystem().catch((err) => alert(err.message));
  }
  if (page === 'config') {
    document.getElementById('config-form').addEventListener('submit', submitConfig);
    loadConfig().catch((err) => alert(err.message));
  }
});
