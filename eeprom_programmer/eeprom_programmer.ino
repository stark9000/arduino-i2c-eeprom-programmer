// ============================================================================
//  EEPROM Programmer - Arduino firmware v2
//  Board : Arduino Nano (ATmega328P, CH340 USB)
//  Chips : 24C01 ... 24C512, 24CM01, 24CM02 (I2C)
//
//  Wiring: SDA -> A4, SCL -> A5, VCC -> 5V, GND -> GND,
//          A0/A1/A2 -> GND, WP -> GND (write enabled).
//          4.7k pull-ups on SDA/SCL if your module has none.
//
//  Serial: 115200 baud, one command per line ('\n'), all numbers in HEX.
//
//    V                       -> VER:2
//    P                       -> PONG
//    T:<size>                -> OK            select chip by size in bytes
//    S                       -> SIZE:<size>   auto-detect (writes 2 bytes, restores them)
//                               or ERR:NOCHIP
//    R:<addr>:<len>          -> D:<hexdata>:<sum>        len 1..64
//    W:<addr>:<hexdata>:<sum>-> OK | ERR:CHK | ERR:I2C | ERR:VERIFY:<addr>
//                               (page write + read-back verify, len 1..64)
//    B:<addr>:<len>:<val>    -> OK | NE:<addr> | ERR:I2C  (blank check)
//    F:<addr>:<len>:<val>    -> OK | ERR:I2C | ERR:VERIFY:<addr>  (fill/erase)
//    C:<addr>:<len>:<init>   -> CRC:<crc16>   CRC-16/CCITT, init usually FFFF
//
//    <sum> = 8-bit sum of the data bytes, 2 hex digits.
//    Other errors: ERR:NOCFG (no T/S yet), ERR:RANGE, ERR:PARSE, ERR:UNKNOWN
// ============================================================================

#include <Wire.h>

#define FW_VERSION  2
#define BASE_ADDR   0x50     // 1010 A2 A1 A0 with A0..A2 = GND
#define LINE_MAX    200
#define MAX_DATA    64       // max bytes per R / W command
#define WIRE_CHUNK  16       // bytes per I2C transaction (AVR Wire buffer = 32)

static uint32_t chipSize  = 0;
static uint8_t  addrBytes = 2;
static uint16_t pageSize  = 32;

static char    line[LINE_MAX + 1];
static uint8_t lineLen  = 0;
static bool    overflow = false;

// ---------------------------------------------------------------- chip setup

static void configure(uint32_t size) {
  chipSize  = size;
  addrBytes = (size <= 2048UL) ? 1 : 2;           // 24C01..24C16 use 1 address byte
  if      (size <= 256UL)   pageSize = 8;          // 24C01, 24C02
  else if (size <= 2048UL)  pageSize = 16;         // 24C04, 24C08, 24C16
  else if (size <= 8192UL)  pageSize = 32;         // 24C32, 24C64
  else if (size <= 32768UL) pageSize = 64;         // 24C128, 24C256
  else if (size <= 65536UL) pageSize = 128;        // 24C512
  else                      pageSize = 256;        // 24CM01, 24CM02
}

// Device address for a memory address.
//  1-byte chips: A8..A10 go into the device address (24C04/08/16 blocks)
//  2-byte chips: A16..A17 go into the device address (24CM01/24CM02)
static uint8_t devFor(uint32_t addr) {
  if (addrBytes == 1) return BASE_ADDR | ((addr >> 8) & 0x07);
  return BASE_ADDR | ((addr >> 16) & 0x03);
}

static bool devAck(uint8_t dev) {
  Wire.beginTransmission(dev);
  return Wire.endTransmission() == 0;
}

// ACK polling: the chip NACKs while its internal write cycle runs.
static bool waitReady(uint8_t dev) {
  unsigned long t0 = millis();
  do {
    if (devAck(dev)) return true;
  } while (millis() - t0 < 25);
  return false;
}

// ---------------------------------------------------------------- raw I2C
// ab = number of address bytes to send (1 or 2).
// Reads use a repeated start (no STOP after the address), so a read can
// never turn into a write - even if the address width is wrong.

static bool rawRead(uint8_t dev, uint8_t ab, uint16_t a, uint8_t *buf, uint8_t n) {
  Wire.beginTransmission(dev);
  if (ab == 2) Wire.write((uint8_t)(a >> 8));
  Wire.write((uint8_t)a);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(dev, n) != n) {
    while (Wire.available()) Wire.read();
    return false;
  }
  for (uint8_t i = 0; i < n; i++) buf[i] = Wire.read();
  return true;
}

static bool rawWrite(uint8_t dev, uint8_t ab, uint16_t a, const uint8_t *buf, uint8_t n) {
  Wire.beginTransmission(dev);
  if (ab == 2) Wire.write((uint8_t)(a >> 8));
  Wire.write((uint8_t)a);
  Wire.write(buf, n);
  if (Wire.endTransmission() != 0) return false;
  return waitReady(dev);
}

// ---------------------------------------------------------------- memory ops

static bool readMem(uint32_t addr, uint8_t *buf, uint16_t n) {
  while (n) {
    uint8_t c = WIRE_CHUNK - (addr % WIRE_CHUNK);   // never crosses a 16-byte line
    if (c > n) c = n;
    if (!rawRead(devFor(addr), addrBytes, (uint16_t)addr, buf, c)) return false;
    addr += c; buf += c; n -= c;
  }
  return true;
}

static bool writeMem(uint32_t addr, const uint8_t *buf, uint16_t n) {
  while (n) {
    uint16_t room = pageSize - (addr % pageSize);   // never crosses a page
    uint8_t c = WIRE_CHUNK;
    if (room < c) c = room;
    if (n < c)    c = n;
    if (!rawWrite(devFor(addr), addrBytes, (uint16_t)addr, buf, c)) return false;
    addr += c; buf += c; n -= c;
  }
  return true;
}

// ---------------------------------------------------------------- auto-detect
// Writes a 2-byte marker with 2-byte addressing at 0x005A and looks where it
// landed. Everything touched is saved first and restored afterwards.
//   1-byte chip -> bytes 0..2 become 5A A5 3C   (the "address low" byte is data)
//   2-byte chip -> bytes 005A..005B become A5 3C
// Size: 1-byte chips by which block addresses ACK (+ wrap test for 24C01),
//       2-byte chips by address wrap-around, then 0x51/0x52 for 24CM01/02.

static uint32_t detectSize() {
  if (!devAck(BASE_ADDR)) return 0;

  const uint8_t mark[2] = { 0xA5, 0x3C };
  uint8_t s1[3], s2[2], t[3];

  if (!rawRead(BASE_ADDR, 1, 0x00, s1, 3)) return 0;     // valid if 1-byte chip
  if (!rawRead(BASE_ADDR, 2, 0x5A, s2, 2)) return 0;     // valid if 2-byte chip
  if (!rawWrite(BASE_ADDR, 2, 0x5A, mark, 2)) return 0;

  uint32_t size = 0;
  bool oneByte = rawRead(BASE_ADDR, 1, 0x00, t, 3)
                 && t[0] == 0x5A && t[1] == 0xA5 && t[2] == 0x3C;

  if (oneByte) {
    if      (devAck(BASE_ADDR | 4)) size = 2048;          // 24C16 (blocks 0..7)
    else if (devAck(BASE_ADDR | 2)) size = 1024;          // 24C08 (blocks 0..3)
    else if (devAck(BASE_ADDR | 1)) size = 512;           // 24C04 (blocks 0..1)
    else {
      // 24C01 has 7 address bits: 0x80 wraps back to 0x00
      size = (rawRead(BASE_ADDR, 1, 0x80, t, 3)
              && t[0] == 0x5A && t[1] == 0xA5 && t[2] == 0x3C) ? 128 : 256;
    }
    rawWrite(BASE_ADDR, 1, 0x00, s1, 3);                  // restore
  } else {
    if (rawRead(BASE_ADDR, 2, 0x5A, t, 2) && t[0] == mark[0] && t[1] == mark[1]) {
      for (uint32_t n = 4096; n <= 32768UL; n <<= 1) {
        if (rawRead(BASE_ADDR, 2, (uint16_t)(n + 0x5A), t, 2)
            && t[0] == mark[0] && t[1] == mark[1]) { size = n; break; }
      }
      if (size == 0) {
        if (!devAck(BASE_ADDR | 1))      size = 65536UL;   // 24C512
        else if (!devAck(BASE_ADDR | 2)) size = 131072UL;  // 24CM01
        else                             size = 262144UL;  // 24CM02
      }
    }
    rawWrite(BASE_ADDR, 2, 0x5A, s2, 2);                  // restore
  }
  return size;
}

// ---------------------------------------------------------------- helpers

static uint16_t crcUpdate(uint16_t crc, uint8_t b) {
  crc ^= (uint16_t)b << 8;
  for (uint8_t i = 0; i < 8; i++) crc = (crc & 0x8000) ? (crc << 1) ^ 0x1021 : (crc << 1);
  return crc;
}

static int8_t hexNibble(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return -1;
}

static bool parseHex32(const char *s, uint32_t &out) {
  if (!s || !*s) return false;
  uint32_t v = 0;
  for (; *s; s++) {
    int8_t n = hexNibble(*s);
    if (n < 0) return false;
    v = (v << 4) | (uint8_t)n;
  }
  out = v;
  return true;
}

static void printHex8(uint8_t v) {
  const char *h = "0123456789ABCDEF";
  Serial.write(h[v >> 4]);
  Serial.write(h[v & 15]);
}

static void printHex32(uint32_t v) {
  bool started = false;
  for (int8_t s = 28; s >= 0; s -= 4) {
    uint8_t n = (v >> s) & 15;
    if (n || started || s == 0) { Serial.write("0123456789ABCDEF"[n]); started = true; }
  }
}

static bool inRange(uint32_t addr, uint32_t len) {
  return len > 0 && addr < chipSize && len <= chipSize - addr;
}

// ---------------------------------------------------------------- commands

static void handleLine(char *cmd) {
  // split on ':' into at most 5 fields
  char *f[5] = { 0 };
  uint8_t nf = 0;
  f[nf++] = cmd;
  for (char *p = cmd; *p && nf < 5; p++) {
    if (*p == ':') { *p = 0; f[nf++] = p + 1; }
  }
  if (f[0][0] == 0 || f[0][1] != 0) { Serial.println(F("ERR:UNKNOWN")); return; }
  char op = f[0][0];

  if (op == 'V') { Serial.print(F("VER:")); Serial.println(FW_VERSION); return; }
  if (op == 'P') { Serial.println(F("PONG")); return; }

  if (op == 'T') {
    uint32_t size;
    if (nf != 2 || !parseHex32(f[1], size) || size < 128 || size > 262144UL) {
      Serial.println(F("ERR:PARSE")); return;
    }
    configure(size);
    Serial.println(F("OK"));
    return;
  }

  if (op == 'S') {
    uint32_t size = detectSize();
    if (size == 0) { Serial.println(F("ERR:NOCHIP")); return; }
    configure(size);
    Serial.print(F("SIZE:")); printHex32(size); Serial.println();
    return;
  }

  if (chipSize == 0) { Serial.println(F("ERR:NOCFG")); return; }

  uint32_t addr, len, val;

  if (op == 'R') {
    if (nf != 3 || !parseHex32(f[1], addr) || !parseHex32(f[2], len) || len > MAX_DATA) {
      Serial.println(F("ERR:PARSE")); return;
    }
    if (!inRange(addr, len)) { Serial.println(F("ERR:RANGE")); return; }
    uint8_t buf[MAX_DATA];
    if (!readMem(addr, buf, len)) { Serial.println(F("ERR:I2C")); return; }
    uint8_t sum = 0;
    Serial.print(F("D:"));
    for (uint8_t i = 0; i < len; i++) { printHex8(buf[i]); sum += buf[i]; }
    Serial.write(':'); printHex8(sum); Serial.println();
    return;
  }

  if (op == 'W') {
    uint32_t sumIn;
    size_t hexLen = (nf == 4) ? strlen(f[2]) : 0;
    if (nf != 4 || !parseHex32(f[1], addr) || hexLen == 0 || (hexLen & 1)
        || hexLen / 2 > MAX_DATA || !parseHex32(f[3], sumIn)) {
      Serial.println(F("ERR:PARSE")); return;
    }
    len = hexLen / 2;
    if (!inRange(addr, len)) { Serial.println(F("ERR:RANGE")); return; }
    uint8_t buf[MAX_DATA], sum = 0;
    for (uint8_t i = 0; i < len; i++) {
      int8_t hi = hexNibble(f[2][2 * i]), lo = hexNibble(f[2][2 * i + 1]);
      if (hi < 0 || lo < 0) { Serial.println(F("ERR:PARSE")); return; }
      buf[i] = (hi << 4) | lo;
      sum += buf[i];
    }
    if (sum != (uint8_t)sumIn) { Serial.println(F("ERR:CHK")); return; }
    if (!writeMem(addr, buf, len)) { Serial.println(F("ERR:I2C")); return; }
    uint8_t back[MAX_DATA];
    if (!readMem(addr, back, len)) { Serial.println(F("ERR:I2C")); return; }
    for (uint8_t i = 0; i < len; i++) {
      if (back[i] != buf[i]) {
        Serial.print(F("ERR:VERIFY:")); printHex32(addr + i); Serial.println(); return;
      }
    }
    Serial.println(F("OK"));
    return;
  }

  if (op == 'B' || op == 'F' || op == 'C') {
    if (nf != 4 || !parseHex32(f[1], addr) || !parseHex32(f[2], len) || !parseHex32(f[3], val)) {
      Serial.println(F("ERR:PARSE")); return;
    }
    if (!inRange(addr, len)) { Serial.println(F("ERR:RANGE")); return; }
    uint8_t buf[WIRE_CHUNK];

    if (op == 'F') {
      memset(buf, (uint8_t)val, WIRE_CHUNK);
      uint32_t a = addr, n = len;
      while (n) {
        uint8_t c = (n < WIRE_CHUNK) ? n : WIRE_CHUNK;
        if (!writeMem(a, buf, c)) { Serial.println(F("ERR:I2C")); return; }
        a += c; n -= c;
      }
      // read-back check
      a = addr; n = len;
      while (n) {
        uint8_t c = (n < WIRE_CHUNK) ? n : WIRE_CHUNK;
        if (!readMem(a, buf, c)) { Serial.println(F("ERR:I2C")); return; }
        for (uint8_t i = 0; i < c; i++) {
          if (buf[i] != (uint8_t)val) {
            Serial.print(F("ERR:VERIFY:")); printHex32(a + i); Serial.println(); return;
          }
        }
        a += c; n -= c;
      }
      Serial.println(F("OK"));
      return;
    }

    uint16_t crc = (uint16_t)val;
    uint32_t a = addr, n = len;
    while (n) {
      uint8_t c = (n < WIRE_CHUNK) ? n : WIRE_CHUNK;
      if (!readMem(a, buf, c)) { Serial.println(F("ERR:I2C")); return; }
      for (uint8_t i = 0; i < c; i++) {
        if (op == 'B') {
          if (buf[i] != (uint8_t)val) {
            Serial.print(F("NE:")); printHex32(a + i); Serial.println(); return;
          }
        } else {
          crc = crcUpdate(crc, buf[i]);
        }
      }
      a += c; n -= c;
    }
    if (op == 'B') { Serial.println(F("OK")); return; }
    Serial.print(F("CRC:"));
    printHex8(crc >> 8); printHex8(crc & 0xFF);
    Serial.println();
    return;
  }

  Serial.println(F("ERR:UNKNOWN"));
}

// ---------------------------------------------------------------- setup/loop

void setup() {
  Serial.begin(115200);
  Wire.begin();
  Wire.setClock(400000);           // 400 kHz; use 100000 for very old 24C01/02 parts
#if defined(WIRE_HAS_TIMEOUT)
  Wire.setWireTimeout(25000, true); // never hang on a stuck bus
#endif
  delay(50);
  Serial.println(F("READY"));
}

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\r') continue;
    if (c == '\n') {
      line[lineLen] = 0;
      if (overflow) Serial.println(F("ERR:PARSE"));
      else if (lineLen) handleLine(line);
      lineLen = 0;
      overflow = false;
    } else if (lineLen < LINE_MAX) {
      line[lineLen++] = c;
    } else {
      overflow = true;
    }
  }
}
