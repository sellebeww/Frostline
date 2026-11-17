# ESP32 ESS test sensor

A small Arduino sketch (NimBLE) that makes an ESP32 behave like the BLE temperature sensor Frostline expects: Environmental Sensing Service `0x181A`, Temperature `0x2A6E` (sint16, 0.01 degC, read and notify), Humidity `0x2A6F` (uint16, 0.01 %).

**Status: not tested on hardware.** The sketch has not been run on an ESP32 with a phone yet. Expect to fix small things (library version differences in the NimBLE API, for example). The first real test is item 1 in [`docs/HARDWARE_TEST.md`](../../docs/HARDWARE_TEST.md).

## Use

1. Install the Arduino IDE (or arduino-cli) with ESP32 board support.
2. Install the library **NimBLE-Arduino** (h2zero) from the Library Manager.
3. Open `esp32-ess-sensor.ino`, select your ESP32 board and upload.
4. The device advertises as `Frostline-ESS`. The temperature is a sine wave between 1 and 9 degC with a 10 minute period, notified every 10 seconds, so it crosses the 2 and 8 degC limits.

## Real probe (optional)

Uncomment `USE_DS18B20`, install `OneWire` and `DallasTemperature`, and wire a DS18B20: data on GPIO 4 (`DS18B20_PIN`) with a 4.7 kOhm pull-up to 3V3, VCC to 3V3, GND to GND.

## To test a failing sensor

Rename the service UUID `"181A"` in the sketch to anything else. Frostline must then show that the sensor does not support ESS instead of crashing.
