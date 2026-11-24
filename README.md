# EEPROM Programmer

Read, write, verify and erase 24Cxx I2C EEPROMs from the PC, with an Arduino Nano as the USB–I2C bridge.

**Chips:** 24C01, 24C02, 24C04, 24C08, 24C16, 24C32, 24C64, 24C128, 24C256, 24C512, 24CM01, 24CM02

## Hardware

| EEPROM pin | Nano |
|---|---|
| SDA | A4 |
| SCL | A5 |
| VCC | 5V |
| GND, A0, A1, A2, WP | GND |

Use 4.7 kΩ pull-ups on SDA and SCL if your EEPROM module doesn't have them. WP must be at GND or writes fail.
Auto-detect assumes A0/A1/A2 are at GND.

## Setup

1. Open `eeprom_programmer/eeprom_programmer.ino` in the Arduino IDE and upload it to the Nano (board *Arduino Nano*).
   The app checks the firmware version and refuses to work with the old sketch.
2. Open this folder in NetBeans as a project (Java 8). The only library is `lib/jssc-2.9.2.jar`.
3. Run. Main class: `eepromprogrammer.Main`.

## Using it

1. Pick the **Port** → **Connect**. The Nano resets when the port opens; after about 2 s the app finds the board and **auto-detects the chip**.
   Use **Detect** again after swapping chips, or pick the chip from the list.
2. **Open file…** (or drag a file onto the window) to load a `.bin` or Intel `.hex` file into the **Buffer** tab.
3. **Write** writes the buffer. The board reads back every block and checks it, so no separate verify pass is needed.
4. **Read** reads the whole chip into the **Chip** tab. If a buffer is loaded, differences are highlighted in red.

| Button | What it does |
|---|---|
| Read (F5) | Read the whole chip into the Chip tab |
| Write (F6) | Write the buffer; each block is read back and checked |
| Verify (F7) | Read the chip and compare it with the buffer |
| Blank check | Check that every byte is 0xFF |
| Erase | Fill the chip with 0xFF |
| Checksum | CRC-16 of the chip, compared with the buffer |
| New blank | New chip-sized buffer filled with 0xFF |
| Chip → buffer | Copy the last read into the buffer (for cloning or editing) |
| Fill… | Fill an address range (or the selected cells) with a value |
| Compare | Highlight differences between buffer and last read |
| Cancel (Esc) | Stop the running operation |

- Double-click a cell in the Buffer tab to edit it. Type hex, or 8 binary digits in Binary view.
- **Go to address** (Ctrl+G) jumps to a hex address; **Next difference** steps through the red cells.
- Hex view shows 16 bytes per row and Binary view 8, so both fit a 1366 px wide screen.
- During Read/Write, cells turn green once done and orange while being transferred.
- The app remembers your port, chip, view and last folder.
- Dark theme, built with no external library and without Nimbus: Metal plus a dark colour theme and a few flat painters in `Theme.java`.
  The button icons and app icon are PNG files in `src/eepromprogrammer/icons/` and are packed into the jar.
  `app.ico` in the same folder is there if you wrap the jar as an .exe.

## Expected speed (Nano at 115200 baud)

| Chip | Read | Write + check |
|---|---|---|
| 24C256 (32 KB) | ~10 s | ~20 s |
| 24C512 (64 KB) | ~20 s | ~40 s |
| 24CM02 (256 KB) | ~1.5 min | ~2.5 min |

These are estimates; actual speed depends on the chip's write-cycle time.

## Troubleshooting

- **"No answer from the board"**: the v2 sketch isn't uploaded, or another program (e.g. the Arduino serial monitor) has the port open.
- **"No chip detected"**: check wiring and pull-ups and that the chip is seated. You can still select the chip by hand.
- **Write fails with "read-back mismatch"**: WP isn't at GND, the wrong chip type is selected, or the chip is worn out.
- **Very old 24C01/24C02 parts**: if reads are unreliable, change `Wire.setClock(400000)` to `100000` in the sketch.
- jSSC may print an `SLF4J: Failed to load class` note in the console at start-up. It's harmless.

## Serial protocol (firmware v2)

115200 baud, one command per line, all numbers in hex. `<sum>` is the 8-bit sum of the data bytes.

| Command | Reply |
|---|---|
| `V` | `VER:2` |
| `P` | `PONG` |
| `T:<size>` | `OK` (select chip by size in bytes) |
| `S` | `SIZE:<size>` or `ERR:NOCHIP` (auto-detect) |
| `R:<addr>:<len>` | `D:<hexdata>:<sum>` (len up to 0x40 = 64 bytes) |
| `W:<addr>:<hexdata>:<sum>` | `OK`, `ERR:CHK`, `ERR:I2C`, `ERR:VERIFY:<addr>` |
| `B:<addr>:<len>:<val>` | `OK` or `NE:<addr>` (blank check) |
| `F:<addr>:<len>:<val>` | `OK` or `ERR:…` (fill / erase) |
| `C:<addr>:<len>:<init>` | `CRC:<crc16>` (CRC-16/CCITT, init FFFF) |

Auto-detect temporarily writes 2 bytes at 0x005A (on 24C01–24C16, bytes 0–2) and restores them afterwards.

## Project layout

```
src/eepromprogrammer/
  Main.java         entry point (installs the theme)
  Theme.java        dark theme: colours, flat buttons/tabs/scrollbars/progress/combo, icon loading
  MainWindow.java   the window and all operations
  HexView.java      hex/binary table with progress and difference colours
  Programmer.java   firmware protocol (timeouts, retries, checksums)
  SerialLink.java   jSSC serial port, line based
  LineChannel.java  interface between the two above
  ChipType.java     chip list (size, page size)
  HexFiles.java     .bin and Intel HEX load/save
  icons/            20 px button icons, app_16…app_256.png, app.ico
eeprom_programmer/eeprom_programmer.ino   Arduino firmware v2
lib/jssc-2.9.2.jar
```
