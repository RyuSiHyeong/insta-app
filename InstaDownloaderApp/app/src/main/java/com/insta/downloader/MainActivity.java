package com.insta.downloader;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-F966N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidDownloader");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectDownloaderScript();
            }
        });

        webView.loadUrl("https://www.instagram.com/");
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void downloadMedia(String url, String account, String filename) {
            runOnUiThread(() -> {
                try {
                    String safeAccount = (account == null || account.isEmpty() ? "unknown" : account).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                    String safeFile = (filename == null || filename.isEmpty() ? "media.jpg" : filename).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                    String subPath = "인스타/" + safeAccount + "/" + safeFile;

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) {
                        request.addRequestHeader("Cookie", cookie);
                    }
                    request.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());
                    request.setTitle(safeFile);
                    request.setDescription("인스타/" + safeAccount + " 폴더에 저장 중...");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subPath);

                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(MainActivity.this, "다운로드: 인스타/" + safeAccount + "/" + safeFile, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "다운로드 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void injectDownloaderScript() {
        String js = "(function() {" +
            "if (window.__igdMobileInjected) return;" +
            "window.__igdMobileInjected = true;" +

            "const style = document.createElement('style');" +
            "style.textContent = `" +
            "  .igd-post-btn-group { display:inline-flex; align-items:center; gap:10px; margin-left:10px; margin-right:auto; }" +
            "  .igd-icon-btn { background:transparent; border:none; padding:4px; color:inherit; display:inline-flex; align-items:center; }" +
            "  .igd-grid-hover-btn { position:absolute; bottom:6px; right:6px; z-index:40; padding:4px 8px; background:rgba(0,149,246,0.9); color:#fff; border:none; border-radius:6px; font-size:11px; font-weight:700; }" +
            "  .igd-profile-dl-btn { margin:8px 0; padding:6px 12px; background:#0095f6; color:#fff; border:none; border-radius:8px; font-size:12px; font-weight:600; }" +
            "  .igd-story-dl-btn { position:fixed; bottom:75px; right:16px; z-index:99999; padding:8px 14px; background:rgba(0,149,246,0.95); color:#fff; border:none; border-radius:20px; font-size:13px; font-weight:700; box-shadow:0 2px 8px rgba(0,0,0,0.5); }" +
            "`;" +
            "document.head.appendChild(style);" +

            "const mediaCache = new Map();" +
            "const userStoryCache = new Map();" +
            "function getCsrf() { const m = document.cookie.match(/csrftoken=([^;]+)/); return m ? m[1] : ''; }" +
            "function getHeaders() { return { 'X-IG-App-ID': '936619743392459', 'X-ASBD-ID': '129477', 'X-CSRFToken': getCsrf(), 'X-Requested-With': 'XMLHttpRequest' }; }" +

            "function shortcodeToId(sc) {" +
            "  const alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';" +
            "  let id = BigInt(0);" +
            "  for (let i = 0; i < sc.length; i++) {" +
            "    const idx = alpha.indexOf(sc[i]);" +
            "    if (idx !== -1) id = (id * BigInt(64)) + BigInt(idx);" +
            "  }" +
            "  return id.toString();" +
            "}" +

            "function getBestUrl(item) {" +
            "  if (item.video_versions && item.video_versions.length > 0) return { url: item.video_versions[0].url, type: 'mp4' };" +
            "  if (item.video_resources && item.video_resources.length > 0) return { url: item.video_resources[0].src, type: 'mp4' };" +
            "  if (item.video_url) return { url: item.video_url, type: 'mp4' };" +
            "  if (item.image_versions2 && item.image_versions2.candidates && item.image_versions2.candidates.length > 0) return { url: item.image_versions2.candidates[0].url, type: 'jpg' };" +
            "  if (item.display_resources && item.display_resources.length > 0) return { url: item.display_resources[item.display_resources.length - 1].src, type: 'jpg' };" +
            "  if (item.display_url) return { url: item.display_url, type: 'jpg' };" +
            "  return null;" +
            "}" +

            "function parseItem(item, owner) {" +
            "  const res = [];" +
            "  const user = item.user?.username || item.owner?.username || owner || 'insta_user';" +
            "  const pk = String(item.pk || item.id || '').split('_')[0];" +
            "  if (item.carousel_media) {" +
            "    item.carousel_media.forEach((s, i) => { const m = getBestUrl(s); if (m) res.push({ ...m, index: i + 1, username: user, pk: pk }); });" +
            "  } else if (item.edge_sidecar_to_children?.edges) {" +
            "    item.edge_sidecar_to_children.edges.forEach((e, i) => { const m = getBestUrl(e.node || e); if (m) res.push({ ...m, index: i + 1, username: user, pk: pk }); });" +
            "  } else {" +
            "    const m = getBestUrl(item); if (m) res.push({ ...m, index: 1, username: user, pk: pk });" +
            "  }" +
            "  return res;" +
            "}" +

            "function extractObj(obj, owner) {" +
            "  if (!obj || typeof obj !== 'object') return;" +
            "  const curOwner = obj.user?.username || obj.owner?.username || owner;" +
            "  if ((obj.id || obj.pk || obj.shortcode || obj.code) && (obj.image_versions2 || obj.video_versions || obj.video_resources || obj.carousel_media || obj.edge_sidecar_to_children || obj.display_url)) {" +
            "    const code = obj.code || obj.shortcode;" +
            "    const pk = String(obj.pk || obj.id || '').split('_')[0];" +
            "    const parsed = parseItem(obj, curOwner);" +
            "    if (parsed.length > 0) {" +
            "      if (code) mediaCache.set(String(code), parsed);" +
            "      if (pk) mediaCache.set(String(pk), parsed);" +
            "      if (curOwner && pk) {" +
            "        if (!userStoryCache.has(curOwner)) userStoryCache.set(curOwner, []);" +
            "        const arr = userStoryCache.get(curOwner);" +
            "        if (!arr.some(x => x.pk === pk)) arr.push(parsed[0]);" +
            "      }" +
            "    }" +
            "  }" +
            "  for (const k in obj) { if (Object.prototype.hasOwnProperty.call(obj, k) && typeof obj[k] === 'object') extractObj(obj[k], curOwner); }" +
            "}" +

            // 1. 초기 렌더링된 JSON 스크립트 태그 파싱
            "function scanPageScripts() {" +
            "  document.querySelectorAll('script[type=\"application/json\"]').forEach(s => {" +
            "    if (s.dataset.igdScanned) return;" +
            "    s.dataset.igdScanned = '1';" +
            "    try { extractObj(JSON.parse(s.textContent)); } catch(e){}" +
            "  });" +
            "}" +
            "scanPageScripts();" +

            // 2. fetch 후킹
            "const origFetch = window.fetch;" +
            "window.fetch = async function(...args) {" +
            "  const r = await origFetch.apply(this, args);" +
            "  try { r.clone().json().then(d => extractObj(d)).catch(()=>{}); } catch(e){}" +
            "  return r;" +
            "};" +

            // 3. XMLHttpRequest 후킹 (인스타그램 스토리/GraphQL 요청 가로채기)
            "const origOpen = XMLHttpRequest.prototype.open;" +
            "XMLHttpRequest.prototype.open = function() {" +
            "  this.addEventListener('load', function() {" +
            "    try {" +
            "      if (this.responseText && this.responseText.startsWith('{')) {" +
            "        extractObj(JSON.parse(this.responseText));" +
            "      }" +
            "    } catch(e){}" +
            "  });" +
            "  return origOpen.apply(this, arguments);" +
            "};" +

            "async function fetchByShortcode(sc) {" +
            "  if (mediaCache.has(sc)) return mediaCache.get(sc);" +
            "  try {" +
            "    const mid = shortcodeToId(sc);" +
            "    if (mediaCache.has(mid)) return mediaCache.get(mid);" +
            "    const r = await origFetch('https://www.instagram.com/api/v1/media/' + mid + '/info/', { headers: getHeaders(), credentials: 'include' });" +
            "    if (r.ok) { const j = await r.json(); extractObj(j); return mediaCache.get(sc) || mediaCache.get(mid); }" +
            "  } catch(e){}" +
            "  return null;" +
            "}" +

            // 4. 스토리 전용 API 조회 함수
            "async function fetchStoryMedia(sUser, sPk) {" +
            "  scanPageScripts();" +
            "  if (sPk && mediaCache.has(sPk)) return mediaCache.get(sPk)[0];" +
            "  if (sPk && /^\\d+$/.test(sPk)) {" +
            "    try {" +
            "      const r = await origFetch('https://www.instagram.com/api/v1/media/' + sPk + '/info/', { headers: getHeaders(), credentials: 'include' });" +
            "      if (r.ok) {" +
            "        const j = await r.json(); extractObj(j, sUser);" +
            "        if (mediaCache.has(sPk)) return mediaCache.get(sPk)[0];" +
            "      }" +
            "    } catch(e){}" +
            "  }" +
            "  try {" +
            "    if (sUser === 'highlights' && sPk) {" +
            "      const hr = await origFetch('https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=highlight%3A' + sPk, { headers: getHeaders(), credentials: 'include' });" +
            "      if (hr.ok) { const hj = await hr.json(); extractObj(hj); }" +
            "    } else if (sUser && sUser !== 'story_user') {" +
            "      const pr = await origFetch('https://www.instagram.com/api/v1/users/web_profile_info/?username=' + encodeURIComponent(sUser), { headers: getHeaders(), credentials: 'include' });" +
            "      if (pr.ok) {" +
            "        const pj = await pr.json();" +
            "        const uid = pj?.data?.user?.id;" +
            "        if (uid) {" +
            "          const sr = await origFetch('https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=' + uid, { headers: getHeaders(), credentials: 'include' });" +
            "          if (sr.ok) { const sj = await sr.json(); extractObj(sj, sUser); }" +
            "        }" +
            "      }" +
            "    }" +
            "  } catch(e){}" +
            "  if (sPk && mediaCache.has(sPk)) return mediaCache.get(sPk)[0];" +
            "  const cachedList = userStoryCache.get(sUser);" +
            "  if (cachedList && cachedList.length > 0) {" +
            "    const hasVideo = Boolean(document.querySelector('video'));" +
            "    if (hasVideo) {" +
            "      const vItem = cachedList.find(x => x.type === 'mp4');" +
            "      if (vItem) return vItem;" +
            "    }" +
            "    return cachedList[cachedList.length - 1];" +
            "  }" +
            "  return null;" +
            "}" +

            "function dl(url, user, file) { window.AndroidDownloader.downloadMedia(url, user || 'insta_user', file); }" +

            "function getProfileUser() {" +
            "  const p = window.location.pathname.split('/').filter(Boolean);" +
            "  const rev = ['p','reel','reels','stories','explore','direct','accounts'];" +
            "  return (p.length >= 1 && !rev.includes(p[0])) ? p[0] : null;" +
            "}" +

            "function extractUserFromArticle(art) {" +
            "  const links = art.querySelectorAll('header a[href^=\"/\"], a[role=\"link\"][href^=\"/\"]');" +
            "  const rev = ['p','reel','reels','stories','explore','direct','accounts'];" +
            "  for (const a of links) {" +
            "    const p = (a.getAttribute('href')||'').split('/').filter(Boolean);" +
            "    if (p.length === 1 && !rev.includes(p[0])) return p[0];" +
            "  }" +
            "  return getProfileUser() || 'insta_user';" +
            "}" +

            "function injectUI() {" +
            "  document.querySelectorAll('article').forEach(art => {" +
            "    if (art.dataset.igdMobile) return;" +
            "    const saveSvg = art.querySelector('svg[aria-label*=\"저장\"], svg[aria-label*=\"Save\"]');" +
            "    if (!saveSvg) return;" +
            "    const bar = saveSvg.closest('section') || saveSvg.parentElement?.parentElement?.parentElement?.parentElement;" +
            "    if (!bar) return;" +
            "    art.dataset.igdMobile = '1';" +

            "    const grp = document.createElement('div'); grp.className = 'igd-post-btn-group';" +
            "    const b1 = document.createElement('button'); b1.className = 'igd-icon-btn'; b1.innerHTML = '⬇️현재';" +
            "    const b2 = document.createElement('button'); b2.className = 'igd-icon-btn'; b2.innerHTML = '📦전체';" +

            "    b1.onclick = async (e) => {" +
            "      e.preventDefault(); e.stopPropagation();" +
            "      const link = art.querySelector('a[href*=\"/p/\"], a[href*=\"/reel/\"]');" +
            "      const sc = link ? (link.getAttribute('href').match(/\\/(p|reel)\\/([A-Za-z0-9_-]+)/)||[])[2] : null;" +
            "      const user = extractUserFromArticle(art);" +
            "      if (sc) {" +
            "        const list = await fetchByShortcode(sc);" +
            "        if (list && list.length > 0) { dl(list[0].url, list[0].username || user, sc + '_1.' + list[0].type); return; }" +
            "      }" +
            "      const img = art.querySelector('img[srcset], img');" +
            "      if (img) dl(img.src, user, (sc||'post') + '_current.jpg');" +
            "    };" +

            "    b2.onclick = async (e) => {" +
            "      e.preventDefault(); e.stopPropagation();" +
            "      const link = art.querySelector('a[href*=\"/p/\"], a[href*=\"/reel/\"]');" +
            "      const sc = link ? (link.getAttribute('href').match(/\\/(p|reel)\\/([A-Za-z0-9_-]+)/)||[])[2] : 'post';" +
            "      const user = extractUserFromArticle(art);" +
            "      const list = await fetchByShortcode(sc);" +
            "      if (list && list.length > 0) {" +
            "        list.forEach((m, i) => setTimeout(() => dl(m.url, m.username || user, sc + '_' + m.index + '.' + m.type), i * 300));" +
            "      }" +
            "    };" +

            "    grp.appendChild(b1); grp.appendChild(b2);" +
            "    const bw = saveSvg.closest('div[role=\"button\"]')?.parentElement || saveSvg.parentElement;" +
            "    if (bw && bw.parentElement) bw.parentElement.insertBefore(grp, bw); else bar.appendChild(grp);" +
            "  });" +

            "  document.querySelectorAll('a[href*=\"/p/\"], a[href*=\"/reel/\"]').forEach(a => {" +
            "    if (a.dataset.igdGrid || (a.closest('article') && !a.querySelector('img'))) return;" +
            "    if (!a.querySelector('img')) return;" +
            "    const m = a.getAttribute('href').match(/\\/(p|reel)\\/([A-Za-z0-9_-]+)/);" +
            "    if (!m) return;" +
            "    a.dataset.igdGrid = '1'; a.style.position = 'relative';" +
            "    const gb = document.createElement('button'); gb.className = 'igd-grid-hover-btn'; gb.textContent = '⬇다운';" +
            "    gb.onclick = async (e) => {" +
            "      e.preventDefault(); e.stopPropagation();" +
            "      const sc = m[2]; const pUser = getProfileUser() || 'insta_user';" +
            "      gb.textContent = '...';" +
            "      const list = await fetchByShortcode(sc);" +
            "      if (list && list.length > 0) {" +
            "        list.forEach((item, i) => setTimeout(() => dl(item.url, item.username || pUser, sc + '_' + item.index + '.' + item.type), i * 300));" +
            "        gb.textContent = '완료';" +
            "      } else { gb.textContent = '실패'; }" +
            "      setTimeout(() => gb.textContent = '⬇다운', 2000);" +
            "    };" +
            "    a.appendChild(gb);" +
            "  });" +

            "  const pUser = getProfileUser();" +
            "  const header = document.querySelector('header');" +
            "  if (pUser && header && !header.querySelector('.igd-profile-dl-btn')) {" +
            "    const pb = document.createElement('button'); pb.className = 'igd-profile-dl-btn'; pb.textContent = '계정 전체 게시물 다운';" +
            "    pb.onclick = async () => {" +
            "      if (!confirm('@' + pUser + ' 계정의 전체 게시물을 다운로드할까요?')) return;" +
            "      pb.disabled = true; pb.textContent = '수집 중...';" +
            "      let nextMax = null, more = true;" +
            "      while (more) {" +
            "        const r = await origFetch('https://www.instagram.com/api/v1/feed/user/' + pUser + '/username/?count=12' + (nextMax ? '&max_id=' + encodeURIComponent(nextMax) : ''), { headers: getHeaders(), credentials: 'include' });" +
            "        if (!r.ok) break;" +
            "        const d = await r.json();" +
            "        for (const item of (d.items || [])) {" +
            "          const list = parseItem(item, pUser); const code = item.code || item.pk;" +
            "          list.forEach((m, i) => setTimeout(() => dl(m.url, pUser, code + '_' + m.index + '.' + m.type), i * 250));" +
            "        }" +
            "        more = Boolean(d.more_available && d.next_max_id); nextMax = d.next_max_id;" +
            "        if (more) await new Promise(res => setTimeout(res, 2200));" +
            "      }" +
            "      pb.disabled = false; pb.textContent = '전체 다운 완료';" +
            "    };" +
            "    header.appendChild(pb);" +
            "  }" +

            "  if (window.location.pathname.startsWith('/stories/')) {" +
            "    if (!document.querySelector('.igd-story-dl-btn')) {" +
            "      const sb = document.createElement('button'); sb.className = 'igd-story-dl-btn'; sb.textContent = '⬇️ 스토리 저장';" +
            "      sb.onclick = async (e) => {" +
            "        e.preventDefault(); e.stopPropagation();" +
            "        sb.textContent = '⏳ 확인 중...';" +
            "        const pts = window.location.pathname.split('/').filter(Boolean);" +
            "        const sUser = pts[1] || 'story_user'; const sPk = pts[2];" +
            "        const item = await fetchStoryMedia(sUser, sPk);" +
            "        if (item && item.url) {" +
            "          const targetUser = (sUser === 'highlights' && item.username) ? item.username : sUser;" +
            "          dl(item.url, targetUser, 'story_' + (sPk || item.pk || Date.now()) + '.' + item.type);" +
            "          sb.textContent = '✅ 저장 완료';" +
            "          setTimeout(() => sb.textContent = '⬇️ 스토리 저장', 1500);" +
            "          return;" +
            "        }" +
            "        const v = document.querySelector('video');" +
            "        if (v) {" +
            "          const vSrc = (v.src && !v.src.startsWith('blob:')) ? v.src : (v.querySelector('source')?.src || '');" +
            "          if (vSrc && !vSrc.startsWith('blob:')) {" +
            "            dl(vSrc, sUser, 'story_' + Date.now() + '.mp4');" +
            "            sb.textContent = '⬇️ 스토리 저장';" +
            "            return;" +
            "          }" +
            "        }" +
            "        let bestImg = null, maxArea = 0;" +
            "        document.querySelectorAll('img').forEach(im => {" +
            "          const area = (im.naturalWidth || im.width || 0) * (im.naturalHeight || im.height || 0);" +
            "          if (im.src && area > maxArea && (im.clientWidth > 150)) { maxArea = area; bestImg = im; }" +
            "        });" +
            "        if (bestImg) {" +
            "          dl(bestImg.src, sUser, 'story_' + Date.now() + '.jpg');" +
            "        }" +
            "        sb.textContent = '⬇️ 스토리 저장';" +
            "      };" +
            "      document.body.appendChild(sb);" +
            "    }" +
            "  } else {" +
            "    const sb = document.querySelector('.igd-story-dl-btn'); if (sb) sb.remove();" +
            "  }" +
            "}" +
            "setInterval(injectUI, 1000);" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
