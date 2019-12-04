package khoerlevillanova.edu.fnir;


/*
This is the main activity for controlling and reading from the BLE device
This activity is created when an item in the device scan activity is clicked.
Upon creation, this class will connect to the desired BLE Device. Once
connected, there is an option menu to chose what to do with the device:
1. Connect
    Reconnects to BLE device that was selected on previous page if connection is lost
2. Data Analysis
    This is the main function of this class that recieves data from the BLE device and plots and analyses
    that data.
    First, this button calls the function "createBaselineDialog" which gives the user the option to begin.
    After clicking yes, the getDataClass is initialized, which then starts a short baseline test. After this test is
    complete, the class will begin analysing and plotting data received from the BLE device.
    If this button is clicked when the app is in the process of plotting, or if the graph is paused with data
    still on it, this will reset the graph and data and start a new getDataColletion class.
3. Stop Data Collection
    Stops reading values from the BLE device, but leaves graph intact and all the data samples are still contained
    in array lists
4. Save Data
    After the graph is paused, this option appears allowing the user to save the current data sample
5. Continue Graphing
    After the graph is paused, this appears and allows the user to continue graphing on the same graph, extending
    the previous data samples.
6. Load Data
    //TODO: When loading data, it is flipped about the x axis
    //TODO: comparing data?
    Loads previously saved data samples

Currently it is not possible to graph raw voltage values, only HB and HBO2 readings
 */


import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static android.os.Environment.DIRECTORY_DOCUMENTS;




public class DeviceControlActivity extends AppCompatActivity{


    //Constants
    private final String TAG = "DEVICE CONTROL ACTIVITY";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String DIRECTORY_NAME = "fNIR Data";

    //TAGS for logging in different sections of this activity
    public static final String DATACOLLECTION_LOG_TAG = "DATA COLLECTION:   ";
    public static final String SAVINGDATA_LOG_TAG = "SAVING DATA:   ";
    public static final String LOADINGDATA_LOG_TAG = "LOADING DATA:   ";
    public static final String OPTIONSMENU_LOG_TAG = "User Selected:   ";

    //User Interface Variables variables
    private TextView dataField_730;
    private TextView dataField_850;
    private TextView timeField;
    private ProgressDialog baselineProgress;
    private ImageButton settingsButton;
    private Button addMarkerButton;

    //Data variables
    private dataAnalysis mDataAnalysis;
    private getDataClass mGetDataClass;
    private Timer mTimer;
    private ArrayList<Double> data_730; //Array for storing 730 wavelength voltages
    private ArrayList<Double> data_850; //Array for storing 850 wavelength voltages
    private ArrayList<Double> data_time; //Array for storing time when samples are taken
    private ArrayList<Double> HB; //Array for storing oxygenated hemoglobin values
    private ArrayList<Double> HBO2; //Array for storing de-oxygenated hemoglobin values
    private ArrayList<Double> markerValueArray; //Array for storing oxygenated hemoglobin values
    private ArrayList<Double> markerTimeArray; //Array for storing de-oxygenated hemoglobin values
    private double time = 0;  //Time when sample is taken
    public Integer count = 0; //Count of the number of samples taken
    public int filterType = 0;//The type of data filter used; 0 = no filter, 1 = fir, 2 = iir
    private int filterOrder = 5;    //The order of the filter used
    private double cutoffFrequency = .1;    //The cutoff frequency for the iir filter
    private int NUMBER_OF_BASELINE_SAMPLES = 10; //How many samples for the baseline test
    private int SAMPLING_RATE = 100; //Sample period in milliseconds

    //Graphing variables
    private GraphView dataGraph;
    private LineGraphSeries<DataPoint> series_HB;
    private LineGraphSeries<DataPoint> series_HBO2;
    private LineGraphSeries<DataPoint> series_730;
    private LineGraphSeries<DataPoint> series_850;
    private PointsGraphSeries<DataPoint> series_Marker;

    //Bluetooth variables
    private String mDeviceName; //Sent from previous activity, name of Bluetooth Low Energy Device
    private String mDeviceAddress;  //Sent from previous activity, address of Bluetooth Low Energy Device
    private BluetoothLeService mBluetoothLeService; //Class for handling Bluetooth Low Energy Connection

    //Variables that define current state of application
    public static boolean state_connected = false; //If connected to a device, this is true
    public static boolean state_graphing = false; //If the application is actively updating the graph, this is true
    public static boolean state_filled_graphing = false; //If there is data on the graph, this is true
    public static boolean state_analyze_data = false; //If the raw data is being converted to HB and HBO2 before graphing

    //Class that allows users to save samples of the current test
    public saveData mSaveData;
    //Class that allows a user to load saved data
    public savedFiles mSavedFiles;




    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        //User Interface variable initializations
        timeField = findViewById(R.id.timeField);
        dataField_730 = findViewById(R.id.data730);
        dataField_850 = findViewById(R.id.data850);
        settingsButton = findViewById(R.id.settingsImageButton);
        addMarkerButton = findViewById(R.id.addMarkerButton);

        //Open the settings dialog
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

        //Add a marker to the graph
        addMarkerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(state_graphing) addMarker();
                else Toast.makeText(DeviceControlActivity.this, "You must be graphing in order to add a marker!", Toast.LENGTH_LONG).show();
            }
        });

        //Requesting permission to write to the app's storage, for saving data
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }

        //Graph initialization
        dataGraph = findViewById(R.id.dataGraph);
        setUpGraph();

        //Getting device name and device address from intent
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        Log.d(TAG, "Conncedted to: Name: " + mDeviceName + "   Address: " + mDeviceAddress);


        //Connecting to Bluetooth Low Energy services class, which manages the bluetooth connection
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }




    //Options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.devicecontroloptionmenu, menu);

        //Determining what buttons should be available when connected to a BLE device
        //The following options determine the buttons available when the device is in different states
        if (state_connected) {

            menu.findItem(R.id.menu_connect).setVisible(false);

            //If the graph is empty and the app is not currently graphing
            if(!state_graphing && !state_filled_graphing){
                menu.findItem(R.id.dataAnalysis).setVisible(true);
                menu.findItem(R.id.stopData).setVisible(false);
                menu.findItem(R.id.continueGraph).setVisible(false);
                menu.findItem(R.id.saveData).setVisible(false);
                menu.findItem(R.id.loadData).setVisible(true);
            }

            //If currently graphing
            else if(state_graphing){
                menu.findItem(R.id.dataAnalysis).setVisible(false);
                menu.findItem(R.id.stopData).setVisible(true);
                menu.findItem(R.id.continueGraph).setVisible(false);
                menu.findItem(R.id.saveData).setVisible(false);
                menu.findItem(R.id.loadData).setVisible(false);
            }

            //If the graph is filled but stopped
            else if(!state_graphing && state_filled_graphing){
                menu.findItem(R.id.dataAnalysis).setVisible(true);
                menu.findItem(R.id.stopData).setVisible(false);
                menu.findItem(R.id.continueGraph).setVisible(true);
                menu.findItem(R.id.saveData).setVisible(true);
                menu.findItem(R.id.loadData).setVisible(true);
            }
        }

        //When disconnected, the only button available should be to connect to the BLE device, go
            // to the home page, and to load old data
        else if(!state_connected){
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.continueGraph).setVisible(false);
            menu.findItem(R.id.dataAnalysis).setVisible(false);
            menu.findItem(R.id.stopData).setVisible(false);
            menu.findItem(R.id.saveData).setVisible(false);
            menu.findItem(R.id.loadData).setVisible(true);
        }
        return true;
    }


    //When a button is selected from the options menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            //Connect to device
            case R.id.menu_connect:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Connect");
                mBluetoothLeService.connect(mDeviceAddress);

                return true;

            //Begin collecting and graphing data
            case R.id.dataAnalysis:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Raw Data Collection");
                if(state_analyze_data) initializeAnalyzedSeries();
                else initializeRawSeries();
                createBeginBaselineDialog();

                return true;

            //Continues graphing from a stopped graph
            case R.id.continueGraph:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Continue graph");
                mGetDataClass = new getDataClass(true);
                mTimer = new Timer();
                mTimer.schedule(mGetDataClass, 0, SAMPLING_RATE);

                return true;

            //Stops data collection, but keeps graph intact
            case R.id.stopData:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Stop data collection");
                stopDataCollection();

                return true;

            //Save the current data displayed in the graph
            case R.id.saveData:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Save Data");
                stopDataCollection();
                mSaveData = new saveData();

                return true;

            case R.id.loadData:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Load Data");
                mSavedFiles = new savedFiles();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }




    //This is the first step of data collection which is called after either of the data collection buttons are clicked
    //Initializes and displays a dialog that asks the user if they want to start a baseline test
    private void createBeginBaselineDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

        //If clicked, begins baseline test
        builder.setPositiveButton("Begin", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mTimer = new Timer();
                mGetDataClass = new getDataClass();
                mTimer.schedule(mGetDataClass, 0, SAMPLING_RATE);
            }
        });

        // User cancelled the dialog, nothing happens
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });

        builder.setMessage("In order to begin data collection, a five second baseline test must be collected.")
                .setTitle("Baseline Test Required");
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    //Sets up the progress bar for showing how much of the baseline test is complete
    public void createBaselineProgress(){
        baselineProgress = new ProgressDialog(this);
        baselineProgress.setTitle("Creating Baseline...");
        baselineProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        baselineProgress.setMax(100);
        baselineProgress.setCancelable(false);
        baselineProgress.show();
    }


    //If the baseline progress bar needs to be cancelled for any reason
    //This is only used once in this program, and that is the case when the user connects to a BLE device
    //and clicks begin baseline test, but the BLE device does not send any data
    public void dismissBaselineProgress(){
        if(baselineProgress != null){
            baselineProgress.dismiss();
        }
    }




    //This is the main class for this activity. It collects and processes the data
    //This class is initialized when the user clicks yes on the createBeginBaselineDialog after selecting one of the collect data
    //options from the options menu.
    //First, this class creates a baseline, and then collects, analyses, and plots data received from the BLE Device
    public class getDataClass extends TimerTask {

        //If true, the app will begin by creating a baseline and then graphing
        private boolean gettingBaseline;
        private String data;
        private double data_730_d;
        private double data_850_d;

        //Constructor for continuing with a previous graph
        getDataClass(boolean continue_graph){
            gettingBaseline = false;
            state_filled_graphing = true;
            state_graphing = true;
            invalidateOptionsMenu();
        }

        //Constructor for creating a blank graph
        getDataClass(){
            gettingBaseline = true;
            createBaselineProgress();
            state_filled_graphing = true;
            state_graphing = true;
            mDataAnalysis = new dataAnalysis(HB, HBO2, data_730, data_850, NUMBER_OF_BASELINE_SAMPLES,
                    filterType, 1000/(new Double(SAMPLING_RATE)), filterOrder, cutoffFrequency);   //Initializing class for data analysis based on previously collected samples
            invalidateOptionsMenu();
        }

        //This method runs every X seconds, and is responsible for controlling when data is received from
        //BLE device
        public void run() {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    //Checking to see if BLE connection is valid
                    if (mBluetoothLeService != null) {

                        //If data is available from the BLE Device
                        if (mBluetoothLeService.readVoltages() != null) {

                            //This function receives data from BLE device and saves it
                            getRawDataValues();

                            //If baseline test is running, continue running it, else plot the data
                            if (gettingBaseline) getBaseline();
                            else getData();
                        }
                    }
                }
            });
        }


        //This function receives data from BLE device and saves it
        public void getRawDataValues(){

            //Read voltages is a method from the BLE service class which reads a string that the BLE
            //device sends. In this case, the string contains 2 voltage readings, one from each wavelength
            data = mBluetoothLeService.readVoltages();

            //If readVoltages does not work
            if(data.equals("ERROR")){
                Log.d(TAG, DATACOLLECTION_LOG_TAG + "UUID not found on device. Ending activity");
                Toast.makeText(DeviceControlActivity.this, "Device does not support data reading",
                        Toast.LENGTH_LONG).show();
                dismissBaselineProgress();
                finish();
                return;
            }

            //Extracting 730 and 850 nm wavelength readings from a string and setting it equal to a temporary variable
            data_730_d = get730(data);
            data_850_d = get850(data);
        }


        //Creates a baseline with NUMBER_OF_BASELINE_SAMPLES data points
        private void getBaseline() {

            //Getting sampleCount samples from BLE device
            if (count < NUMBER_OF_BASELINE_SAMPLES && gettingBaseline) {

                //Performs simple filter and adds samples to respective Array Lists
                mDataAnalysis.addSamplesToVoltageArray(data_730_d, data_850_d);

                //Incrementing the progress dialog
                baselineProgress.incrementProgressBy(100/NUMBER_OF_BASELINE_SAMPLES);

                ++count;
            }


            //After getting enough data samples for the baseline, create the baseline array
            else if (count == NUMBER_OF_BASELINE_SAMPLES && gettingBaseline) {

                baselineProgress.dismiss();
                Log.d(TAG, DATACOLLECTION_LOG_TAG + "Baseline complete.");
                mDataAnalysis.getBaseLine();

                //Once the baseline is complete, reset the arrays and count
                count = 0;
                data_730 = new ArrayList<>();
                data_850 = new ArrayList<>();

                //Since the arrays were reset, they must be reassigned in the Data Anlysis class
                mDataAnalysis.reassign(data_730, data_850);

                //Adding the first sample to the new arrays
                //mDataAnalysis.addSamplesToVoltageArray(data_730_d, data_850_d);

                gettingBaseline = false;    //When gettingBaseline is set to false, the run method will stop
                    //the baseline, and begin data collection and graphing
            }
        }


        //Every time a new data sample is collected, this function converts the raw data into Hb and HBO2 values and graphs them
        //Only used after the baseline test is finished
        public void getData() {

            //Determines when the data sample is taken, in seconds
            time = ((double) count) * SAMPLING_RATE / 1000;
            //Performs simple filter and adds samples to respective Array Lists
            mDataAnalysis.addSamplesToVoltageArray(data_730_d, data_850_d);
            //time = time - ((double) NUMBER_OF_BASELINE_SAMPLES) * SAMPLING_RATE / 1000;

            //Adding new time value to time array
            data_time.add(time);
            Log.d(TAG, DATACOLLECTION_LOG_TAG + "Count: " + count + "\n\n Data: " + data_730.get(count) + "\n\nData: " + data_850.get(count));

            //Converting the 2 voltage readings into oxygenation readings and add to respective arrays
            mDataAnalysis.addHemoglobin(count);

            if(state_analyze_data){

                //Adding the HB and HBO2 points to the graph
                graphData(HB.get(count), series_HB);
                graphData(HBO2.get(count), series_HBO2);
            }

            else{
                //Adding the 730 and 850 readings to the graph
                graphData(data_730.get(count), series_730);
                graphData(data_850.get(count), series_850);
            }

            updateUI();

            //Increment count
            ++count;
        }


        //Getting the 730 nm sample from the string received from the BLE device
        public double get730(String data){
            String data_730_1 = data.substring(0, 2);
            String data_730_2 = data.substring(3, 5);
            String data_730s = new StringBuilder().append(data_730_1).append(data_730_2).toString();
            return getDecimal(data_730s);
        }


        //Getting the 850 nm sample from the string received from the BLE device
        public double get850(String data){
            String data_850_1 = data.substring(6, 8);
            String data_850_2 = data.substring(9, 11);
            String data_850s = new StringBuilder().append(data_850_1).append(data_850_2).toString();
            return getDecimal(data_850s);
        }


        //Graphs data by adding to series and plotting it
        public void graphData(Double value,  LineGraphSeries<DataPoint> series){
            DataPoint dataPoint = new DataPoint(time, value);
            series.appendData(dataPoint, true,50000);
            dataGraph.getViewport().setMinX(0);
            dataGraph.getViewport().setMaxX(time);
        }


        //Updating the time and input reading text boxes, and updating the graph's x axis
        private void updateUI(){

            //Set time to the nearest second
            timeField.setText(String.valueOf((int)time));

            //Updating the UI to display the 730 and 850 voltage readings
            dataField_730.setText(String.format("%d", (int) data_730_d));
            dataField_850.setText(String.format("%d", (int) data_850_d));
        }
    }




    //This class saves a text file which contains HB and HBO2 readings, and the time at which each reading was taken
    //When you need to save a file, initialize this class, and this class will create a dialog to enter the file name
    //And then save the file to a specific directory
    public class saveData {

        String saved_data;
        String fileName;

        //This constructor creates one string from the HBO2, HB, and time arrays
        public saveData(){
            fileName = null;
            StringBuilder stringBuilder = new StringBuilder();

            if(state_analyze_data){
                stringBuilder.append(HB);
                stringBuilder.append(HBO2);
                stringBuilder.append(data_time);
                if(markerTimeArray != null && markerTimeArray.size() > 0 ){
                    stringBuilder.append(markerTimeArray);
                    stringBuilder.append(markerValueArray);
                    //How much "extra data" (markers and data type charatcter) are added to the string
                    Double extra = markerValueArray.size()*2.0+2;
                    stringBuilder.append(extra.toString());
                }
                else stringBuilder.append("-1.0");
                //The last character determines if the data is voltages or HB and HBO2
                stringBuilder.append("-1.0");
            }

            else {
                stringBuilder.append(data_730);
                stringBuilder.append(data_850);
                stringBuilder.append(data_time);
                if(markerTimeArray.size() > 0 ){
                    stringBuilder.append(markerTimeArray);
                    stringBuilder.append(markerValueArray);
                    //How much "extra data" (markers and data type charatcter) are added to the string
                    Double extra = markerValueArray.size()*2.0+2;
                    stringBuilder.append(extra.toString());
                }
                else stringBuilder.append("-1.0");
                stringBuilder.append("-2.0");
            }

            saved_data = stringBuilder.toString();
            Log.d(TAG, "Data to be saved: " + saved_data);
            createTextFile();
        }


        //Alert dialog that has an editText field so that the user can enter a name for the file that will be saved
        //Upon clicking the save button, this function will call saveTextAsFile which will save the file in an external storage directory
        private void createTextFile(){

            AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            View v = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.save_file_dialog, null, false);
            builder.setView(v);

            builder.setCancelable(false);
            final AlertDialog dialog = builder.create();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            //This edit text is allows the user to enter a file name
            final EditText input = v.findViewById(R.id.username);
            final Button saveButton = v.findViewById(R.id.save);
            final Button cancelButton = v.findViewById(R.id.cancel);

            //On click, the button will save the data with the name entered, as long as a name is entered
            saveButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    fileName = input.getText().toString();

                    if (fileName != null && !fileName.equals("")) {
                        Log.d(TAG, SAVINGDATA_LOG_TAG + "Saving file: " + fileName);
                        saveTextAsFile(fileName, saved_data);
                        dialog.dismiss();
                    }

                    else {
                        Log.d(TAG, SAVINGDATA_LOG_TAG + "Invalid file name: " + fileName);
                        Toast.makeText(DeviceControlActivity.this, "Please enter a valid name", Toast.LENGTH_LONG).show();
                    }
                }
            });

            //On click, the button will cancel the dialog and return to DeviceControlActivity
            cancelButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

            dialog.show();
            dialog.getWindow().setLayout(1100, 500);
        }


        //Method for saving the text file to the app's internal storage
        private void saveTextAsFile(String filename, String content) {

            //Checking if there is space on the phone to save a file
            boolean spaceAvailable = isExternalStorageWritable();

            if(spaceAvailable){

                try {
                    File extStore = getPublicAlbumStorageDir(DIRECTORY_NAME);   //Creating the directory, within external storage, to hold this apps files
                    String path = extStore.getAbsolutePath() + "/" + filename + ".txt";  //The address of the file we are creating
                    File myFile = new File(path);   //The file that we will write our data in and save it in the specified directory

                    if(!myFile.exists()) {
                        myFile.createNewFile();

                        //Writing to the file
                        FileOutputStream fOut = new FileOutputStream(myFile);
                        OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                        myOutWriter.append(content);
                        myOutWriter.close();
                        fOut.close();

                        Log.d(TAG, SAVINGDATA_LOG_TAG + filename + " saved.");
                        Toast.makeText(getApplicationContext(), filename + " saved", Toast.LENGTH_LONG).show();
                    }

                    else {
                        Log.d(TAG, SAVINGDATA_LOG_TAG + filename + " save failed.");
                        Toast.makeText(getApplicationContext(), "There is already a file named: " + filename + "." +
                                "Try again with a different file name.", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            else {
                Toast.makeText(DeviceControlActivity.this, "No space available to save this file", Toast.LENGTH_LONG);
            }
        }


        //Returns a directory within the main external storage folder, named DIRECTORY_NAME
        //If this directory does not exist, this function will attempt to create it
        private File getPublicAlbumStorageDir(String DIRECTORY_NAME) {
            //Creating the file for the desired directory
            File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), DIRECTORY_NAME);
            //If the directory does not exist, try to create it
            if (!file.exists()) {
                boolean created = file.mkdirs();
                //If the directory can not be created
                if (!created) {
                    Log.d(TAG, "Directory not created");
                }
                else Log.d(TAG, "Directory created!! YES!");
            }
            return file;
        }


        //Checking if we can write to the external storage
        private boolean isExternalStorageWritable() {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                return true;
            }
            return false;
        }
    }



    //Class for displaying all the saved data files
    //This class is initialized when the "load saved data" option is chose from the options menu
    public class savedFiles {

        String fileName;
        //Three arrays to hold data that is loaded from app storage
        ArrayList<Double> saved_data_one = new ArrayList<>();
        ArrayList<Double> saved_data_two = new ArrayList<>();
        ArrayList<Double> savedTime = new ArrayList<>();
        ArrayList<Double> savedMarkerValues = new ArrayList<>();
        ArrayList<Double> savedMarkerTime = new ArrayList<>();
        //Is the data is raw or analyzed?
        boolean data_analysis;
        //How many extra number were attatched to the end of the saved data?
        Double numberOfExtraDataPoints;


        //Constructor displays all saved files and when one is clicked loads that file into the graph
        savedFiles () {

            //This is accomplished with a pop up dialog
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(DeviceControlActivity.this);
            builderSingle.setTitle("Saved Files: ");

            //These lines of code find the directory where this app saves its files and then puts all of the file names into a string array
            String path = (Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).toString()+ "/" + DIRECTORY_NAME);
            File f = new File(path);
            String[] files = f.list();

            //If the directory is empty, do not try to list all the files in it or the app will crash
            //Normally the directory will never be empty, but this is a precaution
            if(files != null) {

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(DeviceControlActivity.this, R.layout.file_listview, R.id.fileName, files);

                builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                //Creates buttons to determine what happens with selected file
                builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fileName = arrayAdapter.getItem(which);
                        AlertDialog.Builder builderInner = new AlertDialog.Builder(DeviceControlActivity.this);
                        builderInner.setMessage(fileName);
                        builderInner.setTitle("You selected: ");
                        //Opens the selected file
                        builderInner.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openFile();
                                dialog.dismiss();
                            }
                        });
                        //Deletes the selected file
                        builderInner.setNegativeButton("Delete File", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                AlertDialog.Builder builderDelete = new AlertDialog.Builder(DeviceControlActivity.this);
                                builderDelete.setTitle("Are you sure you want to delete " + fileName + "?");

                                builderDelete.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        deleteFile();
                                        dialog.dismiss();
                                    }
                                });
                                builderDelete.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                });

                                builderDelete.show();



                            }
                        });
                        builderInner.show();
                    }
                });
                builderSingle.show();
            }

            else Toast.makeText(DeviceControlActivity.this, "You have no saved files", Toast.LENGTH_LONG).show();
        }

        //Opens the file selected from the alert dialog
        public void openFile () {

            try {
                File extStore = getPublicAlbumStorageDir(DIRECTORY_NAME);   //Creating the directory, within external storage, to hold this apps files
                String path = extStore.getAbsolutePath() + "/" + fileName;  //The address of the file we are creating
                File myFile = new File(path);
                Log.d("LOADING DATA", "Attempting to load file: " + fileName);
                FileInputStream fileInputStream = new FileInputStream(myFile);
                DataInputStream in = new DataInputStream(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));

                String inputString = null;
                String text;

                while ((text = bufferedReader.readLine()) != null) {
                    inputString = (inputString + text);
                }

                if(inputString != null) {
                    //Extracting all numbers from file
                    ArrayList<Double> inputData = new ArrayList<>();
                    Pattern p = Pattern.compile("(\\d+(?:\\.\\d+))");
                    Matcher m = p.matcher(inputString);
                    while (m.find()) {
                        inputData.add(Double.parseDouble(m.group(1)));
                    }

                    Log.d(TAG, LOADINGDATA_LOG_TAG + "Successfully loaded data. Graphing...");
                    //The input data needs to be split into its 3 components
                    splitArrayList(inputData);
                    //Graph the data
                    graphSavedData();
                }

                else{
                    Log.d(TAG, LOADINGDATA_LOG_TAG + "is empty");
                }

                bufferedReader.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, LOADINGDATA_LOG_TAG + "File Not Found Exception");
            } catch (IOException e) {
                Log.d(TAG, LOADINGDATA_LOG_TAG + "IO Exception");
            }
        }//TODO: switch graph colors on original graph


        //Delete the selected file
        public boolean deleteFile(){
            File extStore = getPublicAlbumStorageDir(DIRECTORY_NAME);   //Creating the directory, within external storage, to hold this apps files
            String path = extStore.getAbsolutePath() + "/" + fileName;  //The address of the file we are creating
            File myFile = new File(path);
            return myFile.delete();
        }


        //Returns a directory within the main external storage folder, named DIRECTORY_NAME
        //If this directory does not exist, this function will attempt to create it
        private File getPublicAlbumStorageDir(String DIRECTORY_NAME) {
            //Creating the file for the desired directory
            File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), DIRECTORY_NAME);
            //If the directory does not exist, try to create it
            if (!file.exists()) {
                boolean created = file.mkdirs();
                //If the directory can not be created
                if (!created) {
                    Log.d(TAG, LOADINGDATA_LOG_TAG + "Directory not created");
                }
                else Log.d(TAG, LOADINGDATA_LOG_TAG + "Directory created!! YES!");
            }
            return file;
        }


        //Splitting up the input array into an array for time, HB, and HBO2
        //The last character of the file is 1 if the data is HB and HBO2 and 0 if raw voltages
        public void splitArrayList(ArrayList<Double> inputData) {

            Log.d(TAG, "Loading data: " + inputData.toString());

            //The last number in the file determines if the data is raw or not
            //If it is -1, then the data is analyzed (HB and HBO2)
            //If it is not -1, then the data is raw
            if (inputData.get(inputData.size() - 1) == -1.0) {
                data_analysis = true;
            } else {
                data_analysis = false;
            }

            //The second to last number determines how many numbers were added to the end of the saved data
            //-1 indicates no markers and therefore just 2 extra numbers were added
            numberOfExtraDataPoints = inputData.get(inputData.size() - 2);

            //Find the size of an array of data (the time or data arrays, both are the same size)
            int size;
            //If there are no markers
            if(numberOfExtraDataPoints == -1) {
                size = (inputData.size()-2) / 3;
                Log.d(TAG, "Size (without marker): " + size);
            }
            //If there are markers
            else{
                //Size of each of the three arrays
                size = (inputData.size() - (int) Math.round(numberOfExtraDataPoints)) / 3;
                Log.d(TAG, "Size (with marker): " + size);
                for (int i = inputData.size() - (int) Math.round(numberOfExtraDataPoints); i < inputData.size() - 2; i = i + 2) {
                    savedMarkerTime.add(inputData.get(i));
                    savedMarkerValues.add(inputData.get(i + 1));
                }
            }

            //Create arrays from the time and the 2 data arrays
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < size; ++j) {
                    //Elements j to size
                    if (i == 0) saved_data_one.add(inputData.get(j));
                    //Elements (j + size) to 2*size, HB
                    if (i == 1) saved_data_two.add(inputData.get(j + size));
                    //Elements (j + 2*size) to end
                    if (i == 2) savedTime.add(inputData.get(j + 2 * size));
                }
            }
        }

        //Graphing the data file that was just opened
        public void graphSavedData(){

            Log.d(TAG, LOADINGDATA_LOG_TAG + "Graphing...");

            //Old series must be removed from the graph
            dataGraph.removeAllSeries();

            //If there are markers
            if(numberOfExtraDataPoints != -1) {
                //Adding the markers to the graph
                series_Marker = new PointsGraphSeries<>();
                series_Marker.setColor(Color.BLACK);
                dataGraph.addSeries(series_Marker);
                series_Marker.setTitle("Markers");
                series_Marker.setShape(PointsGraphSeries.Shape.POINT);
                for (int i = 0; i < savedMarkerValues.size(); ++i) {
                    DataPoint dataPoint = new DataPoint(savedMarkerTime.get(i), savedMarkerValues.get(i));
                    series_Marker.appendData(dataPoint, true, 50000);
                }
            }

            //Graphing HB and HBO2
            if(data_analysis) {

                //Deoxygenated hemoglobin series initialization
                series_HB = new LineGraphSeries<>();
                series_HB.setColor(Color.RED);
                dataGraph.addSeries(series_HB);
                series_HB.setTitle("Deoxygenated");

                //Oxygenated hemoglobin series initialization
                series_HBO2 = new LineGraphSeries<>();
                series_HBO2.setColor(Color.BLUE);
                dataGraph.addSeries(series_HBO2);
                series_HBO2.setTitle("Oxygenated");

                //Used to determine the largest and smallest numbers in the data set
                Double smallest = 10000.0;
                Double largest = 0.0;

                //Adding the data points to the grpah
                for (int i = 0; i < savedTime.size(); ++i) {

                    if(saved_data_one.get(i) > largest){
                        largest = saved_data_one.get(i);
                    }
                    if(saved_data_two.get(i) > largest){
                        largest = saved_data_two.get(i);
                    }

                    if(saved_data_one.get(i) < smallest){
                        smallest = saved_data_one.get(i);
                    }
                    if(saved_data_two.get(i) < smallest){
                        smallest = saved_data_two.get(i);
                    }

                    DataPoint dataPoint = new DataPoint(savedTime.get(i), saved_data_one.get(i));
                    series_HB.appendData(dataPoint, true, 50000);

                    DataPoint dataPoint2 = new DataPoint(savedTime.get(i), saved_data_two.get(i));
                    series_HBO2.appendData(dataPoint2, true, 50000);
                }

                //Setting up the graph for analyzed data
                dataGraph.getViewport().setMinX(0);
                dataGraph.getViewport().setMaxX(savedTime.get(savedTime.size() - 1));
                dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Hemodynamic Changes");
                dataGraph.setTitle("Oxygenation Levels");
                dataGraph.getLegendRenderer().setVisible(true);
                dataGraph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
                dataGraph.getLegendRenderer().setTextColor(Color.WHITE);
                dataGraph.getLegendRenderer().setTextSize(60);
                dataGraph.getLegendRenderer().setMargin(20);
                dataGraph.getLegendRenderer().setSpacing(40);
                dataGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
                dataGraph.getViewport().setMinY(smallest - 5);
                dataGraph.getViewport().setMaxY(largest + 5);
            }

            //Graphing voltages
            else{
                //Deoxygenated hemoglobin series initialization
                series_730 = new LineGraphSeries<>();
                series_730.setColor(Color.RED);
                dataGraph.addSeries(series_730);
                series_730.setTitle("730 nm");

                //Oxygenated hemoglobin series initialization
                series_850 = new LineGraphSeries<>();
                series_850.setColor(Color.BLUE);
                dataGraph.addSeries(series_850);
                series_850.setTitle("850 nm");

                Double smallest = 10000.0;
                Double largest = 0.0;

                //Adding the data points to the grpah
                for (int i = 0; i < savedTime.size(); ++i) {

                    if(saved_data_one.get(i) > largest){
                        largest = saved_data_one.get(i);
                    }
                    if(saved_data_two.get(i) > largest){
                        largest = saved_data_two.get(i);
                    }

                    if(saved_data_one.get(i) < smallest){
                        smallest = saved_data_one.get(i);
                    }
                    if(saved_data_two.get(i) < smallest){
                        smallest = saved_data_two.get(i);
                    }

                    DataPoint dataPoint = new DataPoint(savedTime.get(i), saved_data_one.get(i));
                    series_730.appendData(dataPoint, true, 50000);

                    DataPoint dataPoint2 = new DataPoint(savedTime.get(i), saved_data_two.get(i));
                    series_850.appendData(dataPoint2, true, 50000);
                }

                //Setting up the graph for raw data
                dataGraph.getViewport().setMinX(0);
                dataGraph.getViewport().setMaxX(savedTime.get(savedTime.size() - 1));
                dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Voltage");//TODO: what is the y axis?
                dataGraph.setTitle("Light Sensor Data");
                dataGraph.getLegendRenderer().setVisible(true);
                dataGraph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
                dataGraph.getLegendRenderer().setTextColor(Color.WHITE);
                dataGraph.getLegendRenderer().setTextSize(60);
                dataGraph.getLegendRenderer().setMargin(20);
                dataGraph.getLegendRenderer().setSpacing(40);
                dataGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
                dataGraph.getViewport().setMinY(smallest - 100);
                dataGraph.getViewport().setMaxY(largest + 100);
            }
        }
    }



    // Code to manage Service lifecycle, connects to BLE when activity is first started
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            if(state_connected)
                Log.d(TAG, "BLE connection");
            else
                Log.d(TAG, "No connection was established");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };





    // Handles various events regarding the bluetooth device
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();

            //If connected to GATT
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                state_connected = true;
                Log.d(TAG, "Connected to BLE");
                Toast.makeText(DeviceControlActivity.this, "Connected to : " + mDeviceName, Toast.LENGTH_LONG).show();
                invalidateOptionsMenu();

                //If disconnected from GATT
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                state_connected = false;
                Log.d(TAG, "Disconnected from BLE");
                Toast.makeText(DeviceControlActivity.this, "Disconnected", Toast.LENGTH_LONG).show();
                invalidateOptionsMenu();

                //If services are discovered, show all the supported services and characteristics on the user interface.
            /*} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Services discovered");

                //If data is available
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "DATA: " + data);
                //The bluetooth service must not be null or without a try catch the app will crash
                try {
                    data = mBluetoothLeService.readVoltages();
                    Log.d(TAG, "Reading data:      " + data);
                }
                catch(NullPointerException e){
                    Log.d(TAG, "NULL POINTER");
                }
                String data_730_1 = data.substring(0, 2);
                String data_730_2 = data.substring(3, 5);
                String data_730s = new StringBuilder().append(data_730_1).append(data_730_2).toString();
                dataField_730.setText(data_730s);*/
            }
        }
    };




    //Stops graphing and collecting data
    //This is used when the user clicks either "Stop Graphing" or "Save Data"
    public void stopDataCollection(){
        state_graphing = false;
        Log.d(TAG, "Graph stopped");
        if(mTimer!= null) {
            mTimer.cancel();
            mTimer.purge();
        }
        invalidateOptionsMenu();
    }




    //Initializing the series, timer, and getData class for graphing HB and HBo2
    //A series is just a collection of numbers to be plotted on a graph (a special class for creating graphs)
    public void initializeAnalyzedSeries(){

        //Resetting the time for incoming data
        count = 0;
        time = 0;

        //Old series must be removed from the graph
        dataGraph.removeAllSeries();

        //Creating new array list to store the data
        data_850 = new ArrayList<>();
        data_730 = new ArrayList<>();
        data_time = new ArrayList<>();
        HB = new ArrayList<>();
        HBO2 = new ArrayList<>();
        markerTimeArray = new ArrayList<>();
        markerValueArray = new ArrayList<>();

        //Deoxygenated hemoglobin series initialization
        series_HB = new LineGraphSeries<>();
        series_HB.setColor(Color.BLUE);
        dataGraph.addSeries(series_HB);
        series_HB.setTitle("Oxygenated");

        //Oxygenated hemoglobin series initialization
        series_HBO2 = new LineGraphSeries<>();
        series_HBO2.setColor(Color.RED);
        dataGraph.addSeries(series_HBO2);
        series_HBO2.setTitle("Deoxygenated");

        series_Marker = new PointsGraphSeries<>();
        series_Marker.setColor(Color.BLACK);
        dataGraph.addSeries(series_Marker);
        series_Marker.setTitle("Markers");
        series_Marker.setShape(PointsGraphSeries.Shape.POINT);

        //Setting up the graph for analyzed data
        dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Hemodynamic Changes");
        dataGraph.setTitle("Oxygenation Levels");
        dataGraph.getLegendRenderer().setVisible(true);
        dataGraph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
        dataGraph.getLegendRenderer().setTextColor(Color.WHITE);
        dataGraph.getLegendRenderer().setTextSize(60);
        dataGraph.getLegendRenderer().setMargin(20);
        dataGraph.getLegendRenderer().setSpacing(40);
        dataGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        dataGraph.getViewport().setMinY(-20);
        dataGraph.getViewport().setMaxY(30);
    }



    //Initializing the series, timer, and getData class for graphing raw voltages from the sensor
    //A series is just a collection of numbers to be plotted on a graph (a special class for creating graphs)
    public void initializeRawSeries(){

        //Resetting the time for incoming data
        count = 0;
        time = 0;

        //Old series must be removed from the graph
        dataGraph.removeAllSeries();

        //Creating new array list to store the data
        data_850 = new ArrayList<>();
        data_730 = new ArrayList<>();
        data_time = new ArrayList<>();
        HB = new ArrayList<>();
        HBO2 = new ArrayList<>();
        markerTimeArray = new ArrayList<>();
        markerValueArray = new ArrayList<>();

        //Deoxygenated hemoglobin series initialization
        series_730 = new LineGraphSeries<>();
        series_730.setColor(Color.BLUE);
        dataGraph.addSeries(series_730);
        series_730.setTitle("730 nm");

        //Oxygenated hemoglobin series initialization
        series_850 = new LineGraphSeries<>();
        series_850.setColor(Color.RED);
        dataGraph.addSeries(series_850);
        series_850.setTitle("850 nm");

        series_Marker = new PointsGraphSeries<>();
        series_Marker.setColor(Color.BLACK);
        dataGraph.addSeries(series_Marker);
        series_Marker.setTitle("Markers");
        series_Marker.setShape(PointsGraphSeries.Shape.POINT);

        //Setting up the graph for raw data
        dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Voltage");//TODO: what is the y axis?
        dataGraph.setTitle("Light Sensor Data");
        dataGraph.getLegendRenderer().setVisible(true);
        dataGraph.getLegendRenderer().setBackgroundColor(Color.TRANSPARENT);
        dataGraph.getLegendRenderer().setTextColor(Color.WHITE);
        dataGraph.getLegendRenderer().setTextSize(60);
        dataGraph.getLegendRenderer().setMargin(20);
        dataGraph.getLegendRenderer().setSpacing(40);
        dataGraph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        dataGraph.getViewport().setMinY(0);
        dataGraph.getViewport().setMaxY(3000);
    }




    //Converts hex string to decimal
    public static int getDecimal(String hex){

        String digits = "0123456789ABCDEF";
        hex = hex.toUpperCase();
        int val = 0;

        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }

        return val;
    }




    //Intent filter for starting BluetoothLeService
    private static IntentFilter makeGattUpdateIntentFilter() {

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }




    //Request permissions for save data
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1000:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(this, "Permission not Granted!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }



    //Basic graph set up
    public void setUpGraph(){
        dataGraph.getViewport().setYAxisBoundsManual(true);
        dataGraph.getViewport().setMinY(-20);
        dataGraph.getViewport().setMaxY(30);
        dataGraph.getViewport().setXAxisBoundsManual(true);
        dataGraph.setTitleTextSize(110);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(70);
        dataGraph.getGridLabelRenderer().setVerticalAxisTitleTextSize(70);
        dataGraph.getGridLabelRenderer().setGridColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
        dataGraph.setTitleColor(Color.WHITE);
        dataGraph.setTitle("fNIRS");
        dataGraph.getGridLabelRenderer().setPadding(50);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        dataGraph.getGridLabelRenderer().setHighlightZeroLines(true);
    }



    //This function is called when the add marker button is clicked
    //It adds a black dot to the graph at the location of current time and data
    //It also adds this data point to the array list for markerValues and MarkerTimes
    public void addMarker(){
        DataPoint dataPoint;
        if(state_analyze_data){
            markerTimeArray.add(time);
            markerValueArray.add(HB.get(count-1));
            dataPoint = new DataPoint(time, HB.get(count-1));
        }
        else{
            markerTimeArray.add(time);
            markerValueArray.add(data_730.get(count-1));
            dataPoint = new DataPoint(time, data_730.get(count-1));
        }
        series_Marker.appendData(dataPoint, true,50000);
    }





    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ON DESTROY CALLED");
        endActivity();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "ON PAUSE CALLED");
        endActivity();
    }

    //When the back button is pressed
    @Override
    public void onBackPressed(){
        Log.d(TAG, "ON BACK PRESSED CALLED");
        endActivity();
    }

    public void endActivity(){
        if(mTimer != null){
            mTimer.cancel();
            mTimer.purge();
        }

        if(mBluetoothLeService != null){
            unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
        state_graphing = false;
        state_connected = false;
        state_filled_graphing = false;

        finish();
    }






    //This function is called when the settings button at the bottom of the screen is clicked
    //It opens a custom dialog that has several options to adjust different settings
    public void openSettings(){
        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

        // Inflate and set the custom layout for the dialog
        View v = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.settings_dialog, null, false);
        builder.setView(v);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        //Button to open settings for data collection
        Button dataSettingsButton = v.findViewById(R.id.dataSettingsButton);
        dataSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dataSettingsDialog();
            }
        });

        //Button to open settings for adjusting the filter
        Button filterSettingsButton = v.findViewById(R.id.filterSettingsButton);
        filterSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterSettingsDialog();
            }
        });

        //Button to open settings for the graph
        Button graphSettingsButton = v.findViewById(R.id.graphSettings);
        graphSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                graphSettingsDialog();
            }
        });

        //Cancel the dialog
        Button cancelButton = v.findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }


    //This is called from the settings dialog
    //This function opens up a dialog to adjust the settings of the filter
    public void filterSettingsDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

        View v = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.filter_settings, null, false);
        builder.setView(v);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        //A spinner to determine which type of filter to use
        //0 = no filter, 1 = fir, 2 = iir
        Spinner filterTypeSpinner = v.findViewById(R.id.filterType);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.filter_types, android.R.layout.simple_spinner_item);
        filterTypeSpinner.setAdapter(adapter);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterTypeSpinner.setSelection(filterType);
        filterTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterType = position;
                Log.d(TAG, "Selected filter: " + filterType);
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //A number picker to determine the filter order
        NumberPicker filterOrderNumberPicker = v.findViewById(R.id.filterOrderNumberPicker);
        filterOrderNumberPicker.setMaxValue(9);
        filterOrderNumberPicker.setMinValue(1);
        filterOrderNumberPicker.setValue(filterOrder);
        filterOrderNumberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                filterOrder = newVal;
                Log.d(TAG, "Selected filter order: " + filterOrder);
            }
        });

        //A number picker to determine the cutoff frequency of the iir filter
        NumberPicker cutoffFreqNumberPicker = v.findViewById(R.id.cutoffFreqNumberPicker);
        cutoffFreqNumberPicker.setMaxValue(4);
        cutoffFreqNumberPicker.setMinValue(1);
        String[] freqOptions = new String[]{".1 Hz", ".25 Hz", "1 Hz", "10 Hz"};
        cutoffFreqNumberPicker.setDisplayedValues(freqOptions);
        int currentCutoffFreq;
        if(cutoffFrequency == .1){
            currentCutoffFreq = 1;
        }
        else if(cutoffFrequency == .25){
            currentCutoffFreq = 2;
        }
        else if(cutoffFrequency == 1){
            currentCutoffFreq = 3;
        }
        else{
            currentCutoffFreq = 4;
        }
        cutoffFreqNumberPicker.setValue(currentCutoffFreq);
        cutoffFreqNumberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                switch (newVal){
                    case 1:
                        cutoffFrequency = .1;
                    case 2:
                        cutoffFrequency = .25;
                    case 3:
                        cutoffFrequency = 1.0;
                    case 4:
                        cutoffFrequency = 10.0;
                    default:
                        cutoffFrequency = .1;
                }
            }
        });

        //A button to open up a dialog explaining the filter settings and what they do
        ImageButton infoButton = v.findViewById(R.id.infoButton);
        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder1 = new AlertDialog.Builder(DeviceControlActivity.this);
                View v1 = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.info_dialog, null, false);
                builder1.setView(v1);
                final AlertDialog dialog1 = builder1.create();
                dialog1.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog1.show();
            }
        });

        //A button to close the dialog
        Button okButton = v.findViewById(R.id.okButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }


    //Opened from the settings dialog
    //This function adjusts the graph's display settings
    public void graphSettingsDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

        View v = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.graph_settings, null, false);
        builder.setView(v);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));




        dialog.show();
    }


    //Opened from the settings dialog
    //This function adjusts the data collection settings
    public void dataSettingsDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

        View v = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.data_settings, null, false);
        builder.setView(v);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        //A swtich to change from graphing raw data to graphing HB and HBO2
        final Switch analyzeDataSwitch = v.findViewById(R.id.analyzeDataSwitch);
        if(state_analyze_data == true) analyzeDataSwitch.setChecked(true);
        else analyzeDataSwitch.setChecked(false);
        if(!state_graphing) {
            analyzeDataSwitch.setEnabled(true);
            analyzeDataSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) state_analyze_data = true;
                    else state_analyze_data = false;
                }
            });
        }
        else{
            analyzeDataSwitch.setEnabled(false);
        }

        //A number picker to determine the baseline size
        NumberPicker samplesNumberPicker = v.findViewById(R.id.baselineSamplesNumberPicker);
        samplesNumberPicker.setMaxValue(20);
        samplesNumberPicker.setMinValue(1);
        samplesNumberPicker.setValue(NUMBER_OF_BASELINE_SAMPLES);
        samplesNumberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                NUMBER_OF_BASELINE_SAMPLES = newVal;
                Log.d(TAG, "NUMBER OF BASELINE SAMPLES CHANGED TO " + NUMBER_OF_BASELINE_SAMPLES);
            }
        });

        //A number picker to determine the sampling frequency
        NumberPicker samplingFreqNumberPicker = v.findViewById(R.id.samplingFreqNumberPicker);
        samplingFreqNumberPicker.setMaxValue(4);
        samplingFreqNumberPicker.setMinValue(1);
        String[] freqOptions = new String[]{"1 Hz", "5 Hz", "10 Hz", "20 Hz"};
        samplingFreqNumberPicker.setDisplayedValues(freqOptions);
        int currentSamplingFreq;
        if(SAMPLING_RATE == 1000){
            currentSamplingFreq = 1;
        }
        else if(SAMPLING_RATE == 500){
            currentSamplingFreq = 2;
        }
        else if(SAMPLING_RATE == 100) {
            currentSamplingFreq = 3;
        }
        else{
            currentSamplingFreq = 4;
        }
        samplingFreqNumberPicker.setValue(currentSamplingFreq);
        samplingFreqNumberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                switch (newVal){
                    case 1:
                        SAMPLING_RATE = 1000;
                        Log.d(TAG, "SAMPLING RATE CHANGED TO " + SAMPLING_RATE);
                    case 2:
                        SAMPLING_RATE = 500;
                        Log.d(TAG, "SAMPLING RATE CHANGED TO " + SAMPLING_RATE);
                    case 3:
                        SAMPLING_RATE = 100;
                        Log.d(TAG, "SAMPLING RATE CHANGED TO " + SAMPLING_RATE);
                    case 4:
                        SAMPLING_RATE = 50;
                    default:
                        SAMPLING_RATE = 100;
                        Log.d(TAG, "SAMPLING RATE CHANGED TO " + SAMPLING_RATE);
                }
            }
        });

        //Closes the dialog
        Button okButton = v.findViewById(R.id.okButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}