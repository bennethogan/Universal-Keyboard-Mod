
[![Easy to Use GUI Controller for Create: Aeronautics -- Universal Keyboard Mod for Minecraft](https://www.youtube.com/watch?v=2Qxa50ltkyc)
(Youtube video link)

**Universal Keyboard mod that allows you to control various features I wanted better control over. There is a base range of 16 blocks, can be configured up to 256. **

Youtube video covers most of the features, I will try my best to explain some of them below. I will add a folder to my github called "sequence_demos" where you can download the ones from this video, and add them to your "universal_keyboard" folder in your instance.

Timestamp descriptions too, in case you want to skip around:

0:00 - shows keyboard working without CC:Tweaked or Create, I've tested this with no other mods installed. The sequence logic just reads one redstone side, and sets the other side equal. That "1" is the channel number, its relevant for other situations but its useless right now. Not very easy to make it only appear when needed. But for a redstone line, its useless, otherwise it references a certain linked channel.

0:28 - Create: Propulsion vector thruster control example. I set two thrusters on two different channels, to show those capabilites. Up to 16 channels of completely different devices can be linked.

0:53 - Example of controlling Create's value panels, and specifically I show a script that changes the RPM or two different motors, based on incoming redstone signal to the keyboard

1:33 - Showing keyboard capabilities when linked to a CC:computer. The typing thing is the original purpose of the mod. Its small but I wanted to be able to type while not stuck in the computer's GUI. You can also incorporate typing into the scripting, so that you can print variables for debugging purposes, mainly.

1:58 - I made "Jingle Bells" flash across wireless redstone linked lamps, and a thruster. As best as I could get the timing, it took way too long for me to make, and doesn't look that great. But you get the picture

2:33 - Showing the Sable compat. When on a sublevel, the keyboard detects and can provide things like velocity and position to the sequencer. I showcase a PID I made that still needs some tuning, as well as the behavior of the thruster panel menu when on a sub-level. It shows velocity and position on the control screen, only when on a sub-level.

<img width="1459" height="856" alt="KeyboardMod_gallery4" src="https://github.com/user-attachments/assets/72ab4aff-de0c-4233-92b5-2c8f5d2b4793" />
<img width="1452" height="855" alt="KeyboardMod_gallery2" src="https://github.com/user-attachments/assets/0ef30c5b-7c06-4f10-972b-fa5e091e96d0" />
<img width="1462" height="856" alt="KeyboardMod_gallery3" src="https://github.com/user-attachments/assets/0f41b6dc-2968-46ea-b315-9bdc5e85de18" />


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

------------------------------------------

KNOWN BUGS -The creative vector thruster doesnt let me edit the ThrustPower. This is different than the redstone "on" signal. It is the "%" value that shows up on Creative thrusters. It works on the Creative thruster, but not the Creative Vector Thruster. -Multiblock Creative thruster, which is a new feature of Create: Propulsion, was not working for me in one test, but did in another. It was the only thing not working, and since its a brand new feature of Propulsion, I am going to leave it for a little bit before attempting to tackle this bug -Please tell me of any more you find!

Also there is a cooler recipe if you have Create downloaded, but Create is not required:
<img width="1920" height="1123" alt="2026-05-09_20 07 39" src="https://github.com/user-attachments/assets/5d87b6bf-7f4a-4694-aa9c-6661a1ca42e4" />
<img width="854" height="480" alt="2026-05-09_20 09 18" src="https://github.com/user-attachments/assets/c7805fea-ca2c-4101-a859-1334b9ca3a1d" />
<img width="1920" height="1123" alt="2026-05-16_20 52 29" src="https://github.com/user-attachments/assets/2b9865ba-aa8c-45a2-8558-109682f20309" />
