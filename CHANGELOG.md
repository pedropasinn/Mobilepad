# Changelog

## 0.1.0 – unreleased

First version of Mobilepad: an Android phone as a game controller for PC (Windows, Linux, macOS),
Android devices and Nintendo Switch, over Wi‑Fi, USB or Bluetooth.

### Android app
- Menus with frosted glass panels, a dock, colour icon tiles, a top bar with status, clock and battery,
  and the rounded Nunito font.
- Controller drawn as a handheld shell with moulded buttons, a one-piece D-pad and recessed sticks;
  skins Violet, Rose and Graphite (pure black, for OLED).
- One MENU button for every secondary function: vibration, tilt steering, motion sensor
  and axes, recenter, amiibo and leave.
- Delta skins: import `.deltaskin` files in *Settings → Controller skin* (PDF/PNG artwork, D-pads,
  analog thumbsticks, several fingers at once).
- Macros and radial wheels, motion sensor and DSU server, amiibo through emuiibo.
- Languages: English, Português (Brasil), Italiano, 简体中文, 繁體中文, 日本語.
- Pages: Connect · Controller (skins, button remapping, macros and wheels, orientation) · Motion · Extras
  (amiibo) · Settings. Each page is a list with the selected entry's detail beside it (landscape) or
  unfolding in place (portrait), so nothing sits hidden below the fold; areas that do scroll fade at
  the edge. Landscape uses a side rail; the menus follow the phone's rotation, the chosen orientation
  applies to the controller.
- Delta skins are adapted instead of shown whole: their colours and button artwork dress the Mobilepad
  layout (no game-screen area). Every skin, built-in or imported, has a live miniature, and an import
  opens a preview before use.
- Button remapping (any button to any other, or off; one-tap A/B · X/Y swap) for on-screen and physical
  controllers.
- In-game menu as a pause screen: actions on one side, quick settings and a reference of every macro
  and wheel on the other (a wheel can be opened from there).
- Radial wheels: vector icons instead of emoji (old configurations convert automatically), solid
  colours over any skin, and wheels opened from the touchscreen stay open until a slice is tapped.
- Macros and wheels page in two columns with wheel miniatures.
- Button layout editor: on the controller itself, drag any part, change its size and opacity or hide it;
  landscape and portrait are saved apart. Opened from *Controller → Button layout* or the in-game menu.
- The screen saver (black layer, panel off through Shizuku, Volume and Back+Start shortcuts) is gone:
  Shizuku now only serves the Switch over Bluetooth.

### Windows companion
- Virtual Xbox 360 controller through ViGEmBus, QR code to connect, USB through ADB; dark painted UI.

### Credits
- Nunito font by The Nunito Project Authors, SIL Open Font License 1.1 (`docs/licenses/Nunito-OFL.txt`).
