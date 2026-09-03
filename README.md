# SV2 → Bitwig Cursor Sync (Space-bar triggered)

Press **Space** inside Synthesizer V Studio 2 PRO (running as a Bitwig plugin):
the script reads the SV playhead position, and Bitwig jumps to the matching
position and starts playback.

No AutoHotkey, no loopMIDI, no Python, no npm — all bridging logic lives in a
single Bitwig extension. The result feels similar to working with an ARA
plugin: the SV editor window behaves like an integrated part of your DAW.

It also works with **Instrument X**, the other Dreamtonics product: drop
`sv2_cursor_sync.js` into
`C:\Users\<you>\AppData\Roaming\Dreamtonics\Instrument X\scripts\` and bind
Space the same way in Settings → Shortcuts, and you get the same Space-bar
sync inside its plugin window.

## How it works

Press Space in SV2 (the shortcut is bound to a JS script). The script detects
its running mode:

- **Plugin mode** (hostName contains "Plugin"): read the playhead position in
  seconds → write `SVSYNC:P:<seconds>` to the clipboard → the
  `SVCursorSync.bwextension` Bitwig extension polls the clipboard, spots the
  marker, restores the previous clipboard content → converts seconds to beats
  (measured playhead beats/seconds ratio; near the origin the tempo
  normalization value is back-calculated over a 20–666 BPM range) →
  `setPosition` + `play` (scheduled on the main thread).
- **Standalone mode**: simply call `SV.getPlayback().play()` locally.

## Installation (two steps)

1. **Bitwig extension**: copy `svsync-ext/SVCursorSync.bwextension` to your
   Bitwig Extensions folder, e.g.
   `C:\Users\<you>\Documents\Bitwig Studio\Extensions\`.
   Then in Bitwig: Settings → Controllers → Add Controller, and add/enable it
   by Vendor **bitwig_svsync** / Product **SV Cursor Sync** (it occupies no
   MIDI ports). The extension targets API 23 (Bitwig 6.x).
2. **Synthesizer V Studio 2**: copy `sv2_cursor_sync.js` to your SV2 scripts
   folder:
   `C:\Users\<you>\AppData\Roaming\Dreamtonics\Synthesizer V Studio 2\scripts\`.
   Then in SV2, open **Settings → Shortcuts**: remove the default "Play"
   binding for Space, and bind Space to the script's "sv2 cursor sync"
   entry (it appears under the Tools > Scripts command list). All shortcut
   changes in SV2 — both script and regular commands — are made in
   Settings → Shortcuts.

## Usage

After both sides are installed:

1. Load Synthesizer V Studio 2 PRO as an instrument plugin on a Bitwig track.
2. Position the SV2 playhead where you want playback to start (e.g. click on
   the timeline).
3. Press **Space** inside the SV2 plugin window. Bitwig jumps its playhead to
   the corresponding position and starts playing.

In standalone mode the same shortcut simply toggles local playback in SV2.

## Debug channel (bypasses the clipboard, tests the Bitwig side directly)

Send plain text lines (with a trailing newline) to `127.0.0.1:8890`:

    GO 32.5     -> jump to 32.5 s and play
    STOP        -> stop

## Known limitations

- Beat conversion uses `beats = seconds × BPM / 60` with the project's current
  BPM; positions inside tempo ramps will be slightly off.
- The clipboard backup only covers plain text; if the clipboard holds images or
  files at the moment you trigger a sync, it will be cleared to empty text.
- When SV2 is stopped, `getPlayhead()` returns the playhead position; if your
  positioning method does not move the playhead, click on the timeline first.
- There is a theoretical race window in the two-sided clipboard exchange; if a
  sync occasionally fails, just press Space again.

## Troubleshooting notes from development

- **JavaScript only, not Lua**: on SV2 2.2.1 the Lua bindings throw an internal
  error when calling `setHostClipboard` (the Chinese error message also renders
  as mojibake in the dialog). The JavaScript version works.
- SV2 error dialogs garble Chinese text but display English fine. While
  debugging you can "print" via `throw new Error("ASCII diagnostic message")`.
- The Bitwig extension jar must contain
  `META-INF/services/com.bitwig.extension.ExtensionDefinition` (a single line
  with the fully-qualified definition class name), otherwise the log reports
  "No extensions found"; the classes must also live in a package (not the
  default package).
- Bitwig's JVM is headless, so the AWT clipboard is unavailable; the extension
  reads/writes the clipboard via JNA calls to the Win32 API (JNA is bundled in
  the fat jar).

## Files

- `sv2_cursor_sync.js` — the SV2-side one-shot script (bind to Space)
- `svsync-ext/` — the all-in-one Bitwig extension (clipboard polling + TCP
  debug channel): source code and the prebuilt `SVCursorSync.bwextension`

### Rebuilding the extension

    javac -nowarn -cp "lib/extension-api-23.jar;jna.jar" -d build src/svsync/*.java

Then unzip `jna.jar` into `build` (excluding `META-INF/MANIFEST.MF`), keep
`build/META-INF/services/com.bitwig.extension.ExtensionDefinition`, and run:

    jar --create --file SVCursorSync.bwextension -C build .
