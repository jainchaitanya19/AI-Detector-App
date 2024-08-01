package com.example.aidetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageScanService extends Service {
    private static final String TAG = "ImageScanService";
    private static final String CHANNEL_ID = "foreground_service_channel";
    private ContentObserver imageObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundService();

        imageObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                Log.d(TAG, "New image detected: " + uri.toString());
                new UploadImageTask().execute(uri);
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                imageObserver
        );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(imageObserver);
        // Restart service if destroyed
        Intent intent = new Intent(this, ImageScanService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If the service is killed by the system, restart it
        return START_STICKY;
    }

    private void startForegroundService() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Image Scan Service")
                .setContentText("Scanning for fake images in the background")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private class UploadImageTask extends AsyncTask<Uri, Void, String> {
        private Uri imageUri;

        @Override
        protected String doInBackground(Uri... uris) {
            Log.d(TAG, "Task started");
            Response response = null;
            try {
                imageUri = uris[0];
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open InputStream");
                    return null;
                }

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                inputStream.close(); // Close InputStream here

                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                String encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                OkHttpClient client = new OkHttpClient();

                // Change the URL depending on whether you are using an emulator or physical device
                String baseUrl = "http://10.0.2.2:5000/predict"; // Emulator URL
                // String baseUrl = "http://192.168.1.133:5000/predict"; // Physical device URL

                String prefixedEncodedImage = "data:image/jpg;base64," + encodedImage;
                String json = "{\"image\":\"" + prefixedEncodedImage + "\"}";

                Log.d(TAG, "Request payload: " + json);

                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(baseUrl)
                        .post(body)
                        .build();

                response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    Log.d(TAG, "Upload successful");
                    return response.body().string();
                } else {
                    Log.d(TAG, "Upload failed with code: " + response.code());
                    return null;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error during upload", e);
                return null;
            } finally {
                if (response != null) {
                    response.close();
                }
                Log.d(TAG, "Task completed");
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    // Parse the JSON response to extract the result field
                    JSONObject jsonObject = new JSONObject(result);
                    String resultText = jsonObject.getString("result");

                    // Check if the image is fake and store it in history
                    if ("Fake".equalsIgnoreCase(resultText)) {
                        storeFakeImageUri(imageUri); // Store URI in history
                        sendFakeImageNotification(); // Send notification
                        Log.d(TAG, "Fake image detected: " + resultText);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error", e);
                }
            }
        }

        private void storeFakeImageUri(Uri uri) {
            SharedPreferences prefs = getSharedPreferences("fake_image_history", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            Set<String> history = prefs.getStringSet("history", new HashSet<>());
            history.add(uri.toString()); // Store the URI
            editor.putStringSet("history", history);
            editor.apply();
        }

        private void sendFakeImageNotification() {
            String channelId = "fake_image_detection";
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
                if (channel == null) {
                    Log.e(TAG, "NotificationChannel is null. Creating channel.");
                    createNotificationChannel();
                }
            }

            Notification notification = new NotificationCompat.Builder(ImageScanService.this, channelId)
                    .setContentTitle("Fake Image Detected")
                    .setContentText("A fake image was detected.")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build();

            notificationManager.notify(1, notification);
            Log.d(TAG, "Notification sent.");
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = "fake_image_detection";
                CharSequence channelName = "Fake Image Detection";
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
                channel.setDescription("Channel for fake image detection notifications");

                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created: " + channelId);
                } else {
                    Log.e(TAG, "NotificationManager is null.");
                }
            }
        }
    }
}
