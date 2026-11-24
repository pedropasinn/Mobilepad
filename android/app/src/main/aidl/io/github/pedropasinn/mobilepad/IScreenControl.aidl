package io.github.pedropasinn.mobilepad;

// Runs inside a Shizuku UserService (shell uid). Transaction 16777114 is the
// destroy code Shizuku expects.
interface IScreenControl {
    void destroy() = 16777114;

    // Confirms a pending Bluetooth pairing request (needs BLUETOOTH_PRIVILEGED, which the
    // shell has). Used to pair with the Switch, which gives up within a few seconds.
    boolean confirmPairing(String address) = 3;

    // Sets the Bluetooth class of device; returns the previous value, or -1 on failure.
    int setDeviceClass(int deviceClass) = 4;
}
