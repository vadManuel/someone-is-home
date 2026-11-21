package home.someoneshome.platform

/**
 * **The simulator: NEARBY DEVICES reads NOT ON THIS PHONE** (D-314), as its Bluetooth row does. It
 * has no ranging (D-308), so a probe could only ever say ASKED and keep every simulator bench at
 * PERMISSIONS. A bench accommodation: nothing about UWB or Nearby Interaction is signed off here.
 * This file is in the simulator's source set only: no build that installs on a phone contains it.
 */
internal actual fun nearbyDevicesAskedOnThisBuild(): Boolean = false
