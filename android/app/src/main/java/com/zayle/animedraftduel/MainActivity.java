package com.zayle.animedraftduel;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

/** Affiche le jeu (assets/index.html) servi depuis une vraie origine https locale,
 *  pour que le stockage des images (IndexedDB) et les sauvegardes fonctionnent. */
public class MainActivity extends AppCompatActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    private ValueCallback<Uri[]> pendingFiles;
    private ActivityResultLauncher<Intent> filePicker;
    private ActivityResultLauncher<Intent> saveLauncher;
    private File saveTmp;
    private FileOutputStream saveOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0D0A14);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // les liens externes (recherche d'images) s'ouvrent dans le navigateur
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException e) { /* rien */ }
                return true;
            }
        });

        filePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (pendingFiles == null) return;
            Uri[] uris = null;
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    uris = new Uri[]{ data.getData() };
                }
            }
            pendingFiles.onReceiveValue(uris);
            pendingFiles = null;
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                String[] acc = params.getAcceptTypes();
                boolean images = acc != null && acc.length > 0 && acc[0] != null && acc[0].startsWith("image");
                i.setType(images ? "image/*" : "*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { filePicker.launch(Intent.createChooser(i, images ? "Choisir des images" : "Choisir une sauvegarde")); }
                catch (ActivityNotFoundException e) { pendingFiles = null; return false; }
                return true;
            }
        });

        // Sauvegarde des images : le jeu envoie le fichier par morceaux, puis l'utilisateur choisit où l'enregistrer
        saveLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Uri dest = result.getData() != null ? result.getData().getData() : null;
            boolean ok = false;
            if (result.getResultCode() == RESULT_OK && dest != null && saveTmp != null) {
                try (InputStream in = new FileInputStream(saveTmp); OutputStream out = getContentResolver().openOutputStream(dest)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    ok = true;
                } catch (Exception e) { ok = false; }
            }
            if (saveTmp != null) saveTmp.delete();
            saveTmp = null;
            Toast.makeText(this, ok ? "Sauvegarde enregistrée" : "Sauvegarde annulée", Toast.LENGTH_SHORT).show();
        });
        web.addJavascriptInterface(new SaveBridge(), "AndroidSave");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack(); else finish();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    private class SaveBridge {
        private String name = "anime-draft-duel-images.addpack";
        @JavascriptInterface public boolean begin(String fileName) {
            try {
                if (saveOut != null) saveOut.close();
                name = fileName;
                saveTmp = new File(getCacheDir(), "export.addpack");
                saveOut = new FileOutputStream(saveTmp);
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public void append(String b64) {
            try { if (saveOut != null) saveOut.write(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception e) { /* ignoré */ }
        }
        @JavascriptInterface public void finish() {
            try { if (saveOut != null) saveOut.close(); } catch (Exception e) { /* ignoré */ }
            saveOut = null;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/octet-stream");
                i.putExtra(Intent.EXTRA_TITLE, name);
                saveLauncher.launch(i);
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
}
