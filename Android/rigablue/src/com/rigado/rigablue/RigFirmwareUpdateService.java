package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import java.util.UUID;

/**
 * Created by stutzenbergere on 11/17/14.
 */
public class RigFirmwareUpdateService implements IRigLeConnectionManagerObserver, IRigLeBaseDeviceObserver{

    private static final String kLumenplayDFUServiceUuidString = "00001530-eb68-4181-a6df-42562b7fef98";
    private static final String kLumenplayDFUControlPointUuidString = "00001531-eb68-4181-a6df-42562b7fef98";
    private static final String kLumenplayDFUPacketCharUuidString = "00001532-eb68-4181-a6df-42562b7fef98";

    RigAvailableDeviceData mAvailDevice;
    RigLeBaseDevice mUpdateDevice;
    RigLeBaseDevice mInitialNonBootloaderDevice;
    String mUpdateDeviceAddress;

    BluetoothGattService mDfuService;
    BluetoothGattCharacteristic mControlPoint;
    BluetoothGattCharacteristic mPacketChar;

    IRigLeConnectionManagerObserver mOldConnectionObserver;
    IRigFirmwareUpdateServiceObserver mObserver;

    int mReconnectAttempts;
    boolean mShouldReconnectToDevice;
    boolean mAlwaysReconnectOnDisconnect;

    public RigFirmwareUpdateService() {
        mOldConnectionObserver = RigLeConnectionManager.getInstance().getObserver();
        RigLeConnectionManager.getInstance().setObserver(this);
        mReconnectAttempts = 0;
        mShouldReconnectToDevice = false;
    }

    public void setObserver(IRigFirmwareUpdateServiceObserver observer) {
        mObserver = observer;
    }

    public void setShouldReconnectState(boolean state) {
        mShouldReconnectToDevice = state;
    }

    public void setShouldAlwaysReconnectState(boolean state) {
        mAlwaysReconnectOnDisconnect = state;
    }

    public void connectDevice() {
        RigLog.d("__RigFirmwareUpdateService.connectDevice__");
        //TODO: need to acquire the device address here so that we can issue a new connection to the device
        RigLeConnectionManager.getInstance().connectDevice(mAvailDevice, 20000);
    }

    public void reconnectDevice() {
        RigLog.d("__RigFirmwareUpdateService.reconnectPeripheral__");
        mShouldReconnectToDevice = true;
        mReconnectAttempts = 0;
        RigLeConnectionManager.getInstance().disconnectDevice(mUpdateDevice);
    }

    public void setDevice(RigLeBaseDevice device) {
        /*TODO: Need to track the device address if we are not already in the bootloader that way
          after it's disconnect is finally registered by the system, we can inform the LumenplayManager
          instance of the disconnect which will provide consistency for the device list.
         */
        mUpdateDevice = device;
        mUpdateDevice.setObserver(this);
        mUpdateDeviceAddress = mUpdateDevice.getBluetoothDevice().getAddress();
        mAvailDevice = new RigAvailableDeviceData(mUpdateDevice.getBluetoothDevice(), 0, null, 0);
    }

    public void setInitialNonBootloaderDevice(RigLeBaseDevice device) {
        mInitialNonBootloaderDevice = device;
    }

    public void didDisconnectInitialNonBootloaderDevice() {
        if(mOldConnectionObserver != null) {
            mOldConnectionObserver.didDisconnectDevice(mInitialNonBootloaderDevice.getBluetoothDevice());
        }
    }

    public void triggerServiceDiscovery() {
        RigLog.d("__RigFirmwareUpdateService.triggerServiceDiscovery__");
        if(mUpdateDevice != null) {
            if(RigCoreBluetooth.getInstance().getDeviceConnectionState(mUpdateDevice.getBluetoothDevice()) ==
                    BluetoothProfile.STATE_CONNECTED) {
                mUpdateDevice.runDiscovery();
            } else {
                connectDevice();
            }
        }
    }

    public boolean getServiceAndCharacteristics() {
        UUID dfuServiceUuid = UUID.fromString(kLumenplayDFUServiceUuidString);
        UUID controlPointUuid = UUID.fromString(kLumenplayDFUControlPointUuidString);
        UUID packetUuid = UUID.fromString(kLumenplayDFUPacketCharUuidString);

        mDfuService = null;
        for(BluetoothGattService service : mUpdateDevice.getServiceList()) {
            if(service.getUuid().equals(dfuServiceUuid)) {
                mDfuService = service;
                break;
            }
        }

        if(mDfuService == null) {
            RigLog.e("Did not find Dfu Service!");
            return false;
        }

        for(BluetoothGattCharacteristic characteristic : mDfuService.getCharacteristics()) {
            if(characteristic.getUuid().equals(controlPointUuid)) {
                mControlPoint = characteristic;
            } else if(characteristic.getUuid().equals(packetUuid)) {
                mPacketChar = characteristic;
            }
        }

        if(mControlPoint == null || mPacketChar == null) {
            RigLog.e("One or more dfu characteristics missing!");
            return false;
        }

        return true;
    }

    public void disconnectDevice() {
        RigLeConnectionManager.getInstance().disconnectDevice(mUpdateDevice);
    }

    public void writeDataToControlPoint(byte[] data) {
        if(mUpdateDevice == null) {
            RigLog.e("Update device is null!");
            return;
        }

        if(mControlPoint == null) {
            RigLog.e("Dfu control point is null!");
            return;
        }

        if(data == null) {
            RigLog.e("Attempted to write null data to control point!");
            return;
        }

        mUpdateDevice.writeCharacteristic(mControlPoint, data);
    }

    public void writeDataToPacketCharacteristic(byte [] data) {
        if(mUpdateDevice == null) {
            RigLog.e("Update device is null!");
            return;
        }

        if(mPacketChar == null) {
            RigLog.e("Dfu packet characteristic is null!");
            return;
        }

        if(data == null) {
            RigLog.e("Attempted to write null data to packet characteristic!");
            return;
        }

        mUpdateDevice.writeCharacteristic(mPacketChar, data);
    }

    public void enableControlPointNotifications() {
        RigLog.d("__RigFirmwareUpdateService.enableControlPointNotifications__");

        if(mUpdateDevice == null) {
            RigLog.e("Update device is null!");
            return;
        }

        if(mControlPoint == null) {
            RigLog.e("Dfu control point is null!");
            return;
        }

        mUpdateDevice.setCharacteristicNotification(mControlPoint, true);
    }

    public void completeUpdate() {
        RigLog.d("__RigFirmwareUpdateService.completeUpdate__");
        RigLeConnectionManager.getInstance().setObserver(mOldConnectionObserver);
    }

    //IRigLeConnectionManagerObserver methods
    @Override
    public void didConnectDevice(RigLeBaseDevice device) {
        RigLog.d("__RigFirmwareUpdateService.didConnectDevice__");
        if(device.getBluetoothDevice().getAddress().equals(mUpdateDeviceAddress)) {
            mUpdateDevice = device;
            mUpdateDevice.setObserver(this);
            mUpdateDevice.runDiscovery();
        }
    }

    @Override
    public void didDisconnectDevice(BluetoothDevice btDevice) {
        RigLog.d("__RigFirmwareUpdateService.didDisconnectDevice__");
        if(btDevice.getAddress().equals(mUpdateDeviceAddress)) {
            if(mShouldReconnectToDevice || mAlwaysReconnectOnDisconnect) {
                RigLog.d("Reconnecting...");
                RigLeConnectionManager.getInstance().connectDevice(mAvailDevice, 20000);
            }
            mObserver.didDisconnectPeripheral();
        } else {
            /* This may be the orignal device disconnect.  Pass it on to the original connection
             * manager delegate.
             */
            mOldConnectionObserver.didDisconnectDevice(btDevice);
        }
    }

    @Override
    public void deviceConnectionDidFail(RigAvailableDeviceData device) {
        RigLog.d("__RigLeFirmwareUpdateService.deviceConnectionDidFail__");
        if(device.getBluetoothDevice().getAddress().equals(mUpdateDeviceAddress)) {
            if(mShouldReconnectToDevice || mAlwaysReconnectOnDisconnect) {
                RigLog.d("Reconnecting...");
                RigLeConnectionManager.getInstance().connectDevice(mAvailDevice, 20000);
            }
        }
    }

    @Override
    public void deviceConnectionDidTimeout(RigAvailableDeviceData device) {
        RigLog.d("__RigLeFirmwareUpdateService.deviceConnectionDidTimeout__");
        if(device.getBluetoothDevice().getAddress().equals(mUpdateDeviceAddress)) {
            if(mShouldReconnectToDevice || mAlwaysReconnectOnDisconnect) {
                RigLog.d("Reconnecting...");
                RigLeConnectionManager.getInstance().connectDevice(mAvailDevice, 20000);
            }
        }
    }

    //IRigLeBaseDeviceObserver methods
    @Override
    public void didUpdateValue(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(mControlPoint.getUuid())) {
            mObserver.didUpdateValueForControlPoint(characteristic.getValue());
        }
    }

    @Override
    public void didUpdateNotifyState(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(mControlPoint.getUuid())) {
           mObserver.didEnableControlPointNotifications();
        }
    }

    @Override
    public void didWriteValue(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(mControlPoint.getUuid())) {
            mObserver.didWriteValueForControlPoint();
        }
    }

    @Override
    public void discoveryDidComplete(RigLeBaseDevice device) {
        RigLog.d("__RigFirmwareUpdateService.discoveryDidComplete__");
        mUpdateDevice = device;
        if(!getServiceAndCharacteristics()) {
            RigLog.e("Connected to invalid device!");
            //TODO: Disconnect???
        }
        mUpdateDevice.setObserver(this);
        mObserver.didDiscoverCharacteristicsForDFUService();
    }


}