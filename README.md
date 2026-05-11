
[![Universal Keyboard mod for Minecraft NeoForge](http://img.youtube.com/vi/3qiGU5Us97k/0.jpg)](http://www.youtube.com/watch?v=3qiGU5Us97k)

Universal Keyboard mod that allows you to control various features I wanted better control over.

Right click something to link the keyboard, and place. Configurable wireless range with 16 blocks default. Current support for:

1. CC:Tweaked computer-- once linked you can type on the keyboard without having the terminal open. Useful if you dont want to set up a pocket computer to do that.
-------------------

2. A "sequencer" that is just visual scripting, using the CC peripherals and redstone signals around the keyboard. Watch the video to help it be best understood.

My most recent update added this if/skip, which is a little hard to understand if you arent used to it. Its the least intuitive part, so I will just include the photo and say that this can be used as a very basic altiude control (add more lines and it might work really well):

<img width="2560" height="1377" alt="2026-05-10_20 12 09" src="https://github.com/user-attachments/assets/a293078c-a329-434f-a4d2-4a10c856dfe8" />

So if the redstone signal being received is high enough (above 10 signal strength), we skip 1 row. That would have raised the thruster output. Then it checks if signal stregnth is too low. If its too low, also skip lowering the thruster power. Its a band where if it goes to high, the power gets cut, and if the RS signal goes too low, the power gets increased. In a loop. Ask if you have questions

---------------------------


3. Any CC:Tweaked peripheral-- because so many mods already add compats for this, I am using that for the majority of the features. Currently the GUI is a little rough but you can see the values being returned by peripheral.call, and you can set anything that allows setting values.

<img width="854" height="480" alt="2026-05-09_20 12 14" src="https://github.com/user-attachments/assets/870da0fe-845f-46eb-bcec-b61a4ee613d4" />

-------------------------------------------------------------

4. Those Create "value panels" that can currently only be controlled manually by clicking and holding with a wrench. You can now set it remotely, or again with the sequencer

<img width="854" height="480" alt="2026-05-09_20 11 27" src="https://github.com/user-attachments/assets/b6cfa7da-8839-409d-b4df-bd02bf6958ff" />

-----------------------------------------------------------

5. Lastly for now is the Create: Propulsion Simulated thrusters. There is a special GUI for them to make controlling easier. It uses the CC:tweaked peripheral compat to do this, so it's limited to whatever there is currently a public method for. Will be improved later. See image below:

NOTE: YOU MUST USE THE LATEST NIGHTLY BUILD OF CREATE: PROPULSION SIMULATED from their Discord server. There have been significant reworks, so until the release version is posted on Curseforge you should just be using it anyway. But this keyboard was built around the latest updates for Propulsion


<img width="854" height="480" alt="2026-05-09_20 11 48" src="https://github.com/user-attachments/assets/f4e4c5c3-f1ef-473b-a111-ae6d42742798" />

------------------------------------------------------------------------

Lots more to come, so help me test and give me your thoughts! I don't have a discord server so if you want to put a suggestion in 'Issues' I will certainly read it.

Also there is a cooler recipe if you have Create downloaded, but Create is not required:
<img width="1920" height="1123" alt="2026-05-09_20 07 39" src="https://github.com/user-attachments/assets/5d87b6bf-7f4a-4694-aa9c-6661a1ca42e4" />
<img width="854" height="480" alt="2026-05-09_20 09 18" src="https://github.com/user-attachments/assets/c7805fea-ca2c-4101-a859-1334b9ca3a1d" />

