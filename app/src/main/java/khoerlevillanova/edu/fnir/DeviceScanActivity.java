package khoerlevillanova.edu.fnir;


/*
This activity begins searching for new Bluetooth Low Energy devices upon
creation. When a device is found, it will be added to the device list on the UI.
Upon clicking on one of these devices, a new acticty will be created to handle the
device
 */


import android.Manifest;
import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static khoerlevillanova.edu.fnir.DeviceControlActivity.EXTRAS_DEVICE_ADDRESS;
import static khoerlevillanova.edu.fnir.DeviceControlActivity.EXTRAS_DEVICE_NAME;




public class DeviceScanActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{


    //Constants
    private final String TAG = "DeviceScanActivity";
    private final int SCAN_PERIOD = 7000;

    //UI variables
    private ListView lvDevices;
    private DeviceListAdapter mDeviceListAdapter;
    private Switch scanSwitch;

    //Bluetooth Variables
    private BluetoothAdapter mBluetoothAdapter;
    private BtleScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();
    private BluetoothDevice mBTDevice;
    private boolean mScanning = false;      //Set scanning to false initially
    private HashMap<String, BluetoothDevice> mScanResults;
    private Handler mHandler;




    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);

        //Creating a back button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Creating bluetooth adapter
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        //Turning on bluetooth and checking permissions
        enableBT();

        //Checking if device has bluetooth LE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || !enableBT()) {
            finish();
        }

        //Creating list views for discovered devices
        lvDevices = findViewById(R.id.lvNewDevices);
        lvDevices.setOnItemClickListener(DeviceScanActivity.this);

        scanSwitch = findViewById(R.id.scanSwitch);
        scanSwitch.setChecked(true);

        //If the switch is on, the app will scan for devices, if it is off
        //the app will stop scanning
        scanSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(scanSwitch.isChecked())
                    startScan();
                else
                    stopScan();
            }

        });

        startScan();
    }




    //Method for turning bluetooth on and checking permissions, called when the app is first opened
    public boolean enableBT() {

        final int REQUEST_ENABLE_BT = 1;

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Log.d(TAG, "Bluetooth is now on.");
            return false;

        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }

        return true;
    }




    //Checking location permissions
    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }




    //Granting location permissions
    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }




    //Scanning for Bluetooth Low Energy devices
    private void startScan() {

        if (!enableBT() || mScanning) {
            return;
        }

        Toast.makeText(DeviceScanActivity.this, "Scanning...", Toast.LENGTH_LONG).show();

        //Handler to stop scanning after set amount of time
        mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanSwitch.setChecked(false);
                Log.d(TAG, "Scan stopped");
                stopScan();
            }
        }, SCAN_PERIOD);

        //Begin searching for devices
        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        mScanResults = new HashMap<>();
        mScanCallback = new BtleScanCallback();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mScanning = true;
        Log.d(TAG, "Scan started");
    }




    //Class to handle scan results
    private class BtleScanCallback extends ScanCallback {

        //Is true if the incoming device is already on the list
        private boolean duplicate;

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed with code " + errorCode);
        }

        //Add found devices to UI
        private void addScanResult(ScanResult result) {

            //New bluetooth device
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();

            for (String key : mScanResults.keySet()) {
                if(key.equals(deviceAddress))
                    duplicate = true;
            }

            //Removing any NULL devices and any duplicate devices
            if(device.getName() != null && !duplicate) {
                mScanResults.put(deviceAddress, device);
                mBTDevices.add(device);
                mDeviceListAdapter = new DeviceListAdapter(DeviceScanActivity.this, R.layout.device_adapter_view, mBTDevices);
                lvDevices.setAdapter(mDeviceListAdapter);
            }
        }
    }




    //Stopping the scan
    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
        mHandler = null;

        //Refreshing the options menu
        invalidateOptionsMenu();
    }




    //What to do after the scan
    private void scanComplete() {
        if (mScanResults.isEmpty()) {
            return;
        }
        for (String deviceAddress : mScanResults.keySet()) {
            Log.d(TAG, "Found device: " + deviceAddress);
        }
    }




    //When an item on the list is clicked, open control activity and connect to device
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

        //first cancel discovery because its very memory intensive.
        stopScan();

        //Set the clicked device to your device
        mBTDevice = mBTDevices.get(i);
        String deviceName = mBTDevices.get(i).getName();
        String deviceAdress = mBTDevices.get(i).getAddress();

        //If the device is null, stop; prevents empty devices from showing up
        if (mBTDevice == null || deviceAdress == null || deviceName == null) {
            Log.d(TAG, "Device is NULL");
            Toast.makeText(DeviceScanActivity.this, "This device does not exist", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "onItemClick: You Clicked on a device." + deviceAdress + deviceName);

        //Adding device name and address to intent
        Intent intent = new Intent(DeviceScanActivity.this, DeviceControlActivity.class);
        intent.putExtra(EXTRAS_DEVICE_NAME, deviceName);
        intent.putExtra(EXTRAS_DEVICE_ADDRESS, deviceAdress);
        startActivity(intent);
    }
}

