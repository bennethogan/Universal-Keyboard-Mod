## <span style ="color:lightgreen">Update 1.3.0 has released!</span> There is a lot of new content. Luckily, there is now a built-in wiki (will be finished in 1.3.1), which you can also view on the web using the link below:
## [Interactive Wiki - Web Version](bennethogan.dev/keyboardmod/wiki)

-----------------------------
### 🌐 Multi-language README's: 
### [English](/README.md) | [Português](/languages/portuguese/README.pt.md) | [Русский](/languages/russian/README.ru.md) |

----------------------
# IMPORTANT - READ THIS 

### CC:Tweaked and Create are not required, but I do highly recommend both of them, to take advantage of all the features of the keyboard. Most of the features of the keyboard use CC:Tweaked's peripherals. You will need to download the latest CC:Tweaked from Modrinth, not Curseforge.

### If you have <span style="color:magenta">Create: Connected</span> installed-- then the Wireless Redstone Links WILL NOT WORK UNTIL you set "Redstone Link Wildcard" to "False" in the configs. Thats Create: Connected --> Common --> Features --> "Redstone Link Wildcard" --> "False" or "X"

---------------------------------
### > I suggest taking a look at the [web-version of the wiki](bennethogan.dev/keyboardmod/wiki) to learn about the features described below. There are interactive images of the UI-- you hover over the parts you want to learn more about

----------------------

# What Does This Mod Do?
### > Control your ships or other contraptions with <span style="color:lightgreen">'Live Controller' </span> mode! It's like the Typewriter, Drive-by-write, and Tweaked Controllers. But with a lot more features!
### > Build awesome PID scripts visually with <span style="color:lightgreen">'Sequencer Mode' </span>that allows for as much complexity as you need! Can be run in parallel with Live Control mode, so a keystroke can cause a whole series of events 
### > New to this update -> <span style="color:lightgreen">'Wireless Copycats' </span> blocks - send a different redstone signal to any of its 6 sides. Perfect for those small cars and planes where you really need Drive by Wire! <br> Works similarly to the Create Redstone link, but uses a 6 character alphanumeric string as the frequency, instead of 2 items

---------------------
# The Other Keyboard Features
### > Import keybinds from an Aeronautics Typewriter placed up to 20 blocks away, to easily upgrade to the keyboard!
### > Racing simulator / HOTAS controller support! Up to 16 analog axis per device, up to 16 devices. Enable "Advanced Controller Input" in the config file
### > Favorite a screen to jump straight there when you right-click the keyboard. You can enable "Autostart" in the config, to autostart the Live Controls immediately when you favorite it as well. 1-click start! You can always shift+right-click to open the main menu
### > Remotely change the Create mod's "Value slider panels". This can be done in a on the spot in "value panel mode", or you can interact with them in the sequencer
### > Sable compatibility -> while on a sub-level, the keyboard knows your velocity and world position. There are PID scripts on Github, that you can load into your keyboard to try out!
### > 'Thruster Control Panel', which might feel a bit useless now that I have added 'Live Controls'. But I'm keeping it
### > Directly set values on CC:Tweaked peripherals, and directly type on the computers (it is a keyboard, after all!)!
### > You can place a display link on the keyboard. It won't look pretty, but you'll see a lot of info if you link the keyboard to a CC:Tweaked peripheral!

-----------

## What Features Require Which Optional Dependencies?

| Feature                                                       | Keyboard Mod Only | +CC:Tweaked | +Create | +CC+Create | +Create+Aero/Propulsion | +CC+Create+Aero/Propulsion |
|---------------------------------------------------------------|:---:|:---:|:-------:|:----------:|:-----------------------:|:---:|
| **Live Controller - local RS outputs**                        | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Live Controller - Variable mode**                           | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Live Controller - Overdrive mode**                          | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Live Controller - Wireless RS links**                       | x | x |    ✓    |     ✓      |            ✓            | ✓ |
| **Live Controller - Thruster power/vector**                   | x | x |    x    |     x      |            x            | ✓ |
| **Live Controller - RPM mode**                                | x | x |    x    |      ✓      |            ✓            | ✓ |
|                                                               |
| **Typewriter import**                                         | x | x |    x    |     x      |            ✓            | ✓ |
| **CC Computer mode**                                          | x | ✓ |    x    |     ✓      |            x            | ✓ |
| **CC Peripheral mode**                                        | x | ✓ |    x    |     ✓      |            x            | ✓ |
| **Value Panel**                                               | x | x |    ✓    |     ✓      |            ✓            | ✓ |
| **Thruster Control**                                          | x | x |    x    |     x      |            x            | ✓ |
|                                                               |
| **Sequencer - Math / Variable**                               | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Sequencer - Set RS Out / Delay / Jump / Math / Loop / End** | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Sequencer - If / Skip** (RS inputs only, without CC)        | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Sequencer - Wait For** (RS inputs only, without CC)         | ✓ | ✓ |    ✓    |     ✓      |            ✓            | ✓ |
| **Sequencer - Set Value** (CC peripheral setter)              | x | ✓ |    x    |     ✓      |            x            | ✓ |
| **Sequencer - Type Text / Type Var**                          | x | ✓* |    x    |     ✓*     |            x            | ✓* |
| **Sequencer - Sublevel stats as condition/getter**            | x | x |    x    |     x      |            ✓            | ✓ |


*Type Text/Var additionally require a CC computer to be linked on that channel.

-------------------------------------------------------------------

## Also there is a cooler recipe if you have Create downloaded, but Create is not required:

<img width="1920" height="1123" alt="2026-05-09_20 07 39" src="https://github.com/user-attachments/assets/5d87b6bf-7f4a-4694-aa9c-6661a1ca42e4" />
<img width="854" height="480" alt="2026-05-09_20 09 18" src="https://github.com/user-attachments/assets/c7805fea-ca2c-4101-a859-1334b9ca3a1d" />