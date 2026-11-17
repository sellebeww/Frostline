// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
//
// Test sensor for Frostline: an ESP32 that advertises the BLE Environmental Sensing Service (0x181A)
// with a Temperature characteristic (0x2A6E, sint16 little-endian, 0.01 degC, read + notify) and an
// optional Humidity characteristic (0x2A6F, uint16 little-endian, 0.01 %).
//
// NOT TESTED ON HARDWARE yet. Treat it as a starting point.
//
// Library: "NimBLE-Arduino" by h2zero (Library Manager). Board: any ESP32 (classic or S3/C3).
//
// The temperature is a sine wave between 1 and 9 degC with a 10 minute period, so it crosses the 2 and 8 degC
// limits and lets you see excursion alarms. To use a real DS18B20 instead, see USE_DS18B20 below.

#include <NimBLEDevice.h>

// #define USE_DS18B20          // uncomment, and install "OneWire" and "DallasTemperature"
#define DS18B20_PIN 4           // data pin of the DS18B20 (4.7 kOhm pull-up to 3V3)

#ifdef USE_DS18B20
#include <OneWire.h>
#include <DallasTemperature.h>
OneWire oneWire(DS18B20_PIN);
DallasTemperature ds18b20(&oneWire);
#endif

static const uint32_t NOTIFY_PERIOD_MS = 10000;  // how often a new value is notified
static const float SINE_MIN_C = 1.0f;
static const float SINE_MAX_C = 9.0f;
static const float SINE_PERIOD_S = 600.0f;

NimBLECharacteristic *temperatureChar;
NimBLECharacteristic *humidityChar;

float readTemperatureC() {
#ifdef USE_DS18B20
  ds18b20.requestTemperatures();
  return ds18b20.getTempCByIndex(0);
#else
  float mid = (SINE_MIN_C + SINE_MAX_C) / 2.0f;
  float amp = (SINE_MAX_C - SINE_MIN_C) / 2.0f;
  return mid + amp * sinf(2.0f * PI * (millis() / 1000.0f) / SINE_PERIOD_S);
#endif
}

void setup() {
  NimBLEDevice::init("Frostline-ESS");
  NimBLEServer *server = NimBLEDevice::createServer();
  NimBLEService *ess = server->createService("181A");
  temperatureChar = ess->createCharacteristic("2A6E", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  humidityChar = ess->createCharacteristic("2A6F", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  ess->start();

  NimBLEAdvertising *adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID("181A");  // the app filters its scan on this UUID
  adv->setName("Frostline-ESS");
  adv->start();
#ifdef USE_DS18B20
  ds18b20.begin();
#endif
}

void loop() {
  float t = readTemperatureC();
  // 0x8000 means "unknown" in ESS; the app ignores it. Send it when the probe fails.
  int16_t rawTemp = (t < -100.0f || t > 100.0f) ? (int16_t)0x8000 : (int16_t)lroundf(t * 100.0f);
  uint16_t rawHum = 5500;  // fixed 55.00 % (no humidity sensor)

  // sint16 / uint16, little-endian (the ESP32 is little-endian, so the in-memory layout is already right)
  temperatureChar->setValue((uint8_t *)&rawTemp, sizeof(rawTemp));
  humidityChar->setValue((uint8_t *)&rawHum, sizeof(rawHum));
  temperatureChar->notify();
  humidityChar->notify();
  delay(NOTIFY_PERIOD_MS);
}
