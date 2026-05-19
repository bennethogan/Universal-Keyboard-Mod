
[![Youtube Link to Example Video](https://img.youtube.com/vi/y3kmv2i7s8A/0.jpg)](https://www.youtube.com/watch?v=y3kmv2i7s8A)
<img width="1568" height="1044" alt="KeyboardModGallery7" src="https://github.com/user-attachments/assets/a600faff-04b0-4898-ae08-aaa2cfd95f9f" />


## DEPENDENCIES NOTE -- I set this up so CC:Tweaked and Create are not required, but I do highly recommend them to take advantage of all the features of the keyboard. Most of the non -redstone features of the board are extensions of CC:Tweaked's peripherals. You will need to download the latest CC:Tweaked from modrinth, not Curseforge.

## If you have Create: Connected installed-- then the Wireless Redstone Links wont work until you set "Redstone Link Wildcard" to "False" in the configs. Thats Create: Connected --> Common --> Features --> "Redstone Link Wildcard".

----------

## **Universal Keyboard mod that allows you to control various features I wanted better control over. There is a base range of 16 blocks, can be configured up to 256. **

-----------

## What Features Require Which Optional Dependencies?

| Feature | Keyboard Mod Only | +CC:Tweaked | +Create | +CC+Create | +Create+Aero/Propulsion | +CC+Create+Aero/Propulsion |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **CC Computer mode** | x | ✓ | x | ✓ | x | ✓ |
| **CC Peripheral mode** | x | ✓ | x | ✓ | x | ✓ |
| **Value Panel** | x | x | ✓ | ✓ | ✓ | ✓ |
| **Thruster Control** | x | x | x | x | x | ✓ |
| **Sequencer — Set RS Out / Delay / Jump / Math / Loop / End** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — If / Skip** (RS inputs only without CC) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — Wait For** (RS inputs only without CC) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — Set Value** (peripheral setter) | x | ✓ | x | ✓ | x | ✓ |
| **Sequencer — Type Text / Type Var** | x | ✓* | x | ✓* | x | ✓* |
| **Sequencer — Sublevel stats as condition/getter** | x | x | x | x | ✓ | ✓ |
| **Live Controller — RS outputs** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Live Controller — Wireless channels** | x | x | ✓ | ✓ | ✓ | ✓ |
| **Live Controller — Thruster power/vector** | x | x | x | x | x | ✓ |
| **Typewriter import** | x | x | x | x | ✓ | ✓ |

*Type Text/Var additionally require a CC computer to be linked on that channel.


----


**Youtube video covers most of the features, I will try my best to explain some of them below. I will add a folder to my github called "sequence_demos" where you can download the ones from this video, and add them to your "universal_keyboard" folder in your instance.**

Timestamp descriptions too, in case you want to skip around:

0:00 - shows keyboard working without CC:Tweaked or Create, I've tested this with no other mods installed. The sequence logic just reads one redstone side, and sets the other side equal. That "1" is the channel number, its relevant for other situations but its useless right now. Not very easy to make it only appear when needed. But for a redstone line, its useless, otherwise it references a certain linked channel.

ALL THE FEATURES BELOW HERE REQUIRE CREATE, CC:TWEAKED, OR BOTH

0:28 - Create: Propulsion vector thruster control example. I set two thrusters on two different channels, to show those capabilites. Up to 16 channels of completely different devices can be linked.

0:53 - Example of controlling Create's value panels, and specifically I show a script that changes the RPM or two different motors, based on incoming redstone signal to the keyboard

1:33 - Showing keyboard capabilities when linked to a CC:computer. The typing thing is the original purpose of the mod. Its small but I wanted to be able to type while not stuck in the computer's GUI. You can also incorporate typing into the scripting, so that you can print variables for debugging purposes, mainly. 

1:58 - I made "Jingle Bells" flash across wireless redstone linked lamps, and a thruster. As best as I could get the timing, it took way too long for me to make, and doesn't look that great. But you get the picture

2:33 - Showing the Sable compat. When on a sublevel, the keyboard detects and can provide things like velocity and position to the sequencer. I showcase a PID I made that still needs some tuning, as well as the behavior of the thruster panel menu when on a sub-level. It shows velocity and position on the control screen, only when on a sub-level.

3:04 - "Live Controller" feature integrated with vector thrusters. I've set up WASD to affect vector directions, and SPACE and LeftShift are incrementing the thruster power higher/lower each time I press them.

3:26 - "Live Controller" integrating with wireless redstone links. I show increment, hold and toggle.

3:57 - Typewriter import: exactly what it sounds like. Because all the best builds on Createmod.com are already set up with a typewriter. I've tried it where I just print the schematic, import from the typewriter, put my keyboard in its place (keyboard has the same drop behavior as a typewriter, it keeps its memory). It all works immediately. But then you could rebuild things to incorporate the thruster control, or incrementing directly instead of using a redstone accumulator.

---------------------------------------

UPDATED FEATURES: New "Live Controller" mode. Three main modes, up to 20 keybinds. Easy import from Typewriter.
1. Redstone (RS), where you can send a redstone signal to a wireless redstone link, or a local side. Then select "HLD", "TGL", or "INC" for Hold, Toggle, or Increment. In hold and toggle mode, you set the signal strength. Increment mode has you select "++" or "--", which just means every time you click the key, the signal goes up one, or down one.
2. Thruster (Thr), You can again choose hold, toggle, or increment on the power of that thruster.
3. Vector (VEC), where you can select the "->" to choose a position that the vector thruster will go to when the key is held/toggled. Holding multiple keys together will add the angles (and powers) together, so you can make it so you have a slight turn key, and a sharp turn key that adds more angle when both are pressed.

Ask questions and give feedback if you have it!

---------------------------------------------

FEATURES:

Sequencer with save/load file, up to 100 lines. Store up to 8 variables, do basic math on them, set them equal to an income redstone signal on the block's side

Emit redstone to wireless redstone frequencies through "Wi-Fi Setup" menu. These frequencies cant be read yet, but you can emit redstone at those frequencies in the sequencer.

Link to a Create: Propulsion thruster for special compatibility I did! Easy-to-use GUI to control the vector thruster directions, or any of their redstone signal stregths. No redstone link needed! It takes over the CC:Peripheral controls remotely. Kinda OP, I know. Made for those of us who don't like the challenge.

Link to a CC:Tweaked computer to use this keyboard as, ya know, a keyboard. Helpful for printing variables for debugging purposes. This keyboard isnt actually a CC:Peripheral itself yet, because theres so much info it could feed the computer that the computer can very well just get itself. Most of the features of this mod utilize redstone or the CC:Peripheral methods anyway.

Link to any CC Peripheral to read most of the getters (some return tables which I havent handled yet), and set most of the setters. Helpful to set up a script like in my video, where I adjust the motor's RPM based on redstone signal strength.

Link to any object that has one of Create's "Value Panels", to manually adjust it by typing. I don't know any other mod that lets you do this directly. So this was actually one of the original purposes of this mod, as well as typing on a CC computer. Then Aeronautics came out.

Sable compatibility. So if you are on a sublevel, the keyboard can read certain things about its position and velocity. Highly usefuly for building advanced controllers. See the video.

This is another minor one, but you can turn any CC:Peripheral into a display source for Create. Just link the CC:Peripheral to a keyboard (I only tested it on channel 1 honestly, I will look into this more), and place a display link on top of the keyboard. It doesnt look nice, but it will display a lot of info that CC:Tweaked computers get, which isnt always accessible any other way. 

-------------------------------------------------------------------

KNOWN BUGS
-The creative vector thruster doesnt let me edit the ThrustPower. This is different than the redstone "on" signal. It is the "%" value that shows up on Creative thrusters. It works on the Creative thruster, but not the Creative Vector Thruster.
-Multiblock Creative thruster, which is a new feature of Create: Propulsion, was not working for me in one test, but did in another. It was the only thing not working, and since its a brand new feature of Propulsion, I am going to leave it for a little bit before attempting to tackle this bug
-Please tell me of any more you find!

Also there is a cooler recipe if you have Create downloaded, but Create is not required:
<img width="1920" height="1123" alt="2026-05-09_20 07 39" src="https://github.com/user-attachments/assets/5d87b6bf-7f4a-4694-aa9c-6661a1ca42e4" />
<img width="854" height="480" alt="2026-05-09_20 09 18" src="https://github.com/user-attachments/assets/c7805fea-ca2c-4101-a859-1334b9ca3a1d" />
<img width="1920" height="1123" alt="2026-05-16_20 52 29" src="https://github.com/user-attachments/assets/2b9865ba-aa8c-45a2-8558-109682f20309" />
