package com.example.aidetector;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get the image URI from the intent and set it to the ImageView
        String imageUriString = getIntent().getStringExtra("image_uri");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            imageView.setImageURI(imageUri);

            // Send the image to the Flask API for prediction
            new UploadImageTask().execute(imageUri);
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
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }

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
            } catch (Exception e) {
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

                    // Set the extracted result text to the TextView
                    resultTextView.setText(resultText);
                } catch (JSONException e) {
                    // Handle JSON parsing error
                    resultTextView.setText("Error parsing response");
                    Log.e(TAG, "JSON parsing error", e);
                }
            } else {
                resultTextView.setText("Error occurred");
            }
        }
    }
}
