package com.quan.studynotes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * 远程壳：一个全屏 WebView 加载线上页面。
 * 网页本身是完整的 HTML+CSS+JS，这里的原生代码只负责四件事：
 *   1. 提供浏览器内核（WebView）
 *   2. 处理网页里的「拍照 / 选图」（onShowFileChooser）
 *   3. 处理返回键（先在网页内回退，退无可退才退出 App）
 *   4. 断网时给一个能重试的提示页
 */
public class MainActivity extends Activity {

    private static final String START_URL =
            "https://study-notes-78895.app.workbuddy.host/?sharecode=6FVT4AIQ2N&app=1";

    private static final int REQ_FILE = 1001;

    private WebView web;
    private FrameLayout root;
    private ValueCallback<Uri[]> filePathCallback;
    private AlertDialog exitDlg;

    /**
     * 返回键的优先级：从上到下依次尝试，全都不成立才交给「退出确认」。
     * 顺序就是各层弹窗的 z-index，从高到低。
     */
    private static final String BACK_JS =
            "(function(){" +
            "var dg=document.getElementById('dlg');" +
            "if(dg&&dg.classList.contains('on')){document.getElementById('dlg-no').click();return '1';}" +
            "var nd=document.getElementById('nd');" +
            "if(nd&&nd.classList.contains('on')){document.getElementById('nd-x').click();return '1';}" +
            "var lb=document.getElementById('lb');" +
            "if(lb&&lb.classList.contains('on')){lb.click();return '1';}" +
            "var pk=document.getElementById('pick-sheet');" +
            "if(pk&&pk.classList.contains('on')){document.getElementById('pick-mask').click();return '1';}" +
            "var sh=document.getElementById('inp-sheet');" +
            "if(sh&&sh.classList.contains('on')){document.getElementById('inp-mask').click();return '1';}" +
            "var au=document.getElementById('auth');" +
            "if(au&&au.classList.contains('on')){document.getElementById('a-skip').click();return '1';}" +
            "var rv=document.getElementById('rv');" +
            "if(rv&&rv.classList.contains('on')){document.getElementById('rv-close').click();return '1';}" +
            "var bd=document.getElementById('board');" +
            "if(bd&&bd.classList.contains('on')){document.getElementById('bd-cancel').click();return '1';}" +
            "var ed=document.getElementById('ed');" +
            "if(ed&&ed.classList.contains('on')){document.getElementById('ed-cancel').click();return '1';}" +
            "var sd=document.getElementById('s-day');" +
            "if(sd&&sd.classList.contains('active')){document.getElementById('back-from-day').click();return '1';}" +
            "var b=document.querySelector('.screen.active .back[data-back]');" +
            "if(b){b.click();return '1';}" +
            "return '0';" +
            "})()";

    /**
     * 告诉网页「你在 App 里」：去掉模拟状态栏（真机已经有系统状态栏）、
     * 去掉手机外壳描边、隐藏「添加到主屏幕」的安装提示。
     * 网页自身也支持 ?app=1，这两条路都能走通，互相兜底。
     */
    private static final String APP_MODE_JS =
            "(function(){" +
            "if(!document.body)return;" +
            "document.body.classList.add('in-app');" +
            "if(document.getElementById('in-app-style'))return;" +
            "var s=document.createElement('style');s.id='in-app-style';" +
            "s.textContent=" +
            "'body.in-app .phone{width:100vw;height:100vh;border-radius:0;box-shadow:none;}'" +
            "+'body.in-app .island,body.in-app .home-bar,body.in-app .statusbar>span{display:none;}'" +
            "+'body.in-app .statusbar{height:8px;padding:0;}'" +
            "+'body.in-app .dock{bottom:18px;}'" +
            "+'body.in-app .scroll{padding-bottom:118px;}'" +
            "+'body.in-app .ed,body.in-app .board{padding-top:8px;}'" +
            "+'body.in-app .install-tip{display:none;}'" +
            "+'body.in-app .sheet{padding-bottom:26px;}';" +
            "document.head.appendChild(s);" +
            "})()";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F7F5F0"));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        /* 网页切换深色时反过来通知外壳，把状态栏 / 导航栏一起改掉 */
        web.addJavascriptInterface(new ShellBridge(), "AndroidShell");

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);   /* 双指缩放要保留，但别显示丑丑的 +/- 按钮 */
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportMultipleWindows(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " StudyNotesApp/1.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setBackgroundColor(Color.parseColor("#F7F5F0"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadUrl("file:///android_asset/offline.html");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(APP_MODE_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < 23) {
                    view.loadUrl("file:///android_asset/offline.html");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        if (savedInstanceState == null) {
            web.loadUrl(START_URL);
        } else {
            web.restoreState(savedInstanceState);
        }

        /* 先按系统深浅色铺个底，网页加载完会再校准一次 */
        boolean sysDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        applySystemBars(sysDark);
    }

    /** 给网页调用的桥：只做一件事——切换深浅色时同步系统栏 */
    private class ShellBridge {
        @JavascriptInterface
        public void setDark(final boolean dark) {
            runOnUiThread(() -> {
                if (!isFinishing()) applySystemBars(dark);
            });
        }
    }

    private void applySystemBars(boolean dark) {
        int bar = Color.parseColor(dark ? "#17150F" : "#F7F5F0");
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(bar);
            w.setNavigationBarColor(bar);
        }
        if (root != null) root.setBackgroundColor(bar);
        if (web != null) web.setBackgroundColor(bar);

        if (Build.VERSION.SDK_INT >= 23) {
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    /** 站内链接自己处理；断网页的重试；站外的（mailto、tel 等）交给系统 */
    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("app://retry")) {
            web.loadUrl(START_URL);
            return true;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            confirmExit();
            return;
        }
        if (web.getUrl() != null && web.getUrl().startsWith("file://")) {
            web.loadUrl(START_URL);
            return;
        }
        web.evaluateJavascript(BACK_JS, value -> {
            boolean handled = value != null && value.contains("1");
            if (!handled) confirmExit();
        });
    }

    /**
     * 退出确认弹窗。
     * 手机上最常见的两种防误触做法：
     *   1. 弹出对话框问一句（就是这里用的，Android 官方叫 Exit Confirmation）
     *   2. 「再按一次返回退出」——第一次只弹个 toast，两秒内再按才真的退
     * 这里选 1，因为对话框能写清楚「笔记已经存好」，不会让人以为丢了东西。
     */
    private void confirmExit() {
        if (exitDlg != null && exitDlg.isShowing()) return;
        exitDlg = new AlertDialog.Builder(this)
                .setTitle("要退出学习笔记吗？")
                .setMessage("笔记都已经存好了，下次打开接着写。")
                .setNegativeButton("再写会儿", (d, which) -> d.dismiss())
                .setPositiveButton("退出", (d, which) -> finish())
                .create();
        exitDlg.show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
