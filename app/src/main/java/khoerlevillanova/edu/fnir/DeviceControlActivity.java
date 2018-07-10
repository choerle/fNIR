package khoerlevillanova.edu.fnir;


/*
This is the main activity for controlling and reading from the BLE device
This activity is created when an item in the device scan activity is clicked.
Upon creation, the GATT server will automatically be connected to. Once
connected, there is a menu to chose what to do with the device.
1. Read voltages and then plot them
2.
 */


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
import android.graphics.Color;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;




public class DeviceControlActivity extends AppCompatActivity {




    //Constants
    private final String TAG = "DeviceControlActivity";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    //private final String LIST_NAME = "NAME";
    //private final String LIST_UUID = "UUID";

    //UI variables
    private TextView dataField_730;
    private TextView dataField_850;
    private TextView timeField;
    private GraphView dataGraph;
    private ProgressDialog baselineProgress;

    //Data storage variables
    private bvoxy mBvoxy;
    private ArrayList<Double> data_730;
    private ArrayList<Double> data_850;
    private ArrayList<Double> data_time;
    private ArrayList<Double> HB;
    private ArrayList<Double> HBO2;

    //Graphing variables
    private boolean graphingRaw;
    private int count = 0;
    private double time = 0;
    private int samplingRate = 500; //In milliseconds
    private LineGraphSeries<DataPoint> series_HB;
    private LineGraphSeries<DataPoint> series_HBO2;
    private LineGraphSeries<DataPoint> series_730;
    private LineGraphSeries<DataPoint> series_850;
    private getDataClass mGetDataClass;
    private Timer mTimer;

    //Bluetooth variables
    private boolean servicesAvailable = false; //if true services have been discovered
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    //private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList();
    //private BluetoothGattCharacteristic mNotifyCharacteristic;

    //Management of devices state
    private boolean mConnected = false;     //If connected to a device, this is true
    private boolean graphing = false;       //If the application is actively updating the graph, this is true
    private boolean filledGraph = false;    //If there is data on the graph, this is true




    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //UI variable initializations
        timeField = findViewById(R.id.timeField);
        dataField_730 = findViewById(R.id.data730);
        dataField_850 = findViewById(R.id.data850);

        //Graphing initialization
        dataGraph = findViewById(R.id.dataGraph);
        dataGraph.getViewport().setYAxisBoundsManual(true);
        dataGraph.getViewport().setMinY(-100);
        dataGraph.getViewport().setMaxY(100);
        dataGraph.getViewport().setXAxisBoundsManual(true);
        dataGraph.setTitleTextSize(110);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(60);
        dataGraph.getGridLabelRenderer().setVerticalAxisTitleTextSize(60);
        dataGraph.getGridLabelRenderer().setGridColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);
        dataGraph.getGridLabelRenderer().setVerticalLabelsColor(Color.WHITE);
        dataGraph.setTitleColor(Color.WHITE);
        dataGraph.setTitle("Raw fNIR Data");
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time (Seconds)");
        dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Intensity (Voltage)");

        //Getting device name and address from intent
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        Log.d(TAG, "Name: " + mDeviceName + "   Address: " + mDeviceAddress);

        //Connecting to BLE services class
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }




    //Options menu for connecting and disconnecting from device
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);

        //Determining what buttons should be available when connected to a device
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            //menu.findItem(R.id.menu_disconnect).setVisible(true);

            //If the graph is empty, both data collecting options should be available
            if(!graphing && !filledGraph){
                menu.findItem(R.id.startData).setVisible(true);
                menu.findItem(R.id.dataAnalysis).setVisible(true);
            }

            //If the graph is filled but stopped
            if(!graphing && filledGraph){
                menu.findItem(R.id.continueGraph).setVisible(true);
                menu.findItem(R.id.startData).setVisible(false);
                menu.findItem(R.id.dataAnalysis).setVisible(false);
            }
            else
                menu.findItem(R.id.continueGraph).setVisible(false);

            //While the app is graphing
            if (graphing) {
                menu.findItem(R.id.stopData).setVisible(true);
                menu.findItem(R.id.startData).setVisible(false);
                menu.findItem(R.id.dataAnalysis).setVisible(false);
            }

            //While the app is not graphing
            else if(!graphing)
                menu.findItem(R.id.stopData).setVisible(false);

            //Set clear graph to only be shown when the graph has data
            if(filledGraph)
                menu.findItem(R.id.clearGraph).setVisible(true);
            else if(!filledGraph)
                menu.findItem(R.id.clearGraph).setVisible(false);
        }

        //When disconnected, the only button should be to connect and go home
        else if(!mConnected){
            menu.findItem(R.id.menu_connect).setVisible(true);
            //menu.findItem(R.id.menu_disconnect).setVisible(false);
            menu.findItem(R.id.startData).setVisible(false);
            menu.findItem(R.id.dataAnalysis).setVisible(false);
            menu.findItem(R.id.stopData).setVisible(false);
            menu.findItem(R.id.clearGraph).setVisible(false);
        }
        return true;
    }




    //Options menu for graphing and connecting to BLE device
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            //Connect to device
            case R.id.menu_connect:
                if (!mConnected) {
                    Log.d(TAG, "Menu item connect: trying to connect");
                    mBluetoothLeService.connect(mDeviceAddress);
                }
                return true;

            //Begins collection and graphing of raw data
            case R.id.startData:

                if(!graphing && mConnected) {
                    graphingRaw = true;
                    Log.d(TAG, "Menu item startData: trying to startData");
                    initializeSeries();
                    mTimer.schedule(mGetDataClass, 0, samplingRate);
                    invalidateOptionsMenu();
                }
                return true;

            case R.id.dataAnalysis:

                if(!graphing && mConnected) {
                    graphingRaw = false;
                    Log.d(TAG, "Menu item startData: trying to startData");

                    AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);

                    builder.setPositiveButton("Begin", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            initializeSeries();
                            mTimer.schedule(mGetDataClass, 0, samplingRate);
                        }
                    });

                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });

                    builder.setMessage("In order to begin data collection, a five second baseline test must be collected.")
                            .setTitle("Baseline Test Required");
                    AlertDialog dialog = builder.create();
                    dialog.show();

                    invalidateOptionsMenu();
                }
                return true;

            //Continues graphing from a stopped graph
            case R.id.continueGraph:

                mGetDataClass = new getDataClass(true);
                mTimer = new Timer();
                mTimer.schedule(mGetDataClass, 0, samplingRate);

                return true;

            //Stops collection of data, but keeps graph intact
            case R.id.stopData:
                if(graphing && mConnected) {
                    Log.d(TAG, "Menu item stopData: trying to stopData");
                    stopDataCollection();
                }
                return true;

            //Stops collection of data and clears the graph
            case R.id.clearGraph:
                if(mConnected && filledGraph) {
                    Log.d(TAG, "Menu item clearGraph: trying to clearGraph");
                    clearGraph();
                }
                return true;

            //Return home
            case R.id.returnTo:
                Log.d(TAG, "Returning home");
                clearGraph();
                Intent i = new Intent(DeviceControlActivity.this, MainActivity.class);
                startActivity(i);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }




    //This is the class that recieves the data from the device and then plots it
    //If graphingRaw is true, then a raw graph of voltage readings will be created
    //If graphingRaw is false, then a 20 sample baseline test will be collected and then
        //the data analysis will begin
    public class getDataClass extends TimerTask {

        //If true, the app will begin by creating a baseline and then graphing
        private boolean gettingBaseline;
        private String data;
        private double data_730_d;
        private double data_850_d;


        //Constructor for continuing with a previous graph
        getDataClass(boolean continue1){
            gettingBaseline = false;
        }


        //Constructor for creating a blank graph
        getDataClass(){

            //Graphing raw voltages
            if(graphingRaw){
                gettingBaseline = false;
                dataGraph.getViewport().setMinY(0);
                dataGraph.getViewport().setMaxY(2000);
                dataGraph.getViewport().setMinX(0);
                dataGraph.getViewport().setMaxX(1);
                dataGraph.setTitle("Raw fNIR Data");
                dataGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time (Seconds)");
                dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Intensity (Voltage)");
            }

            //Graphing oxygenation readings
            else {
                dataGraph.getViewport().setMinY(-100);
                dataGraph.getViewport().setMaxY(100);
                dataGraph.setTitle("Oxygenation");
                dataGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time (Seconds)");
                dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Concentration");
                //Initialization of progress dialog for creating baseline text
                createBaselineProgress();
                gettingBaseline = true;
                baselineProgress.show();
            }

            filledGraph = true;
            graphing = true;
            invalidateOptionsMenu();
        }


        //This method runs every X seconds
        public void run() {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    //Checking to see if data is available
                    if (mBluetoothLeService.readVoltages() != null && mBluetoothLeService != null) {

                        //This sets the data string equal to input string
                        getRawDataValues();

                        //730 wavelength
                        data_730_d = get730(data);

                        //850 wavelength
                        data_850_d = get850(data);

                        //For graphing raw voltages
                        if(graphingRaw){
                            getRawData();
                        }

                        //For first creating a baseline then graphing oxygen levels
                        else {
                            if (gettingBaseline)
                                getBaseline();
                            else
                                getData();
                        }
                    }
                }
            });
        }


        //Getting the raw string from BLE device
        private void getRawDataValues(){
            data = mBluetoothLeService.readVoltages();
            Log.d(TAG, "Reading data:      " + data);
        }


        //Graphing raw voltages
        private void getRawData(){

            //Determines when the data sample is taken, in seconds
            time = ((double)count) * samplingRate / 1000;

            //Adding voltages to graph
            graphData(data_730_d, series_730);
            graphData(data_850_d, series_850);

            updateUI();

            //Increment count in order to increment time
            ++count;
        }


        //Creates a baseline using the first few data samples
        private void getBaseline() {

            //Getting the first 20 samples for a baseline
            if (count < 20) {

                //Incrementing the progress dialog
                baselineProgress.incrementProgressBy(5);

                //Adding new readings to the array lists
                data_730.add(data_730_d);
                data_850.add(data_850_d);

                ++count;
            }


            //After getting 20 data samples, create the baseline array
            else if (count == 20 && gettingBaseline) {

                baselineProgress.dismiss();
                Log.d(TAG, "BEGINNING DATA ANALYSIS");
                mBvoxy = new bvoxy(data_730, data_850);
                count = 0;
                gettingBaseline = false;
            }
        }


        //After obtaining a baseline, read in the voltages and convert them
        private void getData() {

            //Determines when the data sample is taken, in seconds
            time = ((double)count) * samplingRate / 1000;

            //Adding new readings to the array lists
            data_730.add(data_730_d);
            data_850.add(data_850_d);

            //Converting the 2 voltage readings into oxygenation readings
            mBvoxy.addHemoglobin(HB, HBO2, count, data_850_d, data_730_d);

            //Adding the HB and HBO2 points to the graph
            graphData(HB.get(count), series_HB);
            graphData(HBO2.get(count), series_HBO2);

            updateUI();

            //Increment count in order to increment time
            ++count;
        }


        //Getting the 730 readings from the input string
        private double get730(String data){

            String data_730_1 = data.substring(0, 2);
            String data_730_2 = data.substring(3, 5);
            String data_730s = new StringBuilder().append(data_730_1).append(data_730_2).toString();
            return getDecimal(data_730s);
        }


        //Getting the 850 readings from the input string
        private double get850(String data){

            String data_850_1 = data.substring(6, 8);
            String data_850_2 = data.substring(9, 11);
            String data_850s = new StringBuilder().append(data_850_1).append(data_850_2).toString();
            return getDecimal(data_850s);
        }


        //TODO: is this way of graphing slow?
        //Graphs data by adding to old series and plotting it
        public void graphData(Double value,  LineGraphSeries<DataPoint> series){

            Log.d(TAG, "Graphing...");
            DataPoint dataPoint = new DataPoint(time, value);
            series.appendData(dataPoint, true,1000);
        }


        //Updating the time and input reading text boxes, and updating the graph's x axis
        private void updateUI(){

            //Set time to the nearest second
            timeField.setText(String.valueOf((int)time));

            //Updating the UI to display the 730 and 850 voltage readings
            dataField_730.setText(String.valueOf(data_730_d));
            dataField_850.setText(String.valueOf(data_850_d));

            dataGraph.getViewport().setMinX(0);
            dataGraph.getViewport().setMaxX(((int)time)+1);
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
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Services discovered");
                servicesAvailable = true;
                invalidateOptionsMenu();

                //If data is available
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "DATA: " + data);
                //displayChractersiticInfo(data);
            }
        }
    };




    //Stops graphing and collecting data
    public void stopDataCollection(){
        graphing = false;
        Log.d(TAG, "Graph stopped");
        invalidateOptionsMenu();
        if(mTimer!= null) {
            mTimer.cancel();
            mTimer.purge();
        }
    }




    // first stops data collection and then clears the graph
    public void clearGraph(){
        filledGraph = false;
        stopDataCollection();
        dataField_730.setText("...");
        dataField_850.setText("...");
        timeField.setText("...");
        Log.d(TAG, "Graph cleared");
        //Old series must be removed from the graph
        dataGraph.removeAllSeries();
    }




    //Initializing the series, timer, and getData class
    //A series is just a collection of numbers to be plotted on a graph
    public void initializeSeries(){

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

        //Oxygenated hemoglobin series initialization
        series_HBO2 = new LineGraphSeries<>();
        series_HBO2.setColor(Color.RED);
        dataGraph.addSeries(series_HBO2);

        //730nm wavelength series initialization
        series_730 = new LineGraphSeries<>();
        series_730.setColor(Color.BLUE);
        dataGraph.addSeries(series_730);

        //850nm wavelength series initialization
        series_850 = new LineGraphSeries<>();
        series_850.setColor(Color.RED);
        dataGraph.addSeries(series_850);

        mTimer = new Timer();
        mGetDataClass = new getDataClass();
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




    //Class for creating oxygenated and deoxygenated hemoglobin arrays
    public class bvoxy {

        //Constants for blood oxygen calculation based on 730nm and 850nm light
        private final double eHB_730 = 1.1022;
        private final double eHBO2_730 = 0.390;
        private final double eHB_850 = 0.69132;
        private final double eHBO2_850 = 1.058;
        private final double L = 0.015;

        //Full arrays that are initialized to input readings
        private ArrayList<Double> w730;
        private ArrayList<Double> w850;

        //Baseline values
        private Double baseline_730;
        private Double baseline_850;

        //Optical density arrays
        private ArrayList<Double> OD_730;
        private ArrayList<Double> OD_850;


        bvoxy(ArrayList<Double> temp730, ArrayList<Double> temp850) {

            w730 = temp730;
            w850 = temp850;

            OD_730 = new ArrayList<>();
            OD_850 = new ArrayList<>();

            HB = new ArrayList<>();
            HBO2 = new ArrayList<>();

            getBaseLine();
        }


        //Creating the baseline arrays from first X data samples
        private void getBaseLine() {

            Double sum730 = 0.0;
            Double sum850 = 0.0;

            for (int i = 0; i < w730.size(); ++i) {
                sum730 += w730.get(i);
                sum850 += w850.get(i);
            }

            baseline_730 = sum730 / 10;
            baseline_850 = sum850 / 10;
        }


        //Getting the oxygenated hemoglobin levels from voltages and adding to the given arraylist
        public void addHemoglobin(ArrayList<Double> HB, ArrayList<Double> HBO2, int i, Double reading_850, Double reading_730) {

            w850.add(reading_850);
            w730.add(reading_730);

            OD_730.add(-Math.log10(w730.get(i) / baseline_730));
            OD_850.add(-Math.log10(w850.get(i) / baseline_850));

            HB.add(((OD_850.get(i) * eHBO2_730) - (OD_730.get(i) * eHBO2_850)) / ((eHBO2_730 * eHB_850) - (eHBO2_850 * eHB_730)) / L);
            HBO2.add(((OD_730.get(i) * eHB_850) - (OD_850.get(i) * eHB_730)) / ((eHBO2_730 * eHB_850) - (eHBO2_850 * eHB_730)) / L);
        }
    }




    //TODO: fix this when hitting back while graphing
    //When the back button is pressed
    @Override
    public void onBackPressed(){
        if(mConnected && filledGraph) {
            clearGraph();
        }
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        finish();
    }



    //Sets up the progress dialog for creating the baseline test
    public void createBaselineProgress(){
        baselineProgress = new ProgressDialog(this);
        baselineProgress.setTitle("Creating Baseline...");
        baselineProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        baselineProgress.setMax(100);
        baselineProgress.setCancelable(false);
    }




    //TODO: is this necesary?
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            //initialize();
            //final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            //Log.d(TAG, "Connect request result=" + result);
        }
    }




    @Override
    protected void onPause() {
        super.onPause();
        clearGraph();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        finish();
    }
    //TODO: readings dont start at same time
    //TODO: wrong uuid


    /*
    //Re-initializes everything when on resume is called
    private void initialize(){

        initializeSeries();

        mTimer = new Timer();

        //Getting device name and address from intent
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        Log.d(TAG, "Name: " + mDeviceName + "   Address: " + mDeviceAddress);

        //Connecting to BLE services class
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }



    //TODO: get name of all available charcteristics and services
    //Finds the name of a service or characteristic based on a UUID
    public static class SampleGattAttributes {

        private static HashMap<String, String> attributes = new HashMap();

        static {
            // Sample Services.
            attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
            // Sample Characteristics.
            attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        }

        public static String lookup(String uuid, String defaultName) {
            String name = attributes.get(uuid);
            return name == null ? defaultName : name;
        }
    }*/
}