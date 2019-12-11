
package com.reactlibrary;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.text.BoringLayout;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;

import org.json.JSONArray;


public class RNOtgStorageModule extends ReactContextBaseJavaModule {

    private String TAG = "Ishaan";
    private boolean DEBUG = true;
    private UsbManager mUsbManager;
    private List<UsbDevice> mDetectedDevices;
    private PendingIntent mPermissionIntent;

    private UsbMassStorageDevice mUsbMSDevice;
    private static final String ACTION_USB_PERMISSION = "com.github.mjdev.libaums.USB_PERMISSION";

    private final ReactApplicationContext reactContext;

    public RNOtgStorageModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        this.reactContext.registerReceiver(mUsbReceiver, filter);
        Log.d(TAG, "RNOtgStorageModule: mUsbReceiver");


        mPermissionIntent = PendingIntent.getBroadcast(this.reactContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mUsbManager = (UsbManager) this.reactContext.getSystemService(Context.USB_SERVICE);
        mDetectedDevices = new ArrayList<UsbDevice>();

//        checkUSBStatus();

    }


    @Override
    public String getName() {
        return "RNOtgStorage";
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
//            Log.d(TAG, "mUsbReceiver triggered. Action " + action);

            checkUSBStatus();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                removedUSB();
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                connectDevice();
            }

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {

                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
//                             Log.d(TAG, "onReceive: device EXTRA_PERMISSION_GRANTED");
                            openDevice();
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }

        }
    };

    private void removedUSB() {
        OTGDisconnected();
    }

    public void OTGDisconnected() {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onOTGDisconnected", "");
    }

    private void logger(String s) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("logger", s);
    }

    public void checkUSBStatus() {

        if (DEBUG)
            logger("checkUSBStatus");

        try {
            mDetectedDevices.clear();
            mUsbManager = (UsbManager) this.reactContext.getSystemService(Context.USB_SERVICE);

            if (mUsbManager != null) {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

                if (!deviceList.isEmpty()) {
                    Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
                    while (deviceIterator.hasNext()) {
                        UsbDevice device = deviceIterator.next();
                        mDetectedDevices.add(device);
                    }
                }

                if (mDetectedDevices.size() > 0) {
                    String deviceName;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        deviceName = (mDetectedDevices.get(0).getProductName());
                    } else {
                        deviceName = (mDetectedDevices.get(0).getDeviceName());
                    }

                    this.reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("newDeviceConnected", deviceName);
                }

            }
        } catch (Exception e) {
            this.reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("logger", e.toString());
        }

    }


    //    @ReactMethod
    public void openDevice() {
//        Log.d("Open 1","Connect If");
        if (mDetectedDevices.size() > 0) {
//            Log.d("Open 2","Connect If");
            UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this.reactContext);
            if (devices.length > 0) {
//                Log.d("Open 3","Connect If");
                mUsbMSDevice = devices[0];
            }
//            Log.d("Open 4","Connect If");
            WritableArray array = Arguments.createArray();


            try {

                mUsbMSDevice.init();
//                Log.d("Open 5","Connect If");

                FileSystem fs = mUsbMSDevice.getPartitions().get(0).getFileSystem();
//                Log.d("Open 8","" + mUsbMSDevice.getPartitions().get(0).getFileSystem());
                UsbFile root = fs.getRootDirectory();
                UsbFile[] files = root.listFiles();
                for (UsbFile file : files) {
                    if (!file.isDirectory()) {
//                        Log.d("Open 7",""+file.getName());
                        array.pushString(file.getName());
                    }
                }

            } catch (Exception e) {
                logger(e.toString());
//                Log.d("Open 6",""+e.getMessage());
            }

            WritableMap map = Arguments.createMap();
            map.putString("type", "success");
            map.putArray("directories", array);

//            p.resolve(map);


        } else {
            WritableMap map = Arguments.createMap();
            map.putString("type", "error");
            map.putString("message", "No Device Found");
//            p.resolve(map);
        }

    }

    //    @ReactMethod
    private void connectDevice() {

        if (mDetectedDevices.size() > 0) {
            String deviceName;
            String serialNumber;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                Log.d("come 1","Connect If");
                deviceName = (mDetectedDevices.get(0).getProductName());
                serialNumber = (mDetectedDevices.get(0).getSerialNumber());

            }
            else {
//                Log.d("come 2","connect else");
                deviceName = (mDetectedDevices.get(0).getDeviceName());
                serialNumber = (mDetectedDevices.get(0).getSerialNumber());

            }

            WritableMap map = Arguments.createMap();
            map.putString("type", "success");
            map.putString("deviceName", deviceName);
            map.putString("serialNumber", serialNumber);
            UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this.reactContext);
            mUsbManager.requestPermission(mDetectedDevices.get(0), mPermissionIntent);

//            p.resolve(map);

        } else {

            WritableMap map = Arguments.createMap();
            map.putString("type", "error");
            map.putString("message", "No Device Found");
//            p.resolve(map);

        }


    }

    private String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    @ReactMethod
    public void openRootFolder(String folderName, Callback callback) {

        try {
            WritableArray array = Arguments.createArray();
            FileSystem fs = mUsbMSDevice.getPartitions().get(0).getFileSystem();
            UsbFile root = fs.getRootDirectory();
            UsbFile[] files = root.listFiles();

            for (UsbFile file : files) {

                if (!file.isDirectory()) {

                    if (file.getName().equals(folderName)) {
                        UsbFile[] x = file.listFiles();
                        for (UsbFile y : x) {
                            if (!y.isDirectory()) {
                                array.pushString(y.getName());
                            }
                        }
                    }
                }
            }

            callback.invoke(array);


        } catch (Exception e) {
            Log.d(TAG, "openRootFolder: error "+e.getMessage());
        }


    }

    @ReactMethod
    public void openRootFolderFile(String folderName, String fileName, Callback callback) {
//        Log.d(TAG, "openRootFolder: 1");
        try {
            if(mUsbMSDevice == null){
                callback.invoke("Permission not granted");
                return;
            }

            FileSystem fs = mUsbMSDevice.getPartitions().get(0).getFileSystem();
            UsbFile root = fs.getRootDirectory();
            UsbFile[] files = root.listFiles();
//            Log.d(TAG, "openRootFolder: "+files.length);
            Boolean worked = false;

            for (UsbFile file : files) {
                if (!file.isDirectory()) {
//                    Log.d("Open11111 ---",""+file.getName()+" -- "+folderName);
                    if (file.getName().equals(fileName)) {
//                        Log.d("Open >>>>>>",""+file.getName()+" -- "+folderName);

                        InputStream is = new UsbFileInputStream(file);
                        byte[] buffer = new byte[fs.getChunkSize()];
                        String response = convertStreamToString(is);
                        callback.invoke(response);
                        worked = true;
//                        UsbFile[] x = file.listFiles();
//                        for (UsbFile y : x) {
//                            if (!y.isDirectory()) {
//                                if (y.getName().equals(fileName)) {
//
//
//                                }
//
//                            } else {
//                                callback.invoke("error@ Error some here");
//                            }
//                        }
                    }
                }
            }


            if (worked.equals(false)) {
                callback.invoke("File Not Found");
            }


        } catch (Exception e) {
            callback.invoke("File Not Found 1" + e.getLocalizedMessage());
        }


    }

    @ReactMethod
    public void udpateOrCreateRootFolderFile(String folderName, String fileName, String content, Callback callback) {


        try {

            FileSystem fs = mUsbMSDevice.getPartitions().get(0).getFileSystem();
            UsbFile root = fs.getRootDirectory();
            UsbFile[] files = root.listFiles();
            Boolean worked = false;

            for (UsbFile file : files) {
                if (file.isDirectory()) {
                    if (file.getName().equals(folderName)) {
                        UsbFile[] x = file.listFiles();

                        for (UsbFile y : x) {
                            if (!y.isDirectory()) {

                                if (y.getName().equals(fileName)) {

                                    y.delete();
                                    UsbFile cv = file.createFile(fileName);
                                    OutputStream os = new UsbFileOutputStream(cv);
                                    os.write(content.getBytes());
                                    os.close();
                                    worked = true;
                                    callback.invoke("success@updated");
                                }

                            } else {
                                callback.invoke("error@ cant open folder ");
                            }
                        }


                        if (worked.equals(false)) {
                            UsbFile cv = file.createFile(fileName);
                            OutputStream os = new UsbFileOutputStream(cv);
                            os.write(content.getBytes());
                            os.close();
                            callback.invoke("success@created");
                        }

                    }
                }
            }

        } catch (Exception e) {
            callback.invoke("error@" + e.toString());
        }
    }
}
