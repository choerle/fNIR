package khoerlevillanova.edu.fnir;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static khoerlevillanova.edu.fnir.DeviceControlActivity.EXTRAS_DEVICE_ADDRESS;
import static khoerlevillanova.edu.fnir.DeviceControlActivity.EXTRAS_DEVICE_NAME;

public class oldData extends AppCompatActivity implements AdapterView.OnItemClickListener{

    private ListView lvFiles;
    private String[] mFiles;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_data);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        lvFiles = findViewById(R.id.lvFiles);
        lvFiles.setOnItemClickListener(oldData.this);

        //Load all saved device files on the screen in a listview
        loadDataFiles();
    }

    //Load all saved device files on the screen in a listview
    public void loadDataFiles() {
        mFiles = fileList();

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.file_listview_white, R.id.fileName, mFiles);
        lvFiles.setAdapter(arrayAdapter);
    }



    //When an item on the list is clicked, open control activity and connect to device
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

        String fileName = mFiles[i];

        Toast.makeText(oldData.this, "Opening " + fileName + "...", Toast.LENGTH_LONG).show();
        //Adding device name and address to intent
        Intent intent = new Intent(oldData.this, viewOldData.class);
        intent.putExtra(EXTRAS_DEVICE_NAME, fileName);
        startActivity(intent);
    }
}

