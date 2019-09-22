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
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static android.os.Environment.DIRECTORY_DOCUMENTS;




public class DeviceControlActivity extends AppCompatActivity {


    //Constants
    private final String TAG = "DEVICE CONTROL ACTIVITY";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String directoryName = "fNIR Data";

    //TAGS for logging in different sections of this activity
    public static final String DATACOLLECTION_LOG_TAG = "DATA COLLECTION:   ";
    public static final String SAVINGDATA_LOG_TAG = "SAVING DATA:   ";
    public static final String LOADINGDATA_LOG_TAG = "LOADING DATA:   ";
    public static final String OPTIONSMENU_LOG_TAG = "User Selected:   ";

    //User Interface Variables variables
    private TextView dataField_730;
    private TextView dataField_850;
    private TextView timeField;
    //ProgressDialog for creating baseline test before data collection begins
    private ProgressDialog baselineProgress;

    //Data storage variables
    private dataAnalysis mDataAnalysis;
    //private bvoxy mBvoxy;   //bvoxy class for converting voltage values into oxygen readings
    private ArrayList<Double> data_730; //Array for storing 730 wavelength voltages
    private ArrayList<Double> data_850; //Array for storing 850 wavelength voltages
    private ArrayList<Double> data_time; //Array for storing time when samples are taken
    private ArrayList<Double> HB; //Array for storing oxygenated hemoglobin values
    private ArrayList<Double> HBO2; //Array for storing de-oxygenated hemoglobin values
    private int sampleCount = 20; //How many samples for the baseline test
    private int incrementProgressBy = 5; //This is a variable for the baseline test. In order to use the progress
        //dialog for the baseline test, the total samples must be 100. To get to 100 from the samples we are taking, we just do
        //100 = sampleCount*incrementProgressBy

    //Graphing variables
    //GraphView object for graphing the data
    private GraphView dataGraph;
    public Integer count = 0;
    private double time = 0;
    private int samplingRate = 100; //In milliseconds
    private LineGraphSeries<DataPoint> series_HB;
    private LineGraphSeries<DataPoint> series_HBO2;
    private LineGraphSeries<DataPoint> series_730;
    private LineGraphSeries<DataPoint> series_850;
    private getDataClass mGetDataClass;
    private Timer mTimer;

    //Bluetooth variables
    private String mDeviceName; //Sent from previous activity, name of Bluetooth Low Energy Device
    private String mDeviceAddress;  //Sent from previous activity, address of Bluetooth Low Energy Device
    private BluetoothLeService mBluetoothLeService; //Class for handling Bluetooth Low Energy Connection

    //Variables that define current state of application
    public static boolean mConnected = false; //If connected to a device, this is true
    public static boolean graphing = false; //If the application is actively updating the graph, this is true
    public static boolean filledGraph = false; //If there is data on the graph, this is true
    public static boolean dataAnalysis;

    //Class that saves samples (HB, HBO2, and data_time) of the current test, for future reference
    public saveData mSaveData;
    //Class that loads a saved test
    public savedFiles mSavedFiles;




    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //User Interface variable initializations
        timeField = findViewById(R.id.timeField);
        dataField_730 = findViewById(R.id.data730);
        dataField_850 = findViewById(R.id.data850);

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
        Log.d(TAG, "Name: " + mDeviceName + "   Address: " + mDeviceAddress);

        //Connecting to Bluetooth Low Energy services class, which manages the bluetooth connection
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }




    //Options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);

        //Determining what buttons should be available when connected to a BLE device
        if (mConnected) {

            menu.findItem(R.id.menu_connect).setVisible(false);

            //The following options determine the buttons available when the device is in different states

            //If the graph is empty
            if(!graphing && !filledGraph){
                menu.findItem(R.id.dataAnalysis).setVisible(true);
                menu.findItem(R.id.dataVoltage).setVisible(true);
                menu.findItem(R.id.stopData).setVisible(false);
                menu.findItem(R.id.continueGraph).setVisible(false);
                menu.findItem(R.id.saveData).setVisible(false);
            }

            //If currently graphing
            else if(graphing){
                menu.findItem(R.id.dataAnalysis).setVisible(false);
                menu.findItem(R.id.dataVoltage).setVisible(false);
                menu.findItem(R.id.stopData).setVisible(true);
                menu.findItem(R.id.continueGraph).setVisible(false);
                menu.findItem(R.id.saveData).setVisible(true);
            }

            //If the graph is filled but stopped
            else if(!graphing && filledGraph){
                menu.findItem(R.id.dataAnalysis).setVisible(true);
                menu.findItem(R.id.dataVoltage).setVisible(true);
                menu.findItem(R.id.stopData).setVisible(false);
                menu.findItem(R.id.continueGraph).setVisible(true);
                menu.findItem(R.id.saveData).setVisible(true);
            }
        }

        //When disconnected, the only button available should be to connect to the BLE device and go
            // to the home page
        else if(!mConnected){
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.continueGraph).setVisible(false);
            menu.findItem(R.id.dataAnalysis).setVisible(false);
            menu.findItem(R.id.dataVoltage).setVisible(false);
            menu.findItem(R.id.stopData).setVisible(false);
            menu.findItem(R.id.saveData).setVisible(false);
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

            //Begins data collection starting with a baseline test
            case R.id.dataAnalysis:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Data Collection and Analysis");
                initializeAnalyzedSeries();
                dataAnalysis = true;
                createBeginBaselineDialog();

                return true;

            //Begins collecting and graphing raw data
            case R.id.dataVoltage:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Raw Data Collection");
                initializeRawSeries();
                dataAnalysis = false;
                createBeginBaselineDialog();

                return true;

            //Continues graphing from a stopped graph
            case R.id.continueGraph:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Continue graph");
                mGetDataClass = new getDataClass(true);
                mTimer = new Timer();
                mTimer.schedule(mGetDataClass, 0, samplingRate);

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

            //Return home
            case R.id.returnTo:

                Log.d(TAG, OPTIONSMENU_LOG_TAG + "Returning home");
                Intent i = new Intent(DeviceControlActivity.this, MainActivity.class);
                startActivity(i);
                finish();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }




    //This is the first step of data collection
    //Initializes and displays a dialog that asks the user if they want to start a baseline test
    //If the user clicks yes the getDataClass is initialized, which begins a required baseline test
    private void createBeginBaselineDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

        //If clicked, begins baseline test
        builder.setPositiveButton("Begin", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mTimer = new Timer();
                mGetDataClass = new getDataClass();
                mTimer.schedule(mGetDataClass, 0, samplingRate);
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
        getDataClass(boolean continue1){
            gettingBaseline = false;
            filledGraph = true;
            graphing = true;
            invalidateOptionsMenu();
        }

        //Constructor for creating a blank graph
        getDataClass(){
            gettingBaseline = true;
            createBaselineProgress();
            filledGraph = true;
            graphing = true;
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

            //Extracting 730 wavelength data reading from data string and setting it equal to a temporary variable
            data_730_d = get730(data);

            //Extracting 850 wavelength data reading from data string and setting it equal to a temporary variable
            data_850_d = get850(data);

            //Adds data points to the respective arrays and Filters data samples and adds to the array list
            //Only used to store data points after baseline test is finished
            if(!gettingBaseline) mDataAnalysis.addSamplesToVoltageArray(data_730_d, data_850_d, count);
            //While baseline test is occuring, directly add the data points to the arrays here because the previous class is not initialized
            else{
                //Adding new readings to the array lists
                data_730.add(data_730_d);
                data_850.add(data_850_d);
            }
        }


        //Creates a baseline using the first sampleCount data samples
        //Currently, sampleCount is set to 50. 50 samples appears to work for a baseline test,
        //but future research could change this
        private void getBaseline() {

            //Getting sampleCount samples from BLE device
            if (count < sampleCount) {

                //Incrementing the progress dialog
                baselineProgress.incrementProgressBy(incrementProgressBy);

                ++count;
            }


            //After getting sampleCount data samples, create the baseline array from the first
            //sampleCount elements of the data_730 and data_850 arrays
            else if (count == sampleCount && gettingBaseline) {

                baselineProgress.dismiss();
                Log.d(TAG, DATACOLLECTION_LOG_TAG + "Baseline complete.");
                mDataAnalysis = new dataAnalysis(HB, HBO2, data_730, data_850, sampleCount);   //Initializing class for data analysis based on previously collected samples
                count = 0;  //TODO: reset all arrays after baseline
                //data_730 = new ArrayList<>();
                //data_850 = new ArrayList<>();

                gettingBaseline = false;    //When gettingBaseline is set to false, the run method will stop
                    //the baseline, and begin data collection and graphing
            }
        }


        //Every time a new data sample is collected, this function converts the raw data into Hb and HBO2 values and graphs them
        //Only used after the baseline test is finished
        public void getData() {

            //Determines when the data sample is taken, in seconds
            time = ((double) count) * samplingRate / 1000;

            //Adding new time value to time array
            data_time.add(time);
            Log.d(TAG, DATACOLLECTION_LOG_TAG + "Count: " + count + "\n\n Data: " + data_730_d + "\n\nData: " + data_730.get(count));

            if(dataAnalysis){
                //Converting the 2 voltage readings into oxygenation readings and add to respective arrays
                mDataAnalysis.addHemoglobin(count);

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
            stringBuilder.append(HB);
            stringBuilder.append(HBO2);
            stringBuilder.append(data_time);
            saved_data = stringBuilder.toString();
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

                try {//TODO: what if two files have the same name?
                    //TODO: Logging?
                    /*File extStore = new File(Environment.getExternalStorageDirectory(), directoryName);
                    if (!extStore.exists()) {
                        extStore.mkdirs();
                    }*/
                    File extStore = getPublicAlbumStorageDir(directoryName);   //Creating the directory, within external storage, to hold this apps files
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


        //Returns a directory within the main external storage folder, named directoryName
        //If this directory does not exist, this function will attempt to create it
        private File getPublicAlbumStorageDir(String directoryName) {
            //Creating the file for the desired directory
            File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), directoryName);
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
        //TODO: does this work? Better way to do this? Checking app permissions?
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
        ArrayList<Double> savedHB = new ArrayList<>();
        ArrayList<Double> savedHBO2 = new ArrayList<>();
        ArrayList<Double> savedTime = new ArrayList<>();


        //Constructor displays all saved files and when one is clicked loads that file into the graph
        savedFiles () {

            //This is accomplished with a pop up dialog
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(DeviceControlActivity.this);
            builderSingle.setTitle("Saved Files: ");

            //These lines of code find the directory where this app saves its files and then puts all of the file names into a string array
            String path = (Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).toString()+ "/" + directoryName);
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
                File extStore = getPublicAlbumStorageDir(directoryName);   //Creating the directory, within external storage, to hold this apps files
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


        public boolean deleteFile(){
            File extStore = getPublicAlbumStorageDir(directoryName);   //Creating the directory, within external storage, to hold this apps files
            String path = extStore.getAbsolutePath() + "/" + fileName;  //The address of the file we are creating
            File myFile = new File(path);
            return myFile.delete();
        }


        //Returns a directory within the main external storage folder, named directoryName
        //If this directory does not exist, this function will attempt to create it
        private File getPublicAlbumStorageDir(String directoryName) {
            //Creating the file for the desired directory
            File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), directoryName);
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
        public void splitArrayList (ArrayList<Double> inputData) {
            int size = inputData.size() / 3;
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < size; ++j) {
                    //Elements j to size, time
                    if (i == 0) savedHB.add(inputData.get(j));
                    //Elements (j + size) to 2*size, HB
                    if (i == 1) savedHBO2.add(inputData.get(j + size));
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


            //Adding the data points to the grpah
            for(int i = 0; i < savedTime.size(); ++i){
                DataPoint dataPoint = new DataPoint(savedTime.get(i), savedHB.get(i));
                series_HB.appendData(dataPoint, true,50000);

                DataPoint dataPoint2 = new DataPoint(savedTime.get(i), savedHBO2.get(i));
                series_HBO2.appendData(dataPoint2, true,50000);
            }

            dataGraph.getViewport().setMinX(0);
            dataGraph.getViewport().setMaxX(savedTime.get(savedTime.size()-1));
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
            if(mConnected)
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
                mConnected = true;
                Log.d(TAG, "Connected to BLE");
                Toast.makeText(DeviceControlActivity.this, "Connected to : " + mDeviceName, Toast.LENGTH_LONG).show();
                invalidateOptionsMenu();

                //If disconnected from GATT
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
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
        graphing = false;
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




    // TODO: this has been transferred to its own class: see
    // Class for creating oxygenated and deoxygenated hemoglobin arrays
    public class bvoxy {

        //Constants for blood oxygen calculation based on 730nm and 850nm light
        private final double eHB_730 = 1.1022;
        private final double eHBO2_730 = 0.390;
        private final double eHB_850 = 0.69132;
        private final double eHBO2_850 = 1.058;
        private final double L = 0.015;

        //Baseline values
        private Double baseline_730;
        private Double baseline_850;

        //Optical density arrays
        private ArrayList<Double> OD_730;
        private ArrayList<Double> OD_850;


        bvoxy() {

            OD_730 = new ArrayList<>();
            OD_850 = new ArrayList<>();

            HB = new ArrayList<>();
            HBO2 = new ArrayList<>();

            getBaseLine();
        }


        //Creating the baseline arrays from first sampleCount samples
        private void getBaseLine() {

            Double sum730 = 0.0;
            Double sum850 = 0.0;

            for (int i = 0; i < sampleCount; ++i) {
                sum730 += data_730.get(i);
                sum850 += data_850.get(i);
            }

            baseline_730 = sum730 / sampleCount;
            baseline_850 = sum850 / sampleCount;
        }


        //Getting the oxygenated hemoglobin levels from voltages and adding to the given arraylist
        public void addHemoglobin() {

            OD_730.add(-Math.log10(data_730.get(count) / baseline_730));
            OD_850.add(-Math.log10(data_850.get(count) / baseline_850));

            HB.add(((OD_850.get(count) * eHBO2_730) - (OD_730.get(count) * eHBO2_850)) / ((eHBO2_730 * eHB_850) - (eHBO2_850 * eHB_730)) / L);
            HBO2.add(((OD_730.get(count) * eHB_850) - (OD_850.get(count) * eHB_730)) / ((eHBO2_730 * eHB_850) - (eHBO2_850 * eHB_730)) / L);
        }
    }









    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ON DESTROY CALLED");
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }




    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "ON PAUSE CALLED");
        finish();
    }
    //TODO: destroy connection when this activity is closed



    //When the back button is pressed
    @Override
    public void onBackPressed(){
        Log.d(TAG, "ON BACK PRESSED CALLED");
        finish();
    }
}