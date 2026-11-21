# Smart scale BLE protocol

Carried over from the reference project (`scale-ref`, `src/utils/scaleProtocol.ts` and
`src/hooks/useScaleBLE.ts`), whose UUIDs and layouts came from reverse engineering Zepp Life
(Xiaomi/Huami). The app's implementation is
`modules/scale/android/src/main/java/expo/modules/scale/Protocol.kt`; `ProtocolTest.kt` holds the
frames. Those frames are built from the layouts below, not yet captured from a scale: add captured
frames, with the scale's model, as they are found.

## Profiles

A scale speaks one of two GATT profiles. Body composition scales also offer the weight scale
service, so Body Composition wins when both are present.

| Profile          | Service | Measurement (notify or indicate) | Control | Start command    |
| ---------------- | ------- | -------------------------------- | ------- | ---------------- |
| Body Composition | `181B`  | `2A9C`                           | `2A2B`  | `06 0B 00 00`    |
| Weight Scale     | `181D`  | `2A9D`                           | `2A2B`  | `03 01 00 00 00` |

Full UUIDs are the Bluetooth base: `0000xxxx-0000-1000-8000-00805f9b34fb`. Huami's body
composition scales (Mi Body Composition Scale, Mi Scale 2, advertised as `MIBFS`) also advertise
their own service `FEE0`, as do Huami's bands and watches, so `FEE0` alone does not make a scale.

The start command is written with response to `2A2B` in the profile's service after subscribing.
The reference wrote it as "enter user mode" (body composition) or "reset" (weight scale). A scale
without the characteristic, or one that refuses the write, still sends weights.

## Finding a scale

The app scans without a scan filter. Android's service UUID filter quietly drops a device that has
just disconnected, and some scales name their service only in service data, so the app matches
in its own code: before a scale is paired, any device advertising `181B` or `181D` as a service or
service data; after, only the paired address. The first scale to connect is paired.

Scales sleep between weighings and wake when stepped on. The connection drops a little after each
weighing, and the app goes back to scanning.

## `2A9D` Weight Measurement

```
[0]     flags
          bit 0  pounds (0.01 lb steps)
          bit 1  timestamp present
          bit 2  user ID present
          bit 4  jin (0.01 斤 steps)          Xiaomi extension
          bit 5  settled                       Xiaomi extension
          bit 7  weight removed                Xiaomi extension
[1-2]   weight, uint16 LE: × 0.005 kg, or × 0.01 lb or jin
[3-9]   timestamp if bit 1: year uint16 LE, month, day, hour, minute, second
[10]    user ID if bit 2
```

`FF FF` as the weight is the standard's "measurement unsuccessful" and is skipped.

Bits 4–7 are reserved in the Bluetooth standard. A scale that follows the standard without Xiaomi's
extensions never sets the settled bit, so its weights show but never become a measurement. Not yet
handled: its frames look the same as a Mi Scale's unsettled ones.

## `2A9C` Body Composition Measurement (Huami)

At least 13 bytes:

```
[0]       flags 1
            bit 0  pounds
            bit 2  impedance present
[1]       flags 2
            bit 1  impedance measured
            bit 5  settled
            bit 6  jin
            bit 7  weight removed
[2-8]     timestamp: year uint16 LE, month, day, hour, minute, second
[9-10]    impedance in Ω, uint16 LE (the reference read it as a user ID)
[11-12]   weight, uint16 LE: × 0.005 kg, or × 0.01 lb or jin
```

`F0 FF` (and `FF FF`) as the weight is a placeholder and is skipped.

This is Huami's layout, not the Bluetooth standard's `2A9C`, where the weight is an optional field
after the body fat percentage. A standard body composition scale would be misread.

**Unit bits differ from the reference.** The reference read flags 1 bit 0 as jin and flags 2
bit 6 as pounds. The app follows the Bluetooth Body Composition specification, where flags bit 0
is the imperial unit, and openScale's Mi Scale 2 driver, which reads flags 2 bit 6 as jin. Both
read the weight the same way, so a scale set to kilograms is unaffected. Confirm on scales set to
pounds and to jin and record the result here.

## Timestamps

Readings are timed by the phone as they arrive. The scale's clock is often wrong (the reference
trusted it only from the year 2000 on), and a live reading is taken as it is sent.

## Weighings

`Weighing.kt` turns readings into measurements:

- A weighing runs from the first reading until one with the removed bit.
- Its first settled reading of 10 kg or more is the measurement; lighter ones are a bag or a pet.
- Repeats of the settled weight, including Huami's settled reading sent with the removed bit as
  the person steps off, are the same measurement.
- A settled weight that moves by 0.5 kg or more within one weighing is someone else: a new
  measurement.
- The measurement stays on screen after the weighing until the next one begins.

Each measurement is saved to Health Connect once, as a `WeightRecord` from a device of type
scale, with the client record id `weight-<milliseconds>`: saving the same measurement again
replaces it.
