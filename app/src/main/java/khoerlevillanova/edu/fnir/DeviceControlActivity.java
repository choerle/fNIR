package khoerlevillanova.edu.fnir;


/*
This is the main activity for controlling and reading from the BLE device
This activity is created when an item in the device scan activity is clicked.
Upon creation, the GATT server will automatically be connected to. Once
connected, there is a menu to chose what to do with the device.
1. Read voltages and then plot them
2. Disconnect from GATT
3. Look up other services and characteristics
 */


import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
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
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    //UI variables
    private TextView dataField;
    private ExpandableListView mGattServicesList;
    private GraphView dataGraph;

    //Graphing variables
    private int count = 0;
    private int time = 0;
    private int samplingRate = 1000; //In milliseconds
    private LineGraphSeries<DataPoint> series_730;
    private LineGraphSeries<DataPoint> series_850;
    private getDataClass mGetDataClass;
    private Timer mTimer;

    //Bluetooth variables
    private boolean servicesAvailable = false; //if true services have been discovered
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList();
    private BluetoothGattCharacteristic mNotifyCharacteristic;

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
        mGattServicesList = findViewById(R.id.lvExp);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        dataField = findViewById(R.id.dataField);

        //Graphing initialization
        dataGraph = findViewById(R.id.dataGraph);

        dataGraph.getViewport().setYAxisBoundsManual(true);
        dataGraph.getViewport().setMinY(0);
        dataGraph.getViewport().setMaxY(3000);
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
        dataGraph.setTitle("Raw fNIR Data");
        dataGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time (Seconds)");
        dataGraph.getGridLabelRenderer().setVerticalAxisTitle("Intensity (Voltage)");

        initializeSeries();
        dataGraph.addSeries(series_730);
        dataGraph.addSeries(series_850);

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




    //TODO: is a disconnect button neccesary?
    //Options menu for connecting and disconnecting from device
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);

        //Determining what buttons should be available when connected to a device
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            //menu.findItem(R.id.menu_disconnect).setVisible(true);

            //Determining what buttons should be available when the app is graphing
            if (graphing) {
                menu.findItem(R.id.startData).setVisible(false);
                menu.findItem(R.id.stopData).setVisible(true);

            } else if(!graphing) {
                menu.findItem(R.id.startData).setVisible(true);
                menu.findItem(R.id.stopData).setVisible(false);
            }

            //Set clear graph to only be shown when the graph has data
            if(filledGraph)
                menu.findItem(R.id.clearGraph).setVisible(true);
            else if(!filledGraph)
                menu.findItem(R.id.clearGraph).setVisible(false);

            //If there are services that can be read
            if(servicesAvailable)
                menu.findItem(R.id.displayServices).setVisible(true);
            else if (!servicesAvailable)
                menu.findItem(R.id.displayServices).setVisible(false);
        }

        //When disconnected, the only button should be to connect
        else if(!mConnected){
            menu.findItem(R.id.menu_connect).setVisible(true);
            //menu.findItem(R.id.menu_disconnect).setVisible(false);
            menu.findItem(R.id.displayServices).setVisible(false);
            menu.findItem(R.id.startData).setVisible(false);
            menu.findItem(R.id.stopData).setVisible(false);
            menu.findItem(R.id.clearGraph).setVisible(false);
        }
        return true;
    }




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

            //Disconnect device and stop collecting data
            /*case R.id.menu_disconnect:
                if (mConnected) {
                    Log.d(TAG, "Menu item disconnect: trying to disconnect");
                    stopDataCollection();
                    mBluetoothLeService.disconnect();
                    mBluetoothLeService.close();
                }
                return true;*/

            //Begins collection and graphing of data
            case R.id.startData:
                if(!graphing && mConnected) {
                    Log.d(TAG, "Menu item startData: trying to startData");
                    //Continue data collection with the old graph
                    if(filledGraph) {
                        mGetDataClass = new getDataClass();
                        mTimer = new Timer();
                    }
                    mTimer.schedule(mGetDataClass, 0, samplingRate);
                    invalidateOptionsMenu();
                }
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

            //Display available services
            case R.id.displayServices:
                if(servicesAvailable) {
                    Log.d(TAG, "Menu item displayServices: trying to displayServices");
                    displayGattServices(mBluetoothLeService.getSupportedGattServices());
                }
                return true;

            //Return home
            case R.id.returnTo:
                Log.d(TAG, "Returning home");
                Intent i = new Intent(DeviceControlActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                return true;
        }

        return super.onOptionsItemSelected(item);
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
                displayChractersiticInfo(data);
            }
        }
    };




    //Class to get data every X seconds and then plot the data
    class getDataClass extends TimerTask {

        public void run(){
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    getData();
                }
            });
        }


        //Displays the data value on the UI and graphs it
        private void getData() {

            if (mBluetoothLeService.readVoltages() != null) {

                filledGraph = true;
                graphing = true;
                invalidateOptionsMenu();

                Log.d(TAG, "Reading data");

                //Determines when the data sample is taken, in seconds
                time = count*samplingRate/1000;
                dataField.setText(String.valueOf(time));

                String data = mBluetoothLeService.readVoltages();

                //730 wavelength
                String data_730_1 = data.substring(0,2);
                String data_730_2 = data.substring(3,5);
                String data_730s = new StringBuilder().append(data_730_1).append(data_730_2).toString();
                double data_730 = getDecimal(data_730s);
                graphData(data_730, series_730);

                //850 wavelength
                String data_850_1 = data.substring(6,8);
                String data_850_2 = data.substring(9,11);
                String data_850s = new StringBuilder().append(data_850_1).append(data_850_2).toString();
                double data_850 = getDecimal(data_850s);
                graphData(data_850, series_850);

                Log.d(TAG, data_730s + "           " + String.valueOf(data_730));

                //Increment count in order to increment time
                ++count;
            }
        }


        //TODO: is this way of graphing slow?
        //Graphs data by adding to old series and plotting it
        public void graphData(double value, LineGraphSeries<DataPoint> series){
            DataPoint dataPoint = new DataPoint(time, value);
            series.appendData(dataPoint, true,5000);
            dataGraph.getViewport().setMinX(0);
            dataGraph.getViewport().setMaxX(time++);
        }
    }




    //Stops graphing and collecting data
    public void stopDataCollection(){
        graphing = false;
        Log.d(TAG, "Graphing stopped");
        invalidateOptionsMenu();
        mTimer.cancel();
        mTimer.purge();
    }




    //Clears when the graph is long clicked, first stops data collection then creates new series
    public void clearGraph(){
        filledGraph = false;
        dataField.setText("...");
        Log.d(TAG, "Graph cleared");
        stopDataCollection();
        initializeSeries();
    }




    //Creates 2 new series for the graph
    public void initializeSeries(){

        //Resetting the time for incoming data
        count = 0;
        time = 0;

        dataGraph.removeAllSeries();

        //730 wavelength series initialization
        series_730 = new LineGraphSeries<>();
        series_730.setColor(Color.BLUE);
        dataGraph.addSeries(series_730);

        //850 wavelength series initialization
        series_850 = new LineGraphSeries<>();
        series_850.setColor(Color.RED);
        dataGraph.addSeries(series_850);

        if(series_850 != null && series_730 != null)
            Log.d(TAG, "Series initialized");

        mTimer = new Timer();
        mGetDataClass = new getDataClass();
    }




    // Populates expandable list view with GATT services found
    // The child views are the characteristics of each service
    private void displayGattServices(List<BluetoothGattService> gattServices) {

        if (gattServices == null)
            return;

        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services
        for (BluetoothGattService gattService : gattServices) {

            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }

            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );

        mGattServicesList.setAdapter(gattServiceAdapter);
    }




    // If a given GATT characteristic is selected, get its information and display in text view
    private final ExpandableListView.OnChildClickListener servicesListClickListner = new ExpandableListView.OnChildClickListener() {

        public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                    int childPosition, long id) {

            if (mGattCharacteristics != null) {

                final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();

                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    if (mNotifyCharacteristic != null) {
                        mBluetoothLeService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
                    mBluetoothLeService.readCharacteristic(characteristic);
                }

                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                            characteristic, true);
                }
                return true;
            }
            return false;
        }
    };




    //Displays the characteristic info on the UI
    public void displayChractersiticInfo(String data){
        if (data != null) {
            //displayCharacterstic.setText(data);
        }
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
    }



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





    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            initialize();
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }




    @Override
    protected void onPause() {
        super.onPause();
        clearGraph();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
}