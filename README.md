# Dynamic Color Wave Lighting for Hubitat

An advanced ambient lighting application for the Hubitat Elevation platform. This app creates immersive, shifting environments by dynamically cycling coordinated color profiles across your selected smart bulbs with smooth wave transitions.

It features robust event suppression to filter out self-generated Z-Wave/Zigbee commands, an optional state capture/restore machine, and physical button controllers to easily trigger or cancel the effects.

## Features

*   **10 Curated Color Profiles:** Instantly shift the mood with everything from natural atmospheric cycles to vibrant reactive palettes.
*   **Intelligent State Recovery:** Captures the exact state of your bulbs before an effect starts (including Power, Hue, Saturation, Level, and Color Temperature) and gracefully restores them when stopped.
*   **External Override Protection:** Automatically stops the animation loop if a user manually turns off a bulb, adjustments the physical switch, or changes the color configuration via a dashboard or physical controller.
*   **Granular Timing Control:** Configure the individual bulb transition intervals and overall cycle pause durations to fine-tune the speed of the wave.
*   **Dual Activation Methods:** Run the loop via an integrated master virtual/physical switch or map it directly to push/hold/double-tap events on a button controller.

---

## Available Color Schemes

| Theme Name | Description |
| :--- | :--- |
| **Aurora Borealis** | Cool greens, shifting teals, and occasional indigo/violet accents. |
| **Chinatown** | Deep striking reds, rich golds, and warm oranges cycling in waves. |
| **Ember & Hearth** | Slow amber-orange candle-like glow with structural flame flicker. |
| **Ocean Drift** | Calm aquamarine and sapphire deep blues shifting gently like water. |
| **Candlelight** | Ultra-warm white-to-gold tones with a delicate fluttering candle glow. |
| **Enchanted Forest** | Deep rich forest greens intertwined with dark, immersive woodland purples. |
| **Synthwave** | Retro hot pink, electric purple, and vibrant neon cyan pulses. |
| **Pride ROYGBIV** | Vividly cycles sequentially through full Red, Orange, Yellow, Green, Blue, Violet. |
| **Japanese Cherry Blossom** | Soft, elegant pastel pinks, snowy whites, and rose-tinted blossom tones. |
| **Primary Colors** | Direct, punchy alternation between pure Red, pure Blue, and pure Yellow. |
| **Blue Monday** | Moody, shifting layers of rich blues, ranging from crisp electric ice to deep midnight tones. |

---

## Installation

1. Log into your Hubitat Elevation Web UI.
2. Navigate to **Apps Code** in the sidebar.
3. Click **+ New App** in the top right corner.
4. Paste the Groovy source code from `Dynamic_Color_Wave_Lighting.groovy` into the editor.
5. Click **Save** in the top right corner.
6. Navigate to **Apps**, click **Add User App**, and select **Dynamic Color Wave Lighting**.

---

## Configuration

*   **Color Bulbs:** Choose the target `capability.colorControl` devices you want included in the effect. Unselected color bulbs on the network will automatically turn off when the loop activates to maintain ambient immersion.
*   **Theme Configuration:** Select your active profile.
*   **Set a bulb every (seconds):** The delay between updating individual bulbs in the sequence. Lower numbers make the "wave" move faster across your room.
*   **Then pause (seconds):** The wait time after an entire wave has rolled through before shuffling the device order and firing the next wave.
*   **Base Effect Brightness:** Sets the ceiling level (1-100%) for the bulbs. Profiles like *Candlelight* and *Ember & Hearth* will dynamically scale below this number to simulate physical flickering.

### Automated Triggers (Optional)
*   **Switch Control:** Associating a switch will activate the wave when turned **On** (and capture current lighting states). Turning it **Off** stops the animation and shuts down all selected fixtures.
*   **Button Control:** Specify a button device, action (pushed, held, doubleTapped), and target button number to trigger or terminate the sequence independently.

---

## Technical Details

### Self-Looping Execution Architecture
The app manages asynchronous iteration across your devices utilizing sequential hardware scheduling commands:
*   `runInMillis()` manages internal bulb propagation steps to keep transitions tightly grouped.
*   `runIn()` handles the macro-level loop cycle delays to optimize Hubitat hub processing overhead.

### Smart Intercept Logic
To prevent local mesh congestion or infinite rule loops, the driver includes an ephemeral event suppression layer (`state.auroraSuppressEventsUntil`). When the application actively pushes a hardware state modification to a bulb, it briefly mutes inbound event responses from that specific device network ID. If a user manually overrides a bulb's color profile outside of the application window, the rule intercepts the event and safely invokes `stopAll()`.

## License

This project is licensed under the Apache License, Version 2.0. See the file details for information.