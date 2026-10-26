/* 学习笔记 Service Worker
 * 注意：本站在分享门槛后面，除页面外所有路径都必须带 sharecode 才能访问，
 * 所以这里不做安装期预缓存，改为浏览时按需缓存。
 * 策略：
 *  - 页面导航：网络优先，成功即写入缓存，断网回落缓存
 *  - 其余同源静态资源：缓存优先
 *  - 云端 API / 认证 / 对象存储 / CDN：直接放行，绝不缓存
 */
const CACHE = 'study-notes-v2';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  if (url.origin !== location.origin) return;          /* CDN / SDK */
  if (url.pathname.includes('/.cloud/')) return;        /* 云端数据面 */
  if (url.pathname.startsWith('/api/')) return;

  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put('./index.html', copy)).catch(() => {});
          }
          return res;
        })
        .catch(() => caches.match('./index.html').then((hit) => hit || caches.match(req)))
    );
    return;
  }

  e.respondWith(
    caches.match(req).then((hit) => {
      if (hit) return hit;
      return fetch(req).then((res) => {
        if (res && res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => {});
        }
        return res;
      });
    })
  );
});
