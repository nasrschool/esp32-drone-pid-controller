# ESP32 Drone PID Controller

A Java desktop controller for tuning an ESP32 drone over Bluetooth. The Swing UI sends PID, thrust, and desired-angle values to an ESP32-compatible Bluetooth link.

## Features

- Bluetooth connection UI with configurable device address
- Live PID and desired-angle controls
- Manual thrust controls with configurable limits and step size
- Kill-switch ramp-down behavior and periodic state streaming
- Example connection and UI classes for experimentation

## Requirements

- JDK 23 (as configured in `pom.xml`)
- Maven 3.9+
- A paired ESP32 Bluetooth SPP device and matching firmware packet format
- A Bluetooth stack compatible with BlueCove or an equivalent serial-port setup

## Setup

1. Pair the ESP32 with the computer.
2. Build with `mvn package`.
3. Run `com.tp.maven.ESP32BluetoothPIDImproved`, optionally passing the Bluetooth address as the first argument.
4. Confirm the ESP32 firmware expects five little-endian floats: P, I, D, thrust, and desired angle.

## Limitations and safety

- BlueCove is legacy software and Bluetooth support is platform-dependent.
- This is an experimental desktop controller, not a complete flight-safety system. Test with props removed or on a secured test rig, and implement a firmware watchdog that zeros thrust after link loss.
