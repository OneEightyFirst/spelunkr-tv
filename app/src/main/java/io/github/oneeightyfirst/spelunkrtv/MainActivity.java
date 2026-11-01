package io.github.oneeightyfirst.spelunkrtv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * The whole app: a full-screen WebView that shows https://spelunkr.tv/ and
 * passes TV remote buttons through to it. It also adds assets/tv.js to the page,
 * which only moves keyboard focus with the arrow keys where the site doesn't. Updater.java
 * checks this project's GitHub Pages site for a newer version on launch.
 *
 * The page can ask the app for exactly one thing: SpelunkrApp.leave(), which closes
 * the app (the site's own "Leave Spelunkr?" button calls it). Nothing else is exposed.
 *
 * What it does NOT do: it reads no files, stores nothing of its own, collects
 * nothing, and refuses to navigate to any site other than spelunkr.tv.
 */
public class MainActivity extends Activity {

    // "?tv" turns on the site's own TV layout: full-screen player, remote-friendly guide.
    private static final String HOME_URL = "https://spelunkr.tv/?tv";
    private static final String ALLOWED_HOST = "spelunkr.tv";
    private static final long EXIT_WINDOW_MS = 2000;

    private WebView webView;
    private FrameLayout root;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean offline;
    private long lastBackAt;
    private String tvScript;
    private Updater updater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // It's a TV channel: don't let the screensaver kick in while it's open.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);  // debug builds only

        root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);                    // the site is a JavaScript app
        s.setDomStorageEnabled(true);                    // the site remembers your settings in localStorage
        s.setMediaPlaybackRequiresUserGesture(false);    // let the channel start playing on its own
        s.setAllowFileAccess(false);                     // the page can't read files on the device
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSupportMultipleWindows(false);              // pop-ups open in place (and are domain-checked)
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Tell the site honestly what's visiting it.
        s.setUserAgentString(s.getUserAgentString() + " SpelunkrTV-Android/" + BuildConfig.VERSION_NAME
                + " (+https://github.com/OneEightyFirst/spelunkr-tv)");

        tvScript = readAsset("tv.js");
        webView.setWebViewClient(new Client());
        webView.setWebChromeClient(new Chrome());
        webView.addJavascriptInterface(new SpelunkrApp(), "SpelunkrApp");
        webView.setBackgroundColor(0xFF1C0A57);

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL);
        }
        webView.requestFocus();

        updater = new Updater(this);
        if (savedInstanceState == null) updater.checkInBackground();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Updater.ACTION_INSTALL_STATUS.equals(intent.getAction())) Updater.onInstallStatus(this, intent);
    }

    // ---------------------------------------------------------------- remote

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;

        if (code == KeyEvent.KEYCODE_BACK) {
            if (down) onBackKey();
            return true;
        }
        if (offline && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            if (down) reload();
            return true;
        }

        // Media buttons are sent to the page under the names the site already listens for.
        String key = mediaKeyName(code);
        if (key != null) {
            if (down) sendKeyToPage(key, null);
            return true;
        }

        // D-pad arrows and the OK button go straight to the WebView, which turns them
        // into ordinary ArrowUp/ArrowDown/ArrowLeft/ArrowRight/Enter key presses.
        return super.dispatchKeyEvent(event);
    }

    private static String mediaKeyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "MediaPlayPause";
            case KeyEvent.KEYCODE_MEDIA_PLAY:       return "MediaPlay";
            case KeyEvent.KEYCODE_MEDIA_PAUSE:      return "MediaPause";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "MediaFastForward";
            case KeyEvent.KEYCODE_MEDIA_REWIND:     return "MediaRewind";
            case KeyEvent.KEYCODE_CHANNEL_UP:       return "ChannelUp";
            case KeyEvent.KEYCODE_CHANNEL_DOWN:     return "ChannelDown";
            default: return null;
        }
    }

    /**
     * Back: leave video fullscreen if we're in it; otherwise let the site handle it
     * (close a menu, go back a screen). If the site has nothing to go back to, step
     * back through history, and finally exit on a second press.
     */
    private void onBackKey() {
        if (fullscreenView != null) {
            webView.evaluateJavascript("document.exitFullscreen && document.exitFullscreen()", null);
            return;
        }
        if (offline) {
            confirmExit();
            return;
        }
        sendKeyToPage("Escape", handled -> {
            if (handled) return;
            if (webView.canGoBack()) webView.goBack();
            else confirmExit();
        });
    }

    private void confirmExit() {
        long now = System.currentTimeMillis();
        if (now - lastBackAt < EXIT_WINDOW_MS) {
            finish();
        } else {
            lastBackAt = now;
            Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show();
        }
    }

    interface Handled { void result(boolean handled); }

    /** Fires a keydown/keyup on the focused element. Reports whether the page used it. */
    private void sendKeyToPage(String key, Handled then) {
        String js = "(function(k){var t=document.activeElement||document.body,"
                + "o={key:k,code:k,bubbles:true,cancelable:true},"
                + "used=!t.dispatchEvent(new KeyboardEvent('keydown',o));"
                + "t.dispatchEvent(new KeyboardEvent('keyup',o));return used;})('" + key + "')";
        webView.evaluateJavascript(js, then == null ? null : v -> then.result("true".equals(v)));
    }

    // ---------------------------------------------------------------- navigation

    static boolean isAllowed(Uri uri) {
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && host != null
                && (host.equals(ALLOWED_HOST) || host.endsWith("." + ALLOWED_HOST));
    }

    private void reload() {
        offline = false;
        webView.loadUrl(HOME_URL);
    }

    private void showOffline() {
        offline = true;
        String html = "<html><body style=\"margin:0;height:100vh;display:flex;align-items:center;"
                + "justify-content:center;background:#1C0A57;color:#fff;font:28px sans-serif;"
                + "text-align:center;padding:0 10%\">" + getString(R.string.offline) + "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private String readAsset(String name) {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class Client extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            if (!offline && isAllowed(Uri.parse(url))) view.evaluateJavascript(tvScript, null);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return block(request.getUrl());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return block(Uri.parse(url));
        }

        /** Only spelunkr.tv pages may load in this app. Anything else is refused. */
        private boolean block(Uri uri) {
            if (isAllowed(uri)) return false;
            Toast.makeText(MainActivity.this, "Only spelunkr.tv can be opened here", Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showOffline();
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            // Android 5.1 (older Fire TVs) only calls this version, and only for the main page.
            if (android.os.Build.VERSION.SDK_INT < 23) showOffline();
        }
    }

    /** The site's "Leave Spelunkr?" → LEAVE calls window.SpelunkrApp.leave(). That's all this does. */
    private class SpelunkrApp {
        @android.webkit.JavascriptInterface
        public void leave() {
            runOnUiThread(MainActivity.this::finish);
        }
    }

    /** Lets the site's own fullscreen button work. */
    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
            fullscreenView = view;
            fullscreenCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            webView.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            if (fullscreenView == null) return;
            root.removeView(fullscreenView);
            fullscreenView = null;
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
            webView.setVisibility(View.VISIBLE);
            webView.requestFocus();
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        // Stop playback when you leave the app (Home button, another app, etc.).
        webView.onPause();
        webView.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.resumeTimers();
        webView.onResume();
        updater.resume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
