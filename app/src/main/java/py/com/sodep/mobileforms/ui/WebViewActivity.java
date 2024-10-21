package py.com.sodep.mobileforms.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.settings.AppSettings;

public class WebViewActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;
    private boolean isNavigatingBack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (!isNavigatingBack) {
                    progressBar.setVisibility(View.VISIBLE);
                    webView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                isNavigatingBack = false;
            }
        });

        webView.loadUrl(AppSettings.DEFAULT_MAP_URI);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            isNavigatingBack = true;
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
