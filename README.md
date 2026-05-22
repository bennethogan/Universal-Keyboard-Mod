### 🌐 Read this in other languages: 
### [English](README.md) | [Português](languages/portuguese/README.pt.md) | [Русский](languages/russian/README.ru.md) |


# IMPORTANT NOTES 

### CC:Tweaked and Create are not required, but I do highly recommend both of them, to take advantage of all the features of the keyboard. Most of the non-redstone features of the keyboard use CC:Tweaked's peripherals. You will need to download the latest CC:Tweaked from Modrinth, not Curseforge.

### If you have <span style="color:red">Create: Connected</span> installed-- then the Wireless Redstone Links WILL NOT WORK UNTIL you set "Redstone Link Wildcard" to "False" in the configs. Thats Create: Connected --> Common --> Features --> "Redstone Link Wildcard" --> "False" or "X"

-------------------

# The Coolest Keyboard Features
### > 'Live controls' for Redstone wireless links, and Create Propulsion's Thrusters (through CC:Tweaked's peripherals)
### > 'Sequencer' mode that can execute simple scripts which interact with redstone & peripherals (& live controls if you use 2 keyboard!)

---------------------
# The Other Keyboard Features
### > Import keybinds from an Aeronautics Typewriter placed up to 20 blocks away, to easily upgrade to the keyboard!
### > Remotely change the Create mod's "Value panels", such as the RPM setting on the Speed Controller
### > Sable compatibility --> while on a sub-level, the keyboard knows your velocity and world position. There are PID scripts on Github, that you can load into your keyboard to try out!
### > 'Thruster Control Panel', which might feel a bit useless now that I have added 'Live Controls'. But I'm keeping it
### > Directly set values on CC:Tweaked peripherals, and directly type on the computers (it is a keyboard, after all!)!
### > You can place a display link on the keyboard. It won't look pretty, but you'll see a lot of info if you link the keyboard to a CC:Tweaked peripheral!


### See slides below for detailed instructions (incomplete)
-----------

## What Features Require Which Optional Dependencies?

| Feature | Keyboard Mod Only | +CC:Tweaked | +Create | +CC+Create | +Create+Aero/Propulsion | +CC+Create+Aero/Propulsion |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Live Controller — local RS outputs** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Live Controller — Wireless RS links** | x | x | ✓ | ✓ | ✓ | ✓ |
| **Live Controller — Thruster power/vector** | x | x | x | x | x | ✓ |
| **Typewriter import** | x | x | x | x | ✓ | ✓ |
| **CC Computer mode** | x | ✓ | x | ✓ | x | ✓ |
| **CC Peripheral mode** | x | ✓ | x | ✓ | x | ✓ |
| **Value Panel** | x | x | ✓ | ✓ | ✓ | ✓ |
| **Thruster Control** | x | x | x | x | x | ✓ |
||
| **Sequencer — Set RS Out / Delay / Jump / Math / Loop / End** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — If / Skip** (RS inputs only, without CC) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — Wait For** (RS inputs only, without CC) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — Set Value** (CC peripheral setter) | x | ✓ | x | ✓ | x | ✓ |
| **Sequencer — Type Text / Type Var** | x | ✓* | x | ✓* | x | ✓* |
| **Sequencer — Sublevel stats as condition/getter** | x | x | x | x | ✓ | ✓ |


*Type Text/Var additionally require a CC computer to be linked on that channel.

---------------------------------------------

## KNOWN BUGS

### > The creative vector thruster doesnt let me edit the ThrustPower. This is different than the redstone "on" signal. It is the "%" value that shows up on Creative thrusters. It works on the Creative thruster, but not the Creative Vector Thruster.

### > Multiblock Creative thruster, which is a new feature of Create: Propulsion, was not working for me in one test, but did in another. It was the only thing not working, and since its a brand new feature of Propulsion, I am going to leave it for a little bit before attempting to tackle this bug

### > Please tell me of any more you find!

-------------------------------------------------------------------

## Also there is a cooler recipe if you have Create downloaded, but Create is not required:

<img width="1920" height="1123" alt="2026-05-09_20 07 39" src="https://github.com/user-attachments/assets/5d87b6bf-7f4a-4694-aa9c-6661a1ca42e4" />
<img width="854" height="480" alt="2026-05-09_20 09 18" src="https://github.com/user-attachments/assets/c7805fea-ca2c-4101-a859-1334b9ca3a1d" />
<img width="1920" height="1123" alt="2026-05-16_20 52 29" src="https://github.com/user-attachments/assets/2b9865ba-aa8c-45a2-8558-109682f20309" />
