package com.example.aidetector;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {
    ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        listView = findViewById(R.id.listView);

        // Retrieve fake image URIs from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("FakeImageHistory", MODE_PRIVATE);
        Set<String> fakeImages = prefs.getStringSet("fake_images", null);

        if (fakeImages != null) {
            List<String> fakeImageList = new ArrayList<>(fakeImages);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fakeImageList);
            listView.setAdapter(adapter);
        } else {
            // Handle case where no fake images are found
            List<String> emptyList = new ArrayList<>();
            emptyList.add("No fake images detected");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, emptyList);
            listView.setAdapter(adapter);
        }
    }
}
