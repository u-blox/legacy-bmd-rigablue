# Overview
The goal of Rigablue is to provide an easy to use library for Bluetooth Low
Energy (BLE) device integration.  Rigablue provides classes which abstract away
many of the low level details required for typical BLE operations: scanning,
connecting, and communications.

## Rigablue Architecture

The main tenant of the current Rigablue architecture is the Observer pattern.
This pattern meets the needs of a sychronous data flow but also provides for
asynchronous data recpetion as needed.  While not the best choice of designs
for modern iOS and Android development, it is the overarching pattern for
the current architecture.

Another common design tenant are the manager classes.  The classes follow a 
singleton design. The manager classes provide the ability to scan for BLE 
devices and connect with them.  These operations are provided by the Discovery 
manager and Connection manager respectively.  As with use of the observer 
pattern, this may not be the best choice for modern application design.

# Scanning

To begin scanning for a BLE device, a request object is first created.  The
request object holds scanning parameters such as Service UUIDs to search for,
amount of time to run the scan, and other OS specific parameters.

After kicking off a discovery request, the request parameters will be supplied
to the low level bluetooth stack.  The discovery request can result in a few
different callbacks: didDiscoverDevice, discoveryDidTimeout, 
bluetoothPowerStateChanged, bluetoothNotSupported.

As each discovery report is returned, the device information is coalesced into
an available device object.  This object contains fields such as device name,
address or UUID (on iOS), advertising data, RSSI, and a timestamp of discovery.
This object is then used to initiate connections via the connection manager.

WIP