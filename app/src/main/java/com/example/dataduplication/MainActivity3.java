package com.example.dataduplication;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public class MainActivity3 extends Activity {

    private static final int REQUEST_STORAGE_PERMISSION = 1;
    private static final int REQUEST_MANAGE_ALL_FILES = 2;
    private WebView webView;
    private TextView statusText;
    private DownloadManager downloadManager;
    private long downloadID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        webView = findViewById(R.id.webView);
        statusText = findViewById(R.id.statusText);
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        if (!checkPermissions()) {
            requestPermissions();
        } else {
            setupWebView();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (!checkPermissions()) {
                    Toast.makeText(MainActivity3.this, "Please allow storage permissions to download files.", Toast.LENGTH_SHORT).show();
                    requestPermissions();
                    return;
                }

                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                File existingFile = searchFileInStorage(fileName);

                if (existingFile != null) {
                    statusText.setText("File already exists at: " + existingFile.getAbsolutePath());
                    Toast.makeText(MainActivity3.this, "File already exists!", Toast.LENGTH_LONG).show();
                } else {
                    startDownload(url, fileName, mimetype);
                }
            }
        });

        webView.loadUrl("https://www.google.com");
    }

    private void startDownload(String url, String fileName, String mimetype) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setMimeType(mimetype);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationUri(Uri.fromFile(file));

        downloadID = downloadManager.enqueue(request);
        statusText.setText("Downloading: " + fileName + "\nSaved at: " + file.getAbsolutePath());
        Toast.makeText(this, "Download Started", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadID);
                Cursor cursor = downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                        runOnUiThread(() -> {
                            statusText.setText("Download Completed: " + file.getAbsolutePath());
                            Toast.makeText(MainActivity3.this, "Download Completed", Toast.LENGTH_LONG).show();
                        });
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                        runOnUiThread(() -> {
                            statusText.setText("Download Failed");
                            Toast.makeText(MainActivity3.this, "Download Failed", Toast.LENGTH_LONG).show();
                        });
                    }
                }
                cursor.close();  // Cursor Leak से बचने के लिए इसे क्लोज करें
            }
        }).start();
    }

    private File searchFileInStorage(String fileName) {
        File root = Environment.getExternalStorageDirectory(); // मुख्य स्टोरेज लोकेशन
        return searchFileRecursively(root, fileName);
    }

    private File searchFileRecursively(File directory, String fileName) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File found = searchFileRecursively(file, fileName);
                    if (found != null) return found;
                } else if (file.getName().equalsIgnoreCase(fileName)) {
                    return file;
                }
            }
        }
        return null;
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.parse("package:" + getPackageName());
            intent.setData(uri);
            startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupWebView();
            } else {
                Toast.makeText(this, "Storage permissions are required!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_ALL_FILES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                setupWebView();
            } else {
                Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
            }
        }
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