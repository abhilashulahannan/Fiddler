## Description

Basically i want to add new feature to my samsung s24 ultra, which the stock OS lacks. These feature are available to playstore through apps but riddled with the ads, hidden behind paywall (Mostly subscription). And they are not to my needs. Hence The fiddler app will sole purpose is to better use my devices and customize it specifically for my needs. Currently the features I am planning-

- Internet-
    - Internet traffic monitor for status bar- It just adds the the monitor to the status bar to indicate network activity.
- Audio-
    - Auto Ringtone switcher- It will replace the system selected audio ringtone file with a song from a dedicated folder and switch them automatically.
- SecureMode-
    - Ideally i want my phone to become embarassment free when i hand out my device to other people.
    - In this i will have modes- Friends, family, Me. Which would be switchable from the quick setting tile. I want to some confidential items visible to certain modes.
- Fidland- Dynamic island-suited to my needs.
    - Effectively creating something like the dynamic island. it will have differnet island pages which will expand from the status bar.
        - 1 page for music apps like spotify, youtube, it will have horizontal swipe function to switch between apps
        - 2md page for next 5 songs on the playlist for active music app
        - 3rd page for 6-8 apps. Maybe add horizontal swipe here to add more apps.
        - Quick settings- Flashlight

Note: maybe integrate Other activities in the Dynamic Island - Fidland

# Fidland- fragment in fiddler

## Start point:

Basically just a black circle around the camera notch.

the theme is black throughout, the idea is to hide camera hole, using amoled display.

## Status bar expansion (These happen dynamically no physicall actions affect it)

### **1. Network Traffic Module (The circle expands to the left to form a pill shape)**

- Display real-time **upload/download speeds** (already implemented partly by you)
- It is just a visual thing, do not need to open any network thing with this

### **2. Equalizer / Media Info (Right Expansion)**

This part is dynamic if one of the following element is active then that is shown, if mutliple items active then they switch at 5 second interval. Some items will not switch if active like recording, timer, stopwatch etc.

- No click function in this part too.
- Animated **equalizer bars** that react to currently playing audio. basically 5-8 vertical lines type equaliser. Switchable
- Notification - Switchable
    - Stacks important notification visually by using app icons and adding badges for count/grouping.
    - Note the app icons are stacked on top of each other, showing each icon for 2 seconds then swtitching to next icon
- When charging → show battery status ring around the camera hole
- When receiving a call → pill expands with caller info
- When using voice recorder → glowing red dot animation
- When hotspot or tethering → add hotspot tethering icon

## Swipe expansion (Swiping down action on anywhere on the pill overlay willl trigger expansion)

### **4. Swipe Down → “Fiddler Dashboard”**

This is your centerpiece. A **rounded rectangular overlay** with smooth fade/scale animations.

### Category 1: 🎵 Music Player

- Controls: play/pause, next/prev, seek bar
- Small App icons for active player
- Album art in background (blurred slightly)
- Tap empty space → launch app
- Gesture: swipe left/right → switch between Spotify, YT Music, etc.

### Category 2: 📜 Music Queue

- Display track queue (for Spotify / YT Music via API or NotificationListener)
- Option to reorder (if supported)
- Clicking on the track will play that track

### Category 3: 🚀 App Launcher

- Will place apps in it. All app additions, deletion will be done on main fidller app, in the fidland fragment.
- The fidland fragment will also host configurations like number of icons by rows and columns, etc.

### Category 4: ⚙️ Quick Settings

- Custom toggle tiles: Wi-Fi, Bluetooth, Torch, Rotation, DND, etc.
- Long-press → opens system setting page
- Option to add/remove tiles will be done in the fiddler app, fidland fragment

---

### 💡 Additional Ideas to Elevate It

### **Visual Polish**

- Use **Lottie animations** for subtle transitions
- Material motion-inspired easing for swipes and expansions
- Rounded corners with consistent elevation shadow for the rectangle view

### **Gestural Enhancements**

- Long-press pill → open fidland fragment in fiddler

### **Customization Panel in Fidland fragment**

- Choose which categories are active
- Adjust animation speed, corner radius, transparency
