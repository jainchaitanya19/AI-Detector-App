package com.example.aidetector;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "MainActivity2";
    ImageView imageView;
    TextView resultTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        imageView = findViewById(R.id.imageView);
        resultTextView = findViewById(R.id.predictionTextView);

        // Get the image URI from the intent and set it to the ImageView
        String imageUriString = getIntent().getStringExtra("image_uri");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            imageView.setImageURI(imageUri);

            // Send the image to the Flask API for prediction
            new UploadImageTask().execute(imageUri);
        } else {
            resultTextView.setText("No image URI provided");
        }
    }

    private class UploadImageTask extends AsyncTask<Uri, Void, String> {
        @Override
        protected String doInBackground(Uri... uris) {
            Log.d(TAG, "Task started");
            Response response = null;
            try {
                Uri imageUri = uris[0];
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

                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                String encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                OkHttpClient client = new OkHttpClient();
                String baseUrl = "http://10.0.2.2:5000/predict"; // Emulator URL

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
                    JSONObject jsonObject = new JSONObject(result);
                    String resultText = jsonObject.getString("result");
                    resultTextView.setText(resultText);

                    // Check if the image is fake and store it in history
                    if ("Fake".equalsIgnoreCase(resultText)) {
                        storeFakeImageUri(getIntent().getStringExtra("image_uri"));
                    }
                } catch (JSONException e) {
                    resultTextView.setText("Error parsing response");
                    Log.e(TAG, "JSON parsing error", e);
                }
            } else {
                resultTextView.setText("Error occurred");
            }
        }

        private void storeFakeImageUri(String uri) {
            SharedPreferences prefs = getSharedPreferences("FakeImageHistory", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            Set<String> uris = prefs.getStringSet("fake_images", new HashSet<>());
            uris.add(uri);
            editor.putStringSet("fake_images", uris);
            editor.apply();
        }
    }
}
